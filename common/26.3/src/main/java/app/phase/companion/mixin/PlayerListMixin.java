package app.phase.companion.mixin;

import app.phase.companion.game.PhaseServer;
import java.net.SocketAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Without account verification the game trusts the nickname and kicks whoever is already
 * in the world on a match. While the mod lets players in this way, a second login under the same name
 * is rejected with the vanilla "name taken" reason.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
	@Inject(method = "canPlayerLogin", at = @At("HEAD"), cancellable = true)
	private void phase$nameTaken(SocketAddress address, NameAndId player, CallbackInfoReturnable<Component> cir) {
		PlayerList list = (PlayerList) (Object) this;
		if (((PhaseServer) list.getServer()).phase$guardNames() && list.getPlayerByName(player.name()) != null) {
			cir.setReturnValue(Component.translatable("multiplayer.disconnect.name_taken"));
		}
	}
}
