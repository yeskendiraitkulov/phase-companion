package app.phase.companion.core;

import java.util.List;

/**
 * Комната из {@code <мир>/phase/room.json}, уже проверенная {@link RoomParser}.
 *
 * @param active     оформление включено; {@code false} — убрать из мира всё своё
 * @param accountCheck проверять аккаунт игроков при входе (как в игре); {@code false} —
 *                   пускать без проверки. Не зависит от {@code active}
 * @param title      заголовок шапки ({@code PHASE} или свой у Phase Pro)
 * @param code       код комнаты или {@code null}
 * @param maxPlayers мест в комнате вместе с хостом
 * @param footer     показывать подвал «Работает на Phase» (Pro может выключить)
 * @param players    игроки в порядке {@code Tab}: хост, Pro, гости, внутри — по нику
 */
public record Room(
		boolean active,
		boolean accountCheck,
		String title,
		String code,
		int maxPlayers,
		boolean footer,
		List<RoomPlayer> players) {

	/** Заголовок по умолчанию. */
	public static final String DEFAULT_TITLE = "PHASE";

	/** Комнаты нет: файл отсутствует, закрыт или в неизвестном формате. */
	public static final Room CLOSED = new Room(false, true, DEFAULT_TITLE, null, 0, true, List.of());

	/** Оформления нет, но игроков пускают без проверки аккаунта. */
	public static final Room PLAIN_OPEN = new Room(false, false, DEFAULT_TITLE, null, 0, true, List.of());

	public Room {
		players = List.copyOf(players);
	}
}
