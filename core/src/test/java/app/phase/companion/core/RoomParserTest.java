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
	void freeRoom() throws Exception {
		Room room = RoomParser.parse(fixture("free.json"));
		assertTrue(room.active());
		assertEquals("PHASE", room.title());
		assertEquals("K7F-2QX", room.code());
		assertEquals(5, room.maxPlayers());
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
		assertEquals(8, RoomPlan.maxPlayers(8, room), "Free не опускает ванильный лимит");
		assertEquals(" • K7F-2QX • ", RoomPlan.headerMiddle(room));
	}

	@Test
	void proRoom() throws Exception {
		Room room = RoomParser.parse(fixture("pro.json"));
		assertEquals("Остров Саита", room.title());
		assertEquals(20, RoomPlan.maxPlayers(8, room));
		List<RoomPlan.TeamSpec> teams = RoomPlan.teams(room);
		assertEquals(List.of("ph.a00", "ph.b01", "ph.c02"), teams.stream().map(RoomPlan.TeamSpec::team).toList());
		assertEquals(List.of("Sait", "Kira", "Den4ik"), teams.stream().map(RoomPlan.TeamSpec::player).toList());
		RoomPlayer kira = teams.get(1).style();
		assertEquals("gold", kira.nameColor());
		assertEquals(0xFBBF24, kira.prefixColor());
		assertEquals("[Строй] ", teams.get(2).style().prefixText());
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
		assertEquals(20, RoomPlan.maxPlayers(8, room), "не больше 20");
		assertEquals(List.of("Bob", "Amy", "Zed"), room.players().stream().map(RoomPlayer::name).toList());
		RoomPlayer zed = room.players().get(2);
		assertNull(zed.label());
		assertNull(zed.nameColor());
		assertEquals(RoomParser.DEFAULT_ACCENT, zed.prefixColor());
		assertEquals(Rank.GUEST, room.players().get(1).rank(), "неизвестная роль — гость");
		assertNull(room.players().get(1).label(), "§ не проходит");
		assertEquals("OK", room.players().get(0).label());
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

		Files.writeString(file, fixture("free.json"));
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
