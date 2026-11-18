package app.phase.companion.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Читает {@code room.json}. Phase уже проверил значения, но мод проверяет их снова: файл
 * лежит в папке мира, и его может поправить кто угодно. Плохое поле не ломает комнату —
 * оно просто отбрасывается, как в датапаке.
 */
public final class RoomParser {

	/** Версия формата, которую понимает мод. Другая — мод молчит, работает датапак. */
	public static final int SCHEMA = 1;
	/** Больше игроков мод не открывает: встроенный сервер работает на ПК хоста в одном потоке. Тот же предел у Phase ({@code look::MAX_SLOTS}). */
	public static final int MAX_PLAYERS_CAP = 50;

	static final int TITLE_MAX_CHARS = 32;
	static final int PREFIX_MAX_CHARS = 8;
	static final int CODE_MAX_CHARS = 16;

	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Pattern CODE = Pattern.compile("[A-Za-z0-9-]{1," + CODE_MAX_CHARS + "}");
	private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");
	/** Цвета ника — именованные цвета игры: команда другого не принимает. */
	static final Set<String> NAME_COLORS = Set.of(
			"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
			"gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
	/** Цвет префикса по умолчанию — Ice Sapphire. */
	public static final int DEFAULT_ACCENT = 0x38BDF8;

	private RoomParser() {
	}

	/** Файл в неизвестном формате: мод не трогает мир. */
	public static final class UnsupportedRoomException extends Exception {
		public UnsupportedRoomException(String message) {
			super(message);
		}
	}

	public static Room parse(String json) throws UnsupportedRoomException {
		JsonObject root;
		try {
			JsonElement element = JsonParser.parseString(json);
			if (!element.isJsonObject()) {
				throw new UnsupportedRoomException("room.json — не объект");
			}
			root = element.getAsJsonObject();
		} catch (RuntimeException err) {
			throw new UnsupportedRoomException("room.json не читается: " + err.getMessage());
		}
		int schema = intOr(root, "schema", -1);
		if (schema != SCHEMA) {
			throw new UnsupportedRoomException("неизвестная версия формата: " + schema);
		}
		// Нет поля — проверка как в игре: так безопаснее для старых файлов.
		boolean accountCheck = boolOr(root, "account_check", true);
		if (!boolOr(root, "active", false)) {
			return accountCheck ? Room.CLOSED : Room.PLAIN_OPEN;
		}
		String title = title(stringOr(root, "title", null));
		String code = stringOr(root, "code", null);
		if (code != null && !CODE.matcher(code).matches()) {
			code = null;
		}
		int maxPlayers = Math.max(1, Math.min(intOr(root, "max_players", 1), 100));
		boolean footer = boolOr(root, "footer", true);
		return new Room(true, accountCheck, title, code, maxPlayers, footer, players(root));
	}

	private static List<RoomPlayer> players(JsonObject root) {
		List<RoomPlayer> out = new ArrayList<>();
		JsonElement raw = root.get("players");
		if (raw == null || !raw.isJsonArray()) {
			return out;
		}
		JsonArray array = raw.getAsJsonArray();
		for (JsonElement element : array) {
			if (!element.isJsonObject()) {
				continue;
			}
			JsonObject obj = element.getAsJsonObject();
			String name = stringOr(obj, "name", null);
			if (name == null || !NAME.matcher(name).matches()) {
				continue;
			}
			boolean duplicate = out.stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
			if (duplicate) {
				continue;
			}
			String label = prefix(stringOr(obj, "label", null));
			String nameColor = stringOr(obj, "name_color", null);
			if (nameColor != null && !NAME_COLORS.contains(nameColor)) {
				nameColor = null;
			}
			String hex = stringOr(obj, "prefix_color", null);
			int prefixColor = hex != null && HEX.matcher(hex).matches()
					? Integer.parseInt(hex.substring(1), 16)
					: DEFAULT_ACCENT;
			out.add(new RoomPlayer(name, Rank.parse(stringOr(obj, "rank", null)), label, nameColor,
					prefixColor, boolOr(obj, "glow", false)));
		}
		out.sort(Comparator.comparing(RoomPlayer::rank)
				.thenComparing(p -> p.name().toLowerCase(Locale.ROOT)));
		return out;
	}

	/** Заголовок: пробелы подряд — один, от 1 до 32 знаков, без опасных знаков. */
	static String title(String raw) {
		if (raw == null) {
			return Room.DEFAULT_TITLE;
		}
		String title = String.join(" ", raw.trim().split("\\s+"));
		int len = title.codePointCount(0, title.length());
		if (len == 0 || len > TITLE_MAX_CHARS || title.codePoints().anyMatch(RoomParser::isUnsafe)) {
			return Room.DEFAULT_TITLE;
		}
		return title;
	}

	/** Префикс: 1–8 знаков, буквы и цифры любого алфавита и {@code - _ . ! + * # &}. */
	static String prefix(String raw) {
		if (raw == null) {
			return null;
		}
		String prefix = raw.trim();
		int len = prefix.codePointCount(0, prefix.length());
		if (len == 0 || len > PREFIX_MAX_CHARS) {
			return null;
		}
		boolean ok = prefix.codePoints().allMatch(c -> !isUnsafe(c)
				&& (Character.isLetterOrDigit(c) || "-_.!+*#&".indexOf(c) >= 0));
		return ok ? prefix : null;
	}

	/** То же правило, что в Phase: управляющие, кавычка, {@code \}, {@code §} и невидимые знаки. */
	static boolean isUnsafe(int c) {
		return Character.isISOControl(c)
				|| c == '"' || c == '\\' || c == '§'
				|| c == 0x00AD || c == 0x061C
				|| (c >= 0x200B && c <= 0x200F)
				|| (c >= 0x2028 && c <= 0x202E)
				|| (c >= 0x2060 && c <= 0x206F)
				|| c == 0xFEFF;
	}

	private static String stringOr(JsonObject obj, String key, String fallback) {
		JsonElement value = obj.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
			return fallback;
		}
		return value.getAsString();
	}

	private static int intOr(JsonObject obj, String key, int fallback) {
		JsonElement value = obj.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return fallback;
		}
		try {
			return value.getAsInt();
		} catch (NumberFormatException err) {
			return fallback;
		}
	}

	private static boolean boolOr(JsonObject obj, String key, boolean fallback) {
		JsonElement value = obj.get(key);
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
			return fallback;
		}
		return value.getAsBoolean();
	}
}
