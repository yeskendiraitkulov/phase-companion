package app.phase.companion.game;

import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;

/** Цвет ника команды. До 26.x это {@code ChatFormatting}; «без цвета» — {@code RESET}, как у новой команды. */
final class TeamColors {
	private TeamColors() {}

	/** {@code name} — имя цвета из {@code room.json} ({@code blue}) или {@code null}: без цвета. */
	static void apply(PlayerTeam team, String name) {
		ChatFormatting color = ChatFormatting.getByName(name);
		team.setColor(color == null ? ChatFormatting.RESET : color);
	}
}
