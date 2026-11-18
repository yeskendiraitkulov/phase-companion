package app.phase.companion.forge;

import app.phase.companion.game.Platform;
import app.phase.companion.game.ServerLifecycle;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge entry point. Server logic only: no network channels, blocks or items;
 * guests do not need the mod ({@code displayTest} in {@code mods.toml}).
 *
 * <p>Tick and shutdown come from Forge events: {@code ServerTickEvent.Post} at the end of
 * {@code tickServer}, {@code ServerStoppingEvent} before {@code stopServer} (that is where
 * Forge 52 places them in {@code MinecraftServer}). {@code ServerStoppedEvent} covers the case
 * where the server crashed and "stopping" never arrived.
 */
@Mod("phase_companion")
public final class PhaseCompanionForge {
	public PhaseCompanionForge(FMLJavaModLoadingContext context) {
		String version = context.getContainer().getModInfo().getVersion().toString();
		ServerLifecycle life = new ServerLifecycle(new Platform("forge", version));
		var bus = MinecraftForge.EVENT_BUS;
		bus.addListener(EventPriority.NORMAL, false, TickEvent.ServerTickEvent.Post.class,
				event -> life.tick(event.getServer()));
		bus.addListener(EventPriority.NORMAL, false, ServerStoppingEvent.class,
				event -> life.stop(event.getServer()));
		bus.addListener(EventPriority.NORMAL, false, ServerStoppedEvent.class,
				event -> life.stop(event.getServer()));
	}
}
