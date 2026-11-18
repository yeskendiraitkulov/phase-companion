package app.phase.companion.mixin;

import app.phase.companion.game.PhaseServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Состояние мода на сервере: пускает ли он без проверки аккаунта ({@link PhaseServer}).
 * Его читает {@code PlayerListMixin} при входе игрока. Общий для всех загрузчиков; тик и
 * остановку у Fabric ловит {@code ServerHooksMixin}, у Forge и NeoForge — события.
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
