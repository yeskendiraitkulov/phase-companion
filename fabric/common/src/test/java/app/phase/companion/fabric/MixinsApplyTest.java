package app.phase.companion.fabric;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.phase.companion.game.PhaseServer;
import app.phase.companion.game.PlayerLimit;
import java.util.Arrays;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.junit.jupiter.api.Test;

/**
 * Миксины встают на классы этой версии игры: загрузчик Fabric применяет их при загрузке
 * класса, а {@code defaultRequire: 1} роняет загрузку, если точка входа не нашлась.
 */
class MixinsApplyTest {
	@Test
	void mixinsApplyToServerClasses() {
		assertTrue(PhaseServer.class.isAssignableFrom(MinecraftServer.class), "MinecraftServerMixin");
		assertTrue(injected(MinecraftServer.class, "phase$tick"), "конец тика");
		assertTrue(injected(MinecraftServer.class, "phase$stop"), "остановка");
		assertTrue(PlayerLimit.class.isAssignableFrom(IntegratedServer.class), "IntegratedServerMixin");
		assertTrue(injected(PlayerList.class, "phase$nameTaken"), "проверка ника при входе");
	}

	/** Обработчик миксина попал в класс игры (Mixin добавляет к имени свой префикс). */
	private static boolean injected(Class<?> target, String handler) {
		return Arrays.stream(target.getDeclaredMethods()).anyMatch(m -> m.getName().endsWith(handler));
	}
}
