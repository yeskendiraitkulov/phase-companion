package app.phase.companion.mixin;

import app.phase.companion.game.PhaseServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Mod state on the server: whether it lets players in without account verification ({@link PhaseServer}).
 * Read by {@code PlayerListMixin} when a player joins. Shared by all loaders; on Fabric the tick and
 * stop are caught by {@code ServerHooksMixin}, on Forge and NeoForge by events.
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements PhaseServer {
	@Unique
	private boolean phase$guard;

	@Override
	public boolean phase$guardNames() {
		return phase$guard;
	}

	@Override
	public void phase$setGuardNames(boolean guard) {
		phase$guard = guard;
	}
}
