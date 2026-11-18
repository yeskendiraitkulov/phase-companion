package app.phase.companion.game;

import java.util.Optional;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.TeamColor;

/** Цвет ника команды. С 26.x это отдельное перечисление {@code TeamColor}, а не {@code ChatFormatting}. */
final class TeamColors {
	private TeamColors() {}

	/** {@code name} — имя цвета из {@code room.json} ({@code blue}) или {@code null}: без цвета. */
	static void apply(PlayerTeam team, String name) {
		team.setColor(Optional.ofNullable(name).map(TeamColor::byName));
	}
}
