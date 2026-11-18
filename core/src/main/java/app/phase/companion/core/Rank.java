package app.phase.companion.core;

/** Who the player is in the room. Order is the {@code Tab} order: host first. */
public enum Rank {
	HOST('a'),
	PRO('b'),
	GUEST('c');

	private final char letter;

	Rank(char letter) {
		this.letter = letter;
	}

	/** Letter in the team name: {@code Tab} sorts players by team name. */
	public char letter() {
		return letter;
	}

	/** Value from {@code room.json}; an unknown one means guest. */
	public static Rank parse(String raw) {
		if (raw == null) {
			return GUEST;
		}
		return switch (raw) {
			case "host" -> HOST;
			case "pro" -> PRO;
			default -> GUEST;
		};
	}
}
