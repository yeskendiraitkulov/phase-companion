package app.phase.companion.core;

import java.util.List;

/**
 * The room from {@code <world>/phase/room.json}, already validated by {@link RoomParser}.
 *
 * @param active     styling is on; {@code false} means remove everything the mod added
 * @param accountCheck verify players' accounts on login (as in the game); {@code false} means
 *                   let them in without verification. Independent of {@code active}
 * @param title      header title ({@code PHASE} or the host's own title)
 * @param code       room code or {@code null}
 * @param maxPlayers slots in the room, host included
 * @param footer     show the "Powered by Phase" footer (the host can turn it off)
 * @param lang       room language: {@code ru} or {@code en}; {@code ru} if the file has no field
 * @param players    players in {@code Tab} order: host, {@code pro} (old format), guests, by name within each group
 */
public record Room(
		boolean active,
		boolean accountCheck,
		String title,
		String code,
		int maxPlayers,
		boolean footer,
		String lang,
		List<RoomPlayer> players) {

	/** Language if the file has none: how every version behaved before translation. */
	public static final String DEFAULT_LANG = "ru";

	/** Default title. */
	public static final String DEFAULT_TITLE = "PHASE";

	/** No room: the file is missing, closed or in an unknown format. */
	public static final Room CLOSED = new Room(false, true, DEFAULT_TITLE, null, 0, true, DEFAULT_LANG, List.of());

	/** No styling, but players are let in without account verification. */
	public static final Room PLAIN_OPEN = new Room(false, false, DEFAULT_TITLE, null, 0, true, DEFAULT_LANG, List.of());

	public Room {
		players = List.copyOf(players);
	}
}
