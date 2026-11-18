package app.phase.companion.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

/**
 * Watches {@code room.json}: once a second checks the modification time and rereads the file
 * only when it changed. The file is small (under 4 KB), so it is read on the server
 * thread; Phase writes it atomically (temp file and rename).
 */
public final class RoomWatcher {

	/** State after a check. */
	public sealed interface Result permits Changed, Unsupported {
	}

	/** New room (or the room is gone). */
	public record Changed(Room room) implements Result {
	}

	/** File exists but in an unknown format: the mod must stand down. */
	public record Unsupported(String reason) implements Result {
	}

	private final Path file;
	private FileTime seen;
	private boolean missingSeen;

	public RoomWatcher(Path file) {
		this.file = file;
	}

	public Path file() {
		return file;
	}

	/** Empty: nothing changed since the last check. */
	public Optional<Result> poll() {
		FileTime modified;
		try {
			modified = Files.getLastModifiedTime(file);
		} catch (NoSuchFileException err) {
			if (missingSeen) {
				return Optional.empty();
			}
			missingSeen = true;
			seen = null;
			return Optional.of(new Changed(Room.CLOSED));
		} catch (IOException err) {
			return Optional.empty();
		}
		if (modified.equals(seen)) {
			return Optional.empty();
		}
		String json;
		try {
			json = Files.readString(file, StandardCharsets.UTF_8);
		} catch (IOException err) {
			// Phase is renaming the file right now: try again in a second.
			return Optional.empty();
		}
		seen = modified;
		missingSeen = false;
		try {
			return Optional.of(new Changed(RoomParser.parse(json)));
		} catch (RoomParser.UnsupportedRoomException err) {
			return Optional.of(new Unsupported(err.getMessage()));
		}
	}
}
