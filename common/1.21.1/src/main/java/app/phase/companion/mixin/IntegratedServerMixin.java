package app.phase.companion.mixin;

import app.phase.companion.game.PlayerLimit;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Player cap of an "Open to LAN" world: in 1.21.1 the integrated server creates its player list
 * with a limit of 8 in {@code PlayerList.maxPlayers}; the mod overwrites that field with the number
 * from the room (up to 50) and restores the original value afterwards.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin implements PlayerLimit {
	/** Vanilla limit, saved before the first override. */
	@Unique
	private int phase$vanilla = PlayerLimit.VANILLA;

	@Override
	public void phase$setPlayerLimit(int limit) {
		PlayerListAccessor list = (PlayerListAccessor) ((IntegratedServer) (Object) this).getPlayerList();
		if (phase$vanilla == PlayerLimit.VANILLA) {
			phase$vanilla = list.phase$maxPlayers();
		}
		list.phase$setMaxPlayers(limit == PlayerLimit.VANILLA ? phase$vanilla : limit);
	}
}
