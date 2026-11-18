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
 * Эталоны в {@code resources/rooms} пишет Rust-тест
 * {@code crates/minecraft/tests/companion_fixtures.rs}: обе стороны читают одни байты.
 */
class RoomParserTest {

	static String fixture(String name) throws IOException {
		try (InputStream in = RoomParserTest.class.getResourceAsStream("/rooms/" + name)) {
			if (in == null) {
				throw new IOException("нет эталона " + name);
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
		assertTrue(room.footer(), "подвал по умолчанию включён");
		assertEquals(2, room.players().size());
		RoomPlayer host = room.players().get(0);
		assertEquals("Steve", host.name());
		assertEquals(Rank.HOST, host.rank());
		assertEquals("[HOST] ", host.prefixText());
		assertNull(host.nameColor());
		assertEquals(0x38BDF8, host.prefixColor());
		RoomPlayer guest = room.players().get(1);
		assertFalse(guest.styled());

		// Как у датапака: команда только хосту, гостю без стиля — нет.
		assertEquals(List.of(new RoomPlan.TeamSpec("ph.a00", "Steve", host)), RoomPlan.teams(room));
		assertEquals(8, RoomPlan.maxPlayers(8, room), "ванильный лимит не опускается");
		assertEquals(" • K7F-2QX • ", RoomPlan.headerMiddle(room));
	}

	@Test
	void styledRoom() throws Exception {
		Room room = RoomParser.parse(fixture("styled.json"));
		assertEquals("Остров Саита", room.title());
		assertFalse(room.footer(), "хост убрал подвал");
		assertEquals(50, room.maxPlayers());
		assertEquals(50, RoomPlan.maxPlayers(8, room), "до 50 мест с модом");
		List<RoomPlan.TeamSpec> teams = RoomPlan.teams(room);
		// Хост первым, гости по нику; гость без стиля команды не получает.
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

	/** Файл поправили руками: мод проверяет всё сам и отбрасывает плохое. */
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
		assertEquals(100, room.maxPlayers(), "парсер держит до 100");
		assertEquals(50, RoomPlan.maxPlayers(8, room), "не больше 50");
		assertEquals(List.of("Bob", "Amy", "Zed"), room.players().stream().map(RoomPlayer::name).toList());
		RoomPlayer zed = room.players().get(2);
		assertNull(zed.label());
		assertNull(zed.nameColor());
		assertEquals(RoomParser.DEFAULT_ACCENT, zed.prefixColor());
		assertEquals(Rank.GUEST, room.players().get(1).rank(), "неизвестная роль — гость");
		assertNull(room.players().get(1).label(), "§ не проходит");
		assertEquals("OK", room.players().get(0).label());
	}

	/** Места: не меньше ванильных 8 (размер комнаты держит Phase) и не больше 50. */
	@Test
	void playerLimitIsClampedBetweenVanillaAndFifty() throws Exception {
		int[][] cases = { { 1, 8 }, { 5, 8 }, { 8, 8 }, { 9, 9 }, { 20, 20 }, { 35, 35 }, { 50, 50 }, { 51, 50 }, { 100, 50 } };
		for (int[] c : cases) {
			Room room = RoomParser.parse("{\"schema\":1,\"active\":true,\"max_players\":" + c[0] + "}");
			assertEquals(c[1], RoomPlan.maxPlayers(8, room), "max_players " + c[0]);
		}
		assertEquals(50, RoomParser.MAX_PLAYERS_CAP);
		// Комната закрыта — лимит игры как был.
		assertEquals(8, RoomPlan.maxPlayers(8, RoomParser.parse("{\"schema\":1,\"active\":false,\"max_players\":40}")));
	}

	@Test
	void accountCheckFlag() throws Exception {
		assertTrue(RoomParser.parse(fixture("basic.json")).accountCheck());
		Room styled = RoomParser.parse(fixture("styled.json"));
		assertFalse(styled.accountCheck());
		assertTrue(styled.active());
		// Оформление выключено, а вход без проверки — включён.
		Room plain = RoomParser.parse(fixture("plain_open.json"));
		assertFalse(plain.active());
		assertFalse(plain.accountCheck());
		assertTrue(RoomPlan.teams(plain).isEmpty());
		assertEquals(8, RoomPlan.maxPlayers(8, plain));
		// Нет поля или не логическое значение — проверка как в игре.
		assertTrue(RoomParser.parse("{\"schema\":1,\"active\":false}").accountCheck());
		assertTrue(RoomParser.parse("{\"schema\":1,\"active\":true,\"account_check\":\"no\"}").accountCheck());
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
		assertEquals(new RoomWatcher.Changed(Room.CLOSED), watcher.poll().orElseThrow(), "нет файла — комнаты нет");
		assertTrue(watcher.poll().isEmpty());

		Files.writeString(file, fixture("basic.json"));
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000));
		RoomWatcher.Changed changed = (RoomWatcher.Changed) watcher.poll().orElseThrow();
		assertEquals("K7F-2QX", changed.room().code());
		assertTrue(watcher.poll().isEmpty(), "время не менялось");

		Files.writeString(file, "{\"schema\": 9}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(2_000_000));
		assertTrue(watcher.poll().orElseThrow() instanceof RoomWatcher.Unsupported);

		Files.delete(file);
		assertEquals(new RoomWatcher.Changed(Room.CLOSED), watcher.poll().orElseThrow());
	}
}
