package app.phase.companion.game;

/** What the mod adds to any server ({@code MinecraftServerMixin}). */
public interface PhaseServer {
	/**
	 * The mod let players in without account verification: a login under a name that is already
	 * in the world is rejected (without verification the game would otherwise kick the player who is
	 * already playing, the host included). Binding a name to a device is done by Phase in the tunnel.
	 */
	boolean phase$guardNames();

	void phase$setGuardNames(boolean guard);
}
