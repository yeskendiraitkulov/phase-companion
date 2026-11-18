package app.phase.companion.mixin;

import app.phase.companion.game.PlayerLimit;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Лимит мира «Открыть для сети»: в 1.21.1 встроенный сервер создаёт список игроков с
 * лимитом 8 в поле {@code PlayerList.maxPlayers}, мод переписывает поле числом из комнаты
 * (до 50) и возвращает прежнее значение.
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin implements PlayerLimit {
	/** Лимит игры, запомненный до первой замены. */
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
