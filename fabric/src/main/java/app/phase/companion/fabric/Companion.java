package app.phase.companion.fabric;

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
import net.minecraft.world.scores.TeamColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Phase Companion в одном мире: живёт от первого тика встроенного сервера до остановки.
 *
 * <p>Всё на потоке сервера. Раз в секунду: пульс для датапака, проверка {@code room.json}.
 * Каждый тик — только сравнение числа игроков, чтобы обновить шапку при входе и выходе.
 */
public final class Companion {
	static final Logger LOG = LoggerFactory.getLogger("phase_companion");

	/** Совпадает с {@code COMPANION_SCORE} и {@code COMPANION_PULSE} в {@code datapack.rs}. */
	static final String PULSE_HOLDER = "#companion";
	static final int PULSE = 5;
	static final String META_OBJECTIVE = "phase.meta";

	private static final int ACCENT = RoomParser.DEFAULT_ACCENT;
	private static final int SECOND = 20;

	private final MinecraftServer server;
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
	/** Кому мод включил свечение: выключает только им, чужое свечение не трогает. */
	private final Set<String> glowing = new HashSet<>();

	private Companion(MinecraftServer server) {
		this.server = server;
		this.phaseDir = server.getWorldPath(LevelResource.ROOT).resolve("phase");
		this.watcher = new RoomWatcher(phaseDir.resolve("room.json"));
		((PhaseServer) server).phase$setPlayerLimit(PhaseServer.VANILLA);
		this.vanillaMax = server.getMaxPlayers();
		this.vanillaAuth = server.usesAuthentication();
	}

	/** Только встроенный сервер одиночной игры: свой сервер настраивается сам. */
	public static Companion create(MinecraftServer server) {
		if (server.isDedicatedServer()) {
			return null;
		}
		LOG.info("Phase Companion: мир {}", server.getWorldPath(LevelResource.ROOT).getFileName());
		return new Companion(server);
	}

	public void tick() {
		int online = server.getPlayerCount();
		if (online != lastOnline) {
			lastOnline = online;
			if (!standingDown && room.active()) {
				sendTabList(online);
			}
		}
		if (++ticks % SECOND != 0) {
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
		room = next;
		applyAccess();
		applyTeams();
		((PhaseServer) server).phase$setPlayerLimit(RoomPlan.maxPlayers(vanillaMax, room));
		// Шапку теперь даёт настоящий Tab: bossbar датапака не нужен.
		command("bossbar remove phase:room");
		if (room.active()) {
			sendTabList(server.getPlayerCount());
			syncGlow();
		} else {
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
			// С 26.x цвет команды — отдельное перечисление TeamColor, а не ChatFormatting.
			team.setColor(Optional.ofNullable(style.nameColor()).map(TeamColor::byName));
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
		((PhaseServer) server).phase$setPlayerLimit(PhaseServer.VANILLA);
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
		((PhaseServer) server).phase$setGuardNames(true);
		if (server.usesAuthentication()) {
			server.setUsesAuthentication(false);
			LOG.info("Phase Companion: игроков пускают без проверки аккаунта (настройка хоста в Phase)");
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
		String body = "{\"mod\":\"phase_companion\",\"loader\":\"fabric\",\"version\":\""
				+ Companion.class.getPackage().getImplementationVersion() + "\"}\n";
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
