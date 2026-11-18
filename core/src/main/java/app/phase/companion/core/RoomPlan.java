package app.phase.companion.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** What to do in the world for a room. Pure functions: the per-version adapter only executes. */
public final class RoomPlan {

	/** Phase commands: {@code ph.} + slot letter + two digits, same as the datapack. */
	private static final Pattern TEAM = Pattern.compile("ph\\.[a-c][0-9]{2}");

	private RoomPlan() {
	}

	/**
	 * A player's own command.
	 *
	 * @param team   team name ({@code ph.b01})
	 * @param player nickname
	 * @param style  color and prefix
	 */
	public record TeamSpec(String team, String player, RoomPlayer style) {
	}

	/** Command created by Phase (the datapack or the mod) and owned by it. */
	public static boolean isPhaseTeam(String name) {
		return name != null && TEAM.matcher(name).matches();
	}

	/**
	 * Commands for styled players in {@code Tab} order. Names are the same as the
	 * datapack hands out: switching between datapack and mod does not change the {@code Tab} order.
	 */
	public static List<TeamSpec> teams(Room room) {
		List<TeamSpec> out = new ArrayList<>();
		if (!room.active()) {
			return out;
		}
		int next = 0;
		for (RoomPlayer player : room.players()) {
			if (!player.styled() || next >= 100) {
				continue;
			}
			out.add(new TeamSpec(String.format("ph.%c%02d", player.rank().letter(), next), player.name(), player));
			next++;
		}
		return out;
	}

	/**
	 * Integrated server limit. The mod only raises it (up to 50) and never lowers it below
	 * vanilla: Phase itself controls the room size, while the game limit applies to everyone who joins.
	 */
	public static int maxPlayers(int vanilla, Room room) {
		if (!room.active()) {
			return vanilla;
		}
		return Math.max(vanilla, Math.min(room.maxPlayers(), RoomParser.MAX_PLAYERS_CAP));
	}

	/** Middle part of the header: {@code " • K7F-2QX • "} or {@code " • "} without a code. */
	public static String headerMiddle(Room room) {
		return room.code() == null ? " • " : " • " + room.code() + " • ";
	}

	/** Word after the player count in the header: {@code " онлайн"} or {@code " online"}. */
	public static String onlineWord(Room room) {
		return room.lang().equals("ru") ? " онлайн" : " online";
	}

	/** {@code Tab} footer in the room's language. */
	public static String footerText(Room room) {
		return room.lang().equals("ru") ? "Работает на Phase" : "Powered by Phase";
	}

	/** Whether the player should glow. */
	public static boolean glows(Room room, String player) {
		return room.active() && room.players().stream()
				.anyMatch(p -> p.glow() && p.name().equalsIgnoreCase(player));
	}
}
