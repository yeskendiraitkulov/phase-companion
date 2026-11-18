package app.phase.companion.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fixtures in {@code resources/rooms} are written by a Rust test in the Phase app:
 * both sides read the same bytes.
 */
class RoomParserTest {

	static String fixture(String name) throws IOException {
		try (InputStream in = RoomParserTest.class.getResourceAsStream("/rooms/" + name)) {
			if (in == null) {
				throw new IOException("missing fixture " + name);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	void basicRoom() throws Exception {
		Room room = RoomParser.parse(fixture("basic.json"));
		assertTrue(room.active());
		assertEquals("PHASE", room.title());
		assertEquals("K7F-2QX", room.code());
		assertEquals(8, room.maxPlayers());
		assertTrue(room.footer(), "footer is on by default");
		assertEquals(2, room.players().size());
		RoomPlayer host = room.players().get(0);
		assertEquals("Steve", host.name());
		assertEquals(Rank.HOST, host.rank());
		assertEquals("[HOST] ", host.prefixText());
		assertNull(host.nameColor());
		assertEquals(0x38BDF8, host.prefixColor());
		RoomPlayer guest = room.players().get(1);
		assertFalse(guest.styled());

		// Like the datapack: a team only for the host, none for a guest without style.
		assertEquals(List.of(new RoomPlan.TeamSpec("ph.a00", "Steve", host)), RoomPlan.teams(room));
		assertEquals(8, RoomPlan.maxPlayers(8, room), "the vanilla limit is not lowered");
		assertEquals(" • K7F-2QX • ", RoomPlan.headerMiddle(room));
	}

	@Test
	void styledRoom() throws Exception {
		Room room = RoomParser.parse(fixture("styled.json"));
		assertEquals("Остров Саита", room.title());
		assertFalse(room.footer(), "host removed the footer");
		assertEquals(50, room.maxPlayers());
		assertEquals(50, RoomPlan.maxPlayers(8, room), "up to 50 slots with the mod");
		List<RoomPlan.TeamSpec> teams = RoomPlan.teams(room);
		// Host first, guests by name; a guest without style gets no team.
		assertEquals(List.of("ph.a00", "ph.c01", "ph.c02"), teams.stream().map(RoomPlan.TeamSpec::team).toList());
		assertEquals(List.of("Sait", "Den4ik", "Kira"), teams.stream().map(RoomPlan.TeamSpec::player).toList());
		RoomPlayer kira = teams.get(2).style();
		assertEquals("gold", kira.nameColor());
		assertEquals(0xFBBF24, kira.prefixColor());
		assertEquals("[Строй] ", teams.get(1).style().prefixText());
		assertTrue(RoomPlan.glows(room, "sait"));
		assertFalse(RoomPlan.glows(room, "Kira"));
	}

	@Test
	void dirtyRoomIsAlreadyClean() throws Exception {
		Room room = RoomParser.parse(fixture("dirty.json"));
		assertEquals("PHASE", room.title());
		assertNull(room.code());
		assertEquals(1, room.players().size());
		assertNull(room.players().get(0).label());
		assertEquals(" • ", RoomPlan.headerMiddle(room));
	}

	@Test
	void closedRoom() throws Exception {
		Room room = RoomParser.parse(fixture("closed.json"));
		assertFalse(room.active());
		assertTrue(RoomPlan.teams(room).isEmpty());
		assertEquals(8, RoomPlan.maxPlayers(8, room));
	}

	/** The file was edited by hand: the mod checks everything itself and drops bad data. */
	@Test
	void handEditedFileIsFiltered() throws Exception {
		Room room = RoomParser.parse("""
				{ "schema": 1, "active": true, "title": "a\\"b", "code": "x y", "max_players": 500,
				  "players": [
				    { "name": "Bad Name", "rank": "host" },
				    { "name": "Zed", "rank": "guest", "label": "toolongprefix", "name_color": "#FF0000",
				      "prefix_color": "red" },
				    { "name": "zed", "rank": "pro", "label": "dup" },
				    { "name": "Amy", "rank": "wizard", "label": "§cX" },
				    { "name": "Bob", "rank": "pro", "label": " OK ", "name_color": "aqua" },
				    "junk", 5
				  ] }
				""");
		assertEquals("PHASE", room.title());
		assertNull(room.code());
		assertEquals(100, room.maxPlayers(), "the parser allows up to 100");
		assertEquals(50, RoomPlan.maxPlayers(8, room), "no more than 50");
		assertEquals(List.of("Bob", "Amy", "Zed"), room.players().stream().map(RoomPlayer::name).toList());
		RoomPlayer zed = room.players().get(2);
		assertNull(zed.label());
		assertNull(zed.nameColor());
		assertEquals(RoomParser.DEFAULT_ACCENT, zed.prefixColor());
		assertEquals(Rank.GUEST, room.players().get(1).rank(), "unknown role means guest");
		assertNull(room.players().get(1).label(), "§ is rejected");
		assertEquals("OK", room.players().get(0).label());
	}

	/** Slots: not fewer than the vanilla 8 (Phase controls the room size) and not more than 50. */
	@Test
	void playerLimitIsClampedBetweenVanillaAndFifty() throws Exception {
		int[][] cases = { { 1, 8 }, { 5, 8 }, { 8, 8 }, { 9, 9 }, { 20, 20 }, { 35, 35 }, { 50, 50 }, { 51, 50 }, { 100, 50 } };
		for (int[] c : cases) {
			Room room = RoomParser.parse("{\"schema\":1,\"active\":true,\"max_players\":" + c[0] + "}");
			assertEquals(c[1], RoomPlan.maxPlayers(8, room), "max_players " + c[0]);
		}
		assertEquals(50, RoomParser.MAX_PLAYERS_CAP);
		// Room closed: the game limit stays as it was.
		assertEquals(8, RoomPlan.maxPlayers(8, RoomParser.parse("{\"schema\":1,\"active\":false,\"max_players\":40}")));
	}

	@Test
	void accountCheckFlag() throws Exception {
		assertTrue(RoomParser.parse(fixture("basic.json")).accountCheck());
		Room styled = RoomParser.parse(fixture("styled.json"));
		assertFalse(styled.accountCheck());
		assertTrue(styled.active());
		// Styling is off but login without verification is on.
		Room plain = RoomParser.parse(fixture("plain_open.json"));
		assertFalse(plain.active());
		assertFalse(plain.accountCheck());
		assertTrue(RoomPlan.teams(plain).isEmpty());
		assertEquals(8, RoomPlan.maxPlayers(8, plain));
		// No field or not a boolean: verification as in the game.
		assertTrue(RoomParser.parse("{\"schema\":1,\"active\":false}").accountCheck());
		assertTrue(RoomParser.parse("{\"schema\":1,\"active\":true,\"account_check\":\"no\"}").accountCheck());
	}

	@Test
	void roomLanguage() throws Exception {
		// Rust writes the room language: Russian and English labels.
		Room ru = RoomParser.parse(fixture("basic.json"));
		assertEquals("ru", ru.lang());
		assertEquals(" онлайн", RoomPlan.onlineWord(ru));
		assertEquals("Работает на Phase", RoomPlan.footerText(ru));
		Room en = RoomParser.parse(fixture("basic_en.json"));
		assertEquals("en", en.lang());
		assertEquals(" online", RoomPlan.onlineWord(en));
		assertEquals("Powered by Phase", RoomPlan.footerText(en));
		// The rest of the file is the same.
		assertEquals(ru.players(), en.players());
		assertEquals(ru.code(), en.code());
		// A file from an old version with no field reads as Russian, as before.
		Room legacy = RoomParser.parse("{\"schema\":1,\"active\":true,\"code\":\"K7F-2QX\",\"max_players\":5}");
		assertEquals("ru", legacy.lang());
		assertEquals(" онлайн", RoomPlan.onlineWord(legacy));
		// Unknown fields are tolerated; unknown language and regional codes.
		assertEquals("en", RoomParser.parse("{\"schema\":1,\"active\":true,\"lang\":\"de\",\"future\":1}").lang());
		assertEquals("ru", RoomParser.parse("{\"schema\":1,\"active\":true,\"lang\":\"ru-RU\"}").lang());
		assertEquals("en", RoomParser.parse("{\"schema\":1,\"active\":true,\"lang\":\"EN\"}").lang());
		assertEquals("ru", RoomParser.parse("{\"schema\":1,\"active\":true,\"lang\":7}").lang());
	}

	@Test
	void unknownSchemaOrJunkIsUnsupported() {
		assertThrows(RoomParser.UnsupportedRoomException.class,
				() -> RoomParser.parse("{\"schema\": 2, \"active\": true}"));
		assertThrows(RoomParser.UnsupportedRoomException.class, () -> RoomParser.parse("{"));
		assertThrows(RoomParser.UnsupportedRoomException.class, () -> RoomParser.parse("[]"));
	}

	@Test
	void teamNamesBelongToPhase() {
		assertTrue(RoomPlan.isPhaseTeam("ph.a00"));
		assertTrue(RoomPlan.isPhaseTeam("ph.c99"));
		assertFalse(RoomPlan.isPhaseTeam("ph.d00"));
		assertFalse(RoomPlan.isPhaseTeam("red"));
		assertFalse(RoomPlan.isPhaseTeam("ph.a0"));
	}

	@Test
	void watcherReadsOnlyOnChange(@TempDir Path dir) throws Exception {
		Path file = dir.resolve("room.json");
		RoomWatcher watcher = new RoomWatcher(file);
		assertEquals(new RoomWatcher.Changed(Room.CLOSED), watcher.poll().orElseThrow(), "no file means no room");
		assertTrue(watcher.poll().isEmpty());

		Files.writeString(file, fixture("basic.json"));
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000));
		RoomWatcher.Changed changed = (RoomWatcher.Changed) watcher.poll().orElseThrow();
		assertEquals("K7F-2QX", changed.room().code());
		assertTrue(watcher.poll().isEmpty(), "time did not change");

		Files.writeString(file, "{\"schema\": 9}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(2_000_000));
		assertTrue(watcher.poll().orElseThrow() instanceof RoomWatcher.Unsupported);

		Files.delete(file);
		assertEquals(new RoomWatcher.Changed(Room.CLOSED), watcher.poll().orElseThrow());
	}
}
