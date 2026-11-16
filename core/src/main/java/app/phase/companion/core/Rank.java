package app.phase.companion.core;

/** Кто игрок в комнате. Порядок — порядок в {@code Tab}: хост первым. */
public enum Rank {
	HOST('a'),
	PRO('b'),
	GUEST('c');

	private final char letter;

	Rank(char letter) {
		this.letter = letter;
	}

	/** Буква в имени команды: {@code Tab} сортирует игроков по имени команды. */
	public char letter() {
		return letter;
	}

	/** Значение из {@code room.json}; неизвестное — гость. */
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
