package app.phase.companion.game;

import java.util.Optional;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

/** Team name color. Since 26.x this is a separate {@code TeamColor} enum instead of {@code ChatFormatting}. */
final class TeamColors {
	private TeamColors() {}

	/** {@code name} is the color name from {@code room.json} ({@code blue}) or {@code null} for no color. */
	static void apply(PlayerTeam team, String name) {
		team.setColor(Optional.ofNullable(name).map(TeamColor::byName));
	}
}
