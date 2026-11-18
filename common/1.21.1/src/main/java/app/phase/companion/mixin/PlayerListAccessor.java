package app.phase.companion.mixin;

import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Before 26.x the limit is the {@code PlayerList.maxPlayers} field, read by both the login check and the ping reply. */
@Mixin(PlayerList.class)
public interface PlayerListAccessor {
	@Accessor("maxPlayers")
	int phase$maxPlayers();

	@Mutable
	@Accessor("maxPlayers")
	void phase$setMaxPlayers(int limit);
}
