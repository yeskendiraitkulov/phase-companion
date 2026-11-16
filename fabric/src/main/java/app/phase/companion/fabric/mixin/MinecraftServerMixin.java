package app.phase.companion.fabric.mixin;

import app.phase.companion.fabric.Companion;
import app.phase.companion.fabric.PhaseServer;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Две точки входа: конец каждого тика сервера и его остановка. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements PhaseServer {
	@Unique
	private Companion phase$companion;
	@Unique
	private int phase$limit = PhaseServer.VANILLA;

	@Override
	public int phase$playerLimit() {
		return phase$limit;
	}

	@Override
	public void phase$setPlayerLimit(int limit) {
		phase$limit = limit;
	}

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
	@Unique
	private boolean phase$started;

	@Inject(method = "tickServer", at = @At("TAIL"))
	private void phase$tick(BooleanSupplier haveTime, CallbackInfo ci) {
		if (!phase$started) {
			phase$started = true;
			phase$companion = Companion.create((MinecraftServer) (Object) this);
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
