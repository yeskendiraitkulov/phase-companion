package app.phase.companion.game;

import app.phase.companion.core.Room;
import app.phase.companion.core.RoomParser;
import app.phase.companion.core.RoomPlan;
import app.phase.companion.core.RoomPlayer;
import app.phase.companion.core.RoomWatcher;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase Companion в одном мире: живёт от первого тика встроенного сервера до остановки.
 *
 * <p>Всё на потоке сервера. Раз в секунду: пульс для датапака, проверка {@code room.json}.
 * Каждый тик — только сравнение числа игроков, чтобы обновить шапку при входе и выходе.
 *
 * <p>Код общий для всех загрузчиков и версий игры: трогает только классы самой игры.
 * Что у загрузчика своё — как вызываются {@link #tick()} и {@link #stop()} и откуда
 * берётся версия мода ({@link Platform}).
 */
public final class Companion {
	static final Logger LOG = LoggerFactory.getLogger("phase_companion");

	/** Совпадает с {@code COMPANION_SCORE} и {@code COMPANION_PULSE} в {@code datapack.rs}. */
	static final String PULSE_HOLDER = "#companion";
	static final int PULSE = 5;
	static final String META_OBJECTIVE = "phase.meta";

	private static final int ACCENT = RoomParser.DEFAULT_ACCENT;
	private static final int SECOND = 20;
	/** Сколько видна полоса при входе: 10 секунд, как у датапака. */
	private static final int WELCOME_TICKS = 10 * SECOND;

	private final MinecraftServer server;
	private final Platform platform;
	private final Path phaseDir;
	private final RoomWatcher watcher;
	private final int vanillaMax;
	/** Проверка аккаунта, как её настроила игра: её мод и возвращает. */
	private final boolean vanillaAuth;

	private Room room = Room.CLOSED;
	/** Файл в незнакомом формате: мод молчит, оформление делает датапак. */
	private boolean standingDown;
	private boolean aliveWritten;
	private int ticks;
	private int lastOnline = -1;
	/** Тик, когда убрать полосу при входе; -1 — полосы нет. */
	private int hideBarAt = -1;
	/** Кому мод включил свечение: выключает только им, чужое свечение не трогает. */
	private final Set<String> glowing = new HashSet<>();

	private Companion(MinecraftServer server, Platform platform) {
		this.server = server;
		this.platform = platform;
		this.phaseDir = server.getWorldPath(LevelResource.ROOT).resolve("phase");
		this.watcher = new RoomWatcher(phaseDir.resolve("room.json"));
		((PlayerLimit) server).phase$setPlayerLimit(PlayerLimit.VANILLA);
		this.vanillaMax = server.getMaxPlayers();
		this.vanillaAuth = server.usesAuthentication();
	}

	/** Только встроенный сервер одиночной игры: свой сервер настраивается сам. */
	public static Companion create(MinecraftServer server, Platform platform) {
		if (server.isDedicatedServer()) {
			return null;
		}
		// Путь корня мира кончается на «.»: имя папки берём у нормализованного пути.
		LOG.info("Phase Companion {}: мир «{}»", platform.version(),
				server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName());
		return new Companion(server, platform);
	}

	public void tick() {
		ticks++;
		int online = server.getPlayerCount();
		if (online != lastOnline) {
			boolean joined = online > lastOnline;
			lastOnline = online;
			if (!standingDown && room.active()) {
				sendTabList(online);
				if (joined) {
					showWelcomeBar(online);
				}
			}
		}
		if (hideBarAt >= 0 && ticks >= hideBarAt) {
			hideBarAt = -1;
			command("bossbar remove phase:room");
		}
		if (ticks % SECOND != 0) {
			return;
		}
		writeAliveOnce();
		Optional<RoomWatcher.Result> result = watcher.poll();
		if (result.isPresent()) {
			switch (result.get()) {
				case RoomWatcher.Changed changed -> {
					standingDown = false;
					apply(changed.room());
				}
				case RoomWatcher.Unsupported unsupported -> standDown(unsupported.reason());
			}
		}
		if (!standingDown) {
			pulse();
			applyAccess();
			if (room.active()) {
				syncGlow();
			}
		}
	}

	public void stop() {
		restoreAccess();
		// Команды остаются в сохранении, как у датапака: при следующем открытии мира Phase
		// или мод приведут их к текущей комнате, закрытая комната их уберёт.
		glowing.clear();
	}

	// ------------------------------------------------------------------ применение

	private void apply(Room next) {
		boolean wasActive = room.active();
		room = next;
		applyAccess();
		applyTeams();
		((PlayerLimit) server).phase$setPlayerLimit(RoomPlan.maxPlayers(vanillaMax, room));
		if (room.active()) {
			sendTabList(server.getPlayerCount());
			// Шапка при входе на 10 секунд, как у датапака (окно Phase её обещает): когда
			// комната открылась. Дальше — при каждом входе (tick); правка оформления её не зовёт.
			if (!wasActive) {
				showWelcomeBar(server.getPlayerCount());
			}
			syncGlow();
		} else {
			hideBarAt = -1;
			command("bossbar remove phase:room");
			server.getPlayerList().broadcastAll(new ClientboundTabListPacket(Component.empty(), Component.empty()));
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (glowing.remove(player.getScoreboardName())) {
					player.setGlowingTag(false);
				}
			}
			// Как у уборки датапака: Phase может удалить датапак из мира.
			command("data modify storage phase:state clean set value 1b");
		}
		LOG.info("Phase Companion: комната {}", room.active() ? "открыта, мест " + room.maxPlayers() : "закрыта");
	}

	private void applyTeams() {
		Scoreboard scoreboard = server.getScoreboard();
		List<RoomPlan.TeamSpec> wanted = RoomPlan.teams(room);
		Set<String> names = new HashSet<>();
		wanted.forEach(spec -> names.add(spec.team()));
		for (PlayerTeam team : List.copyOf(scoreboard.getPlayerTeams())) {
			if (RoomPlan.isPhaseTeam(team.getName()) && !names.contains(team.getName())) {
				scoreboard.removePlayerTeam(team);
			}
		}
		for (RoomPlan.TeamSpec spec : wanted) {
			PlayerTeam team = scoreboard.getPlayerTeam(spec.team());
			if (team == null) {
				team = scoreboard.addPlayerTeam(spec.team());
			}
			RoomPlayer style = spec.style();
			TeamColors.apply(team, style.nameColor());
			String prefix = style.prefixText();
			team.setPlayerPrefix(prefix == null
					? Component.empty()
					: Component.literal(prefix).withStyle(s -> s.withColor(style.prefixColor())));
			// Только свободные: команду карты или другого датапака Phase не трогает.
			PlayerTeam current = scoreboard.getPlayersTeam(spec.player());
			if (current == null || (RoomPlan.isPhaseTeam(current.getName()) && current != team)) {
				scoreboard.addPlayerToTeam(spec.player(), team);
			}
		}
	}

	private void sendTabList(int online) {
		MutableComponent header = Component.empty()
				.append(Component.literal(room.title()).withStyle(s -> s.withColor(ACCENT).withBold(true)))
				.append(Component.literal(RoomPlan.headerMiddle(room) + online + " онлайн").withStyle(ChatFormatting.WHITE));
		Component footer = room.footer()
				? Component.literal("Работает на Phase").withStyle(ChatFormatting.GRAY)
				: Component.empty();
		server.getPlayerList().broadcastAll(new ClientboundTabListPacket(header, footer));
	}

	/**
	 * Полоса сверху экрана «PHASE • код • N онлайн» всем игрокам на {@link #WELCOME_TICKS}:
	 * при открытии комнаты и при каждом входе. Командами игры, а не API боссбара: синтаксис
	 * команд один на всех версиях и загрузчиках, как у датапака (то же имя `phase:room`).
	 */
	private void showWelcomeBar(int online) {
		// Датапак при загрузке мира тоже показал эту полосу и завёл `phase:hide` через 200
		// тиков игрового времени — а пока открыто меню Esc, одиночный мир на паузе, и его
		// таймер срабатывает уже после нашего показа. Полосу ведёт мод: таймер датапака снимаем.
		command("schedule clear phase:hide");
		command("bossbar add phase:room {\"text\":\"PHASE\"}");
		command("bossbar set phase:room color blue");
		command("bossbar set phase:room max " + Math.max(1, room.maxPlayers()));
		command("bossbar set phase:room value " + Math.min(online, Math.max(1, room.maxPlayers())));
		command("bossbar set phase:room name [{\"text\":" + quote(room.title())
				+ ",\"color\":\"" + String.format("#%06X", ACCENT & 0xFFFFFF) + "\",\"bold\":true},{\"text\":"
				+ quote(RoomPlan.headerMiddle(room) + online + " онлайн") + ",\"color\":\"white\",\"bold\":false}]");
		command("bossbar set phase:room players @a");
		command("bossbar set phase:room visible true");
		hideBarAt = ticks + WELCOME_TICKS;
	}

	/** Строка для текста в команде: кавычки и обратная косая черта экранируются. */
	private static String quote(String text) {
		return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private void syncGlow() {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String name = player.getScoreboardName();
			boolean want = RoomPlan.glows(room, name);
			if (want && !player.hasGlowingTag()) {
				player.setGlowingTag(true);
				glowing.add(name);
			} else if (!want && glowing.remove(name)) {
				player.setGlowingTag(false);
			}
		}
	}

	/** Пульс для датапака: пока он больше нуля, датапак не применяет оформление сам. */
	private void pulse() {
		Scoreboard scoreboard = server.getScoreboard();
		Objective meta = scoreboard.getObjective(META_OBJECTIVE);
		if (meta != null) {
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(PULSE_HOLDER), meta).set(PULSE);
		}
	}

	private void standDown(String reason) {
		if (!standingDown) {
			LOG.warn("Phase Companion отходит в сторону: {}", reason);
		}
		standingDown = true;
		room = Room.CLOSED;
		((PlayerLimit) server).phase$setPlayerLimit(PlayerLimit.VANILLA);
		restoreAccess();
	}

	/**
	 * Вход без проверки аккаунта — по настройке хоста в Phase. Проверяется раз в секунду:
	 * если игра сама вернула проверку (например, при открытии для сети), мод снимает её
	 * снова, пока настройка не изменится.
	 */
	private void applyAccess() {
		if (room.accountCheck()) {
			restoreAccess();
			return;
		}
		PhaseServer phase = (PhaseServer) server;
		if (!phase.phase$guardNames()) {
			phase.phase$setGuardNames(true);
			LOG.info("Phase Companion: игроков пускают без проверки аккаунта (настройка хоста в Phase), занятый ник отклоняется");
		}
		// Игра могла вернуть проверку сама — снимаем молча, журнал не засоряем.
		if (server.usesAuthentication()) {
			server.setUsesAuthentication(false);
		}
	}

	private void restoreAccess() {
		PhaseServer phase = (PhaseServer) server;
		if (!phase.phase$guardNames()) {
			return;
		}
		phase.phase$setGuardNames(false);
		server.setUsesAuthentication(vanillaAuth);
		LOG.info("Phase Companion: проверка аккаунта при входе снова как в игре");
	}

	/**
	 * Отметка для Phase: мод работает в этом мире. Пишется, когда в мире появилась папка
	 * Phase; Phase сверяет время файла с открытием мира.
	 */
	private void writeAliveOnce() {
		if (aliveWritten || !Files.isDirectory(phaseDir)) {
			return;
		}
		aliveWritten = true;
		String body = "{\"mod\":\"phase_companion\",\"loader\":\"" + platform.loader() + "\",\"version\":\""
				+ platform.version() + "\"}\n";
		try {
			Path tmp = phaseDir.resolve("companion.ok.tmp");
			Files.writeString(tmp, body, StandardCharsets.UTF_8);
			Files.move(tmp, phaseDir.resolve("companion.ok"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException err) {
			LOG.warn("Phase Companion: не удалось записать companion.ok: {}", err.toString());
		}
	}

	private void command(String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}
}
