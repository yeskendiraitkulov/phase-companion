package app.phase.companion.fabric.mixin;

import app.phase.companion.fabric.PhaseServer;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Лимит мира «Открыть для сети»: игра отвечает 8, мод — числом из комнаты (до 20). */
@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin {
	@Inject(method = "getMaxPlayers", at = @At("HEAD"), cancellable = true)
	private void phase$maxPlayers(CallbackInfoReturnable<Integer> cir) {
		int limit = ((PhaseServer) this).phase$playerLimit();
		if (limit != PhaseServer.VANILLA) {
			cir.setReturnValue(limit);
		}
	}
}
