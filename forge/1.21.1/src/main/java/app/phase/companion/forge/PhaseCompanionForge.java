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
 * Точка входа Forge. Только серверная логика: ни сетевых каналов, ни блоков, ни предметов —
 * гостю мод не нужен ({@code displayTest} в {@code mods.toml}).
 *
 * <p>Тик и остановку дают события Forge: {@code ServerTickEvent.Post} — в конце
 * {@code tickServer}, {@code ServerStoppingEvent} — перед {@code stopServer} (так их ставит
 * Forge 52 в {@code MinecraftServer}). {@code ServerStoppedEvent} — на случай, если сервер
 * упал и «остановка» не пришла.
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
