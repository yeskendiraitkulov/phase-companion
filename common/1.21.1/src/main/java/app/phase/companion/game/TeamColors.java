package app.phase.companion.game;

import net.minecraft.ChatFormatting;
import net.minecraft.world.scores.PlayerTeam;

/** Team name color. Before 26.x this is {@code ChatFormatting}; "no color" is {@code RESET}, like a new team. */
final class TeamColors {
	private TeamColors() {}

	/** {@code name} is the color name from {@code room.json} ({@code blue}) or {@code null} for no color. */
	static void apply(PlayerTeam team, String name) {
		ChatFormatting color = ChatFormatting.getByName(name);
		team.setColor(color == null ? ChatFormatting.RESET : color);
	}
}
