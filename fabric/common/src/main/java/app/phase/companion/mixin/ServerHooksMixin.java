package app.phase.companion.mixin;

import app.phase.companion.fabric.FabricPlatform;
import app.phase.companion.game.Companion;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric only: without Fabric API there are no server events, so there are two entry points: the end of
 * every server tick and its shutdown. Identical from 1.21.1 through 26.x. On Forge and NeoForge
 * events do the same ({@code ServerLifecycle}).
 */
@Mixin(MinecraftServer.class)
public abstract class ServerHooksMixin {
	@Unique
	private Companion phase$companion;
	@Unique
	private boolean phase$started;

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void phase$tick(BooleanSupplier haveTime, CallbackInfo ci) {
		if (!phase$started) {
			phase$started = true;
			phase$companion = Companion.create((MinecraftServer) (Object) this, FabricPlatform.get());
		}
		if (phase$companion != null) {
			phase$companion.tick();
		}
	}

	@Inject(method = "stopServer", at = @At("HEAD"))
	private void phase$stop(CallbackInfo ci) {
		if (phase$companion != null) {
			phase$companion.stop();
			phase$companion = null;
		}
	}
}
