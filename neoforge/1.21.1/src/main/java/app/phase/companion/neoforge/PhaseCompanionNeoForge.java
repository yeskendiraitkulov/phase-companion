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
 * Точка входа NeoForge. Только серверная логика: мод не регистрирует ни сетевых пакетов,
 * ни блоков, ни предметов — гостю (и ванильному клиенту) согласовывать нечего.
 *
 * <p>Тик и остановку дают события NeoForge: {@code ServerTickEvent.Post} — в конце
 * {@code tickServer}, {@code ServerStoppingEvent} — перед {@code stopServer}.
 * {@code ServerStoppedEvent} — на случай, если сервер упал и «остановка» не пришла.
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
