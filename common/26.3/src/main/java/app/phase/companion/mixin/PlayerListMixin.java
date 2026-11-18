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
 * Без проверки аккаунта игра верит нику и при совпадении выбрасывает того, кто уже в
 * мире. Пока мод так пускает игроков, второй вход под тем же ником отклоняется —
 * ванильной причиной «ник занят».
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
