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
 * Reads {@code room.json}. Phase has already checked the values, but the mod checks them again: the file
 * sits in the world folder and anyone can edit it. A bad field does not break the room;
 * it is simply dropped, like in the datapack.
 */
public final class RoomParser {

	/** Format version the mod understands. Any other: the mod stays quiet and the datapack works. */
	public static final int SCHEMA = 1;
	/** The mod never opens more slots: the integrated server runs on the host's PC in a single thread. Same cap in Phase ({@code look::MAX_SLOTS}). */
	public static final int MAX_PLAYERS_CAP = 50;

	static final int TITLE_MAX_CHARS = 32;
	static final int PREFIX_MAX_CHARS = 8;
	static final int CODE_MAX_CHARS = 16;

	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
	private static final Pattern CODE = Pattern.compile("[A-Za-z0-9-]{1," + CODE_MAX_CHARS + "}");
	private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");
	/** Name colors are the game's named colors: a team accepts no others. */
	static final Set<String> NAME_COLORS = Set.of(
			"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold",
			"gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white");
	/** Default prefix color: Ice Sapphire. */
	public static final int DEFAULT_ACCENT = 0x38BDF8;

	private RoomParser() {
	}

	/** File in an unknown format: the mod does not touch the world. */
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
				throw new UnsupportedRoomException("room.json is not an object");
			}
			root = element.getAsJsonObject();
		} catch (RuntimeException err) {
			throw new UnsupportedRoomException("room.json is unreadable: " + err.getMessage());
		}
		int schema = intOr(root, "schema", -1);
		if (schema != SCHEMA) {
			throw new UnsupportedRoomException("unknown format version: " + schema);
		}
		// No field: verification as in the game, which is safer for old files.
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
		return new Room(true, accountCheck, title, code, maxPlayers, footer,
				lang(stringOr(root, "lang", null)), players(root));
	}

	/**
	 * Room language. No field (file from an old Phase version) means Russian, as earlier
	 * versions behaved; a code the mod does not know means English, like in Phase.
	 */
	static String lang(String raw) {
		if (raw == null) {
			return Room.DEFAULT_LANG;
		}
		String primary = raw.trim().split("[-_]", 2)[0].toLowerCase(Locale.ROOT);
		return primary.equals("ru") ? "ru" : "en";
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

	/** Title: runs of spaces collapse to one, 1 to 32 characters, no dangerous characters. */
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

	/** Prefix: 1-8 characters, letters and digits of any alphabet and {@code - _ . ! + * # &}. */
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

	/** Same rule as in Phase: control characters, quote, {@code \}, {@code §} and invisible characters. */
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
