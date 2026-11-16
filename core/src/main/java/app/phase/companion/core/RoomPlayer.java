package app.phase.companion.core;

/**
 * Игрок комнаты.
 *
 * @param name        игровой ник, {@code [A-Za-z0-9_]{1,16}}
 * @param rank        место в {@code Tab}
 * @param label       текст в скобках перед ником ({@code HOST}, {@code VIP}) или {@code null}
 * @param nameColor   именованный цвет игры для ника ({@code blue}, {@code gold}) или {@code null}
 * @param prefixColor цвет префикса, RGB {@code 0xRRGGBB}
 * @param glow        контур сквозь стены
 */
public record RoomPlayer(
		String name,
		Rank rank,
		String label,
		String nameColor,
		int prefixColor,
		boolean glow) {

	/** Нужна ли игроку своя команда: без префикса и цвета оформлять нечего. */
	public boolean styled() {
		return label != null || nameColor != null;
	}

	/** Префикс в {@code Tab}, над головой и в чате: {@code [VIP] }. */
	public String prefixText() {
		return label == null ? null : "[" + label + "] ";
	}
}
