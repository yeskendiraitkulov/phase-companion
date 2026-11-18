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
 * Phase Companion inside one world: lives from the integrated server's first tick until it stops.
 *
 * <p>Everything runs on the server thread. Once a second: the datapack pulse and a {@code room.json} check.
 * Every tick only compares the player count, to refresh the header when someone joins or leaves.
 *
 * <p>Shared by all loaders and game versions: touches only game classes.
 * What differs per loader is how {@link #tick()} and {@link #stop()} are called and where
 * the mod version comes from ({@link Platform}).
 */
public final class Companion {
	static final Logger LOG = LoggerFactory.getLogger("phase_companion");

	/** Matches {@code COMPANION_SCORE} and {@code COMPANION_PULSE} in {@code datapack.rs}. */
	static final String PULSE_HOLDER = "#companion";
	static final int PULSE = 5;
	static final String META_OBJECTIVE = "phase.meta";

	private static final int ACCENT = RoomParser.DEFAULT_ACCENT;
	private static final int SECOND = 20;
	/** How long the join bar stays visible: 10 seconds, same as the datapack. */
	private static final int WELCOME_TICKS = 10 * SECOND;

	private final MinecraftServer server;
	private final Platform platform;
	private final Path phaseDir;
	private final RoomWatcher watcher;
	private final int vanillaMax;
	/** Account verification as the game configured it; the mod restores exactly that. */
	private final boolean vanillaAuth;

	private Room room = Room.CLOSED;
	/** File in an unknown format: the mod stays quiet and the datapack does the styling. */
	private boolean standingDown;
	private boolean aliveWritten;
	private int ticks;
	private int lastOnline = -1;
	/** Tick at which to hide the join bar; -1 means no bar. */
	private int hideBarAt = -1;
	/** Players the mod enabled glow for: only those are turned off again, glow set by others is left alone. */
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

	/** Integrated singleplayer server only; a dedicated server configures itself. */
	public static Companion create(MinecraftServer server, Platform platform) {
		if (server.isDedicatedServer()) {
			return null;
		}
		// The world root path ends in ".": take the folder name from the normalized path.
		LOG.info("Phase Companion {}: world '{}'", platform.version(),
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
		// Commands stay in the save, like the datapack's: the next time the world opens, Phase
		// or the mod will bring them in line with the current room; a closed room removes them.
		glowing.clear();
	}

	// ------------------------------------------------------------------ applying

	private void apply(Room next) {
		boolean wasActive = room.active();
		room = next;
		applyAccess();
		applyTeams();
		((PlayerLimit) server).phase$setPlayerLimit(RoomPlan.maxPlayers(vanillaMax, room));
		if (room.active()) {
			sendTabList(server.getPlayerCount());
			// Header on join for 10 seconds, like the datapack (the Phase window promises it): when the
			// room opened. After that on every join (tick); a styling edit does not trigger it.
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
			// Same as the datapack cleanup: Phase may delete the datapack from the world.
			command("data modify storage phase:state clean set value 1b");
		}
		LOG.info("Phase Companion: room {}", room.active() ? "open, slots: " + room.maxPlayers() : "closed");
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
			// Free ones only: Phase does not touch a command from a map or another datapack.
			PlayerTeam current = scoreboard.getPlayersTeam(spec.player());
			if (current == null || (RoomPlan.isPhaseTeam(current.getName()) && current != team)) {
				scoreboard.addPlayerToTeam(spec.player(), team);
			}
		}
	}

	private void sendTabList(int online) {
		MutableComponent header = Component.empty()
				.append(Component.literal(room.title()).withStyle(s -> s.withColor(ACCENT).withBold(true)))
				.append(Component.literal(RoomPlan.headerMiddle(room) + online + RoomPlan.onlineWord(room)).withStyle(ChatFormatting.WHITE));
		Component footer = room.footer()
				? Component.literal(RoomPlan.footerText(room)).withStyle(ChatFormatting.GRAY)
				: Component.empty();
		server.getPlayerList().broadcastAll(new ClientboundTabListPacket(header, footer));
	}

	/**
	 * Top-of-screen bar "PHASE • code • N online" for all players for {@link #WELCOME_TICKS}:
	 * when the room opens and on every join. Done with game commands, not the bossbar API: the command
	 * syntax is the same on all versions and loaders, like the datapack's (same `phase:room` name).
	 */
	private void showWelcomeBar(int online) {
		// The datapack also showed this bar when the world loaded and scheduled `phase:hide` in 200
		// game ticks, but while the Esc menu is open a singleplayer world is paused and its timer
		// fires after our show. The mod drives the bar, so we cancel the datapack's timer.
		command("schedule clear phase:hide");
		command("bossbar add phase:room {\"text\":\"PHASE\"}");
		command("bossbar set phase:room color blue");
		command("bossbar set phase:room max " + Math.max(1, room.maxPlayers()));
		command("bossbar set phase:room value " + Math.min(online, Math.max(1, room.maxPlayers())));
		command("bossbar set phase:room name [{\"text\":" + quote(room.title())
				+ ",\"color\":\"" + String.format("#%06X", ACCENT & 0xFFFFFF) + "\",\"bold\":true},{\"text\":"
				+ quote(RoomPlan.headerMiddle(room) + online + RoomPlan.onlineWord(room)) +",\"color\":\"white\",\"bold\":false}]");
		command("bossbar set phase:room players @a");
		command("bossbar set phase:room visible true");
		hideBarAt = ticks + WELCOME_TICKS;
	}

	/** String for command text: quotes and backslashes are escaped. */
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

	/** Pulse for the datapack: while it is above zero the datapack does not apply styling itself. */
	private void pulse() {
		Scoreboard scoreboard = server.getScoreboard();
		Objective meta = scoreboard.getObjective(META_OBJECTIVE);
		if (meta != null) {
			scoreboard.getOrCreatePlayerScore(ScoreHolder.forNameOnly(PULSE_HOLDER), meta).set(PULSE);
		}
	}

	private void standDown(String reason) {
		if (!standingDown) {
			LOG.warn("Phase Companion standing down: {}", reason);
		}
		standingDown = true;
		room = Room.CLOSED;
		((PlayerLimit) server).phase$setPlayerLimit(PlayerLimit.VANILLA);
		restoreAccess();
	}

	/**
	 * Login without account verification, per the host's setting in Phase. Checked once a second:
	 * if the game restored verification itself (for example on Open to LAN), the mod turns it off
	 * again until the setting changes.
	 */
	private void applyAccess() {
		if (room.accountCheck()) {
			restoreAccess();
			return;
		}
		PhaseServer phase = (PhaseServer) server;
		if (!phase.phase$guardNames()) {
			phase.phase$setGuardNames(true);
			LOG.info("Phase Companion: players join without account verification (host setting in Phase), a taken name is rejected");
		}
		// The game may have restored verification itself: turn it off quietly, without spamming the log.
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
		LOG.info("Phase Companion: login verification is back to the game default");
	}

	/**
	 * Marker for Phase: the mod is working in this world. Written once the world has a Phase
	 * folder; Phase compares the file time with the moment the world was opened.
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
			LOG.warn("Phase Companion: could not write companion.ok: {}", err.toString());
		}
	}

	private void command(String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}
}
