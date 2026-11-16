package app.phase.companion.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Optional;

/**
 * Следит за {@code room.json}: раз в секунду смотрит время изменения и перечитывает файл,
 * только когда оно сменилось. Файл маленький (меньше 4 КБ), поэтому читается на потоке
 * сервера; Phase пишет его атомарно (временный файл и переименование).
 */
public final class RoomWatcher {

	/** Состояние после проверки. */
	public sealed interface Result permits Changed, Unsupported {
	}

	/** Новая комната (или комнаты больше нет). */
	public record Changed(Room room) implements Result {
	}

	/** Файл есть, но в неизвестном формате: мод должен отойти в сторону. */
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

	/** Пусто — ничего не изменилось с прошлой проверки. */
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
			// Phase как раз переименовывает файл: попробуем через секунду.
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
