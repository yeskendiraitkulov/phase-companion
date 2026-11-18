package app.phase.companion.core;

/**
 * A room player.
 *
 * @param name        in-game nickname, {@code [A-Za-z0-9_]{1,16}}
 * @param rank        place in {@code Tab}
 * @param label       text in brackets before the nickname ({@code HOST}, {@code VIP}) or {@code null}
 * @param nameColor   named game color for the nickname ({@code blue}, {@code gold}) or {@code null}
 * @param prefixColor prefix color, RGB {@code 0xRRGGBB}
 * @param glow        outline through walls
 */
public record RoomPlayer(
		String name,
		Rank rank,
		String label,
		String nameColor,
		int prefixColor,
		boolean glow) {

	/** Whether the player needs a team of their own: with no prefix and no color there is nothing to style. */
	public boolean styled() {
		return label != null || nameColor != null;
	}

	/** Prefix in {@code Tab}, above the head and in chat: {@code [VIP] }. */
	public String prefixText() {
		return label == null ? null : "[" + label + "] ";
	}
}
