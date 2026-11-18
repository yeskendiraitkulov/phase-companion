package app.phase.companion.mixin;

import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** До 26.x лимит — поле {@code PlayerList.maxPlayers}: его читает и проверка входа, и ответ на пинг. */
@Mixin(PlayerList.class)
public interface PlayerListAccessor {
	@Accessor("maxPlayers")
	int phase$maxPlayers();

	@Mutable
	@Accessor("maxPlayers")
	void phase$setMaxPlayers(int limit);
}
