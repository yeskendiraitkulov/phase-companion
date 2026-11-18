package app.phase.companion.neoforge;

import app.phase.companion.game.Platform;
import app.phase.companion.game.ServerLifecycle;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NeoForge entry point. Server logic only: the mod registers no network packets,
 * blocks or items, so there is nothing for a guest (or a vanilla client) to negotiate.
 *
 * <p>Tick and shutdown come from NeoForge events: {@code ServerTickEvent.Post} at the end of
 * {@code tickServer}, {@code ServerStoppingEvent} before {@code stopServer}.
 * {@code ServerStoppedEvent} covers the case where the server crashed and "stopping" never arrived.
 */
@Mod("phase_companion")
public final class PhaseCompanionNeoForge {
	public PhaseCompanionNeoForge(IEventBus modBus, ModContainer container) {
		String version = container.getModInfo().getVersion().toString();
		ServerLifecycle life = new ServerLifecycle(new Platform("neoforge", version));
		NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, event -> life.tick(event.getServer()));
		NeoForge.EVENT_BUS.addListener(ServerStoppingEvent.class, event -> life.stop(event.getServer()));
		NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, event -> life.stop(event.getServer()));
	}
}
