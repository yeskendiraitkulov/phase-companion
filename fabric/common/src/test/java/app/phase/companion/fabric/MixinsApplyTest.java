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
 * The mixins land on classes of this game version: the Fabric loader applies them when the
 * class loads, and {@code defaultRequire: 1} fails the load if an injection point is not found.
 */
class MixinsApplyTest {
	@Test
	void mixinsApplyToServerClasses() {
		assertTrue(PhaseServer.class.isAssignableFrom(MinecraftServer.class), "MinecraftServerMixin");
		assertTrue(injected(MinecraftServer.class, "phase$tick"), "end of tick");
		assertTrue(injected(MinecraftServer.class, "phase$stop"), "shutdown");
		assertTrue(PlayerLimit.class.isAssignableFrom(IntegratedServer.class), "IntegratedServerMixin");
		assertTrue(injected(PlayerList.class, "phase$nameTaken"), "name check on login");
	}

	/** The mixin handler ended up in the game class (Mixin adds its own prefix to the name). */
	private static boolean injected(Class<?> target, String handler) {
		return Arrays.stream(target.getDeclaredMethods()).anyMatch(m -> m.getName().endsWith(handler));
	}
}
