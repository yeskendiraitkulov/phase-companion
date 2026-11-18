package app.phase.companion.game;

import net.minecraft.server.MinecraftServer;

/**
 * Lifetime of {@link Companion} for loaders with server events (Forge, NeoForge): created
 * on the first server tick, stopped when the server stops. Same as the tick mixin on
 * Fabric: Forge and NeoForge events sit at the same places in the game, the end of {@code tickServer} and
 * the start of {@code stopServer}.
 *
 * <p>Only one integrated server exists at a time; a new world means a new server and a new {@link Companion}.
 */
public final class ServerLifecycle {
	private final Platform platform;
	private MinecraftServer server;
	private Companion companion;

	public ServerLifecycle(Platform platform) {
		this.platform = platform;
	}

	/** End of a server tick. */
	public void tick(MinecraftServer current) {
		if (current != server) {
			server = current;
			companion = Companion.create(current, platform);
		}
		if (companion != null) {
			companion.tick();
		}
	}

	/** Start of server shutdown. */
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
