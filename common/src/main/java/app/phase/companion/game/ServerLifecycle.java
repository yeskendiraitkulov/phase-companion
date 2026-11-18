package app.phase.companion.game;

import net.minecraft.server.MinecraftServer;

/**
 * Жизнь {@link Companion} для загрузчиков с событиями сервера (Forge, NeoForge): создаётся
 * на первом тике сервера, останавливается при его остановке. Так же, как миксин тика у
 * Fabric: события Forge и NeoForge стоят в тех же местах игры — конец {@code tickServer} и
 * начало {@code stopServer}.
 *
 * <p>Встроенный сервер в игре один за раз; новый мир — новый сервер и новый {@link Companion}.
 */
public final class ServerLifecycle {
	private final Platform platform;
	private MinecraftServer server;
	private Companion companion;

	public ServerLifecycle(Platform platform) {
		this.platform = platform;
	}

	/** Конец тика сервера. */
	public void tick(MinecraftServer current) {
		if (current != server) {
			server = current;
			companion = Companion.create(current, platform);
		}
		if (companion != null) {
			companion.tick();
		}
	}

	/** Начало остановки сервера. */
	public void stop(MinecraftServer stopping) {
		if (stopping != server) {
			return;
		}
		if (companion != null) {
			companion.stop();
		}
		companion = null;
		server = null;
	}
}
