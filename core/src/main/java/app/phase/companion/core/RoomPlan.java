package app.phase.companion.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Что сделать в мире по комнате. Чистые функции: адаптер под версию игры только исполняет. */
public final class RoomPlan {

	/** Команды Phase: {@code ph.} + буква места + две цифры — как у датапака. */
	private static final Pattern TEAM = Pattern.compile("ph\\.[a-c][0-9]{2}");

	private RoomPlan() {
	}

	/**
	 * Своя команда игрока.
	 *
	 * @param team   имя команды ({@code ph.b01})
	 * @param player ник
	 * @param style  цвет и префикс
	 */
	public record TeamSpec(String team, String player, RoomPlayer style) {
	}

	/** Команда создана Phase (датапаком или модом) и принадлежит ему. */
	public static boolean isPhaseTeam(String name) {
		return name != null && TEAM.matcher(name).matches();
	}

	/**
	 * Команды для оформленных игроков в порядке {@code Tab}. Имена те же, что раздаёт
	 * датапак: переход между датапаком и модом не меняет порядок в {@code Tab}.
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
	 * Лимит встроенного сервера. Мод только поднимает его (до 20), но не опускает ниже
	 * ванильного: размер комнаты держит сам Phase, а лимит игры — для всех, кто входит.
	 */
	public static int maxPlayers(int vanilla, Room room) {
		if (!room.active()) {
			return vanilla;
		}
		return Math.max(vanilla, Math.min(room.maxPlayers(), RoomParser.MAX_PLAYERS_CAP));
	}

	/** Средняя часть шапки: {@code " • K7F-2QX • "} или {@code " • "} без кода. */
	public static String headerMiddle(Room room) {
		return room.code() == null ? " • " : " • " + room.code() + " • ";
	}

	/** Должен ли игрок светиться. */
	public static boolean glows(Room room, String player) {
		return room.active() && room.players().stream()
				.anyMatch(p -> p.glow() && p.name().equalsIgnoreCase(player));
	}
}
