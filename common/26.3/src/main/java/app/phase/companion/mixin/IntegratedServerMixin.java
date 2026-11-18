package app.phase.companion.mixin;

import app.phase.companion.game.PlayerLimit;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Лимит мира «Открыть для сети»: в 26.x игра отвечает 8 прямо из
 * {@code IntegratedServer.getMaxPlayers()}, поля лимита в {@code PlayerList} больше нет —
 * мод подменяет ответ числом из комнаты (до 50).
 */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin implements PlayerLimit {
	@Unique
	private int phase$limit = PlayerLimit.VANILLA;

	@Override
	public void phase$setPlayerLimit(int limit) {
		phase$limit = limit;
	}

	@Inject(method = "getMaxPlayers", at = @At("HEAD"), cancellable = true)
	private void phase$maxPlayers(CallbackInfoReturnable<Integer> cir) {
		if (phase$limit != PlayerLimit.VANILLA) {
			cir.setReturnValue(phase$limit);
		}
	}
}
