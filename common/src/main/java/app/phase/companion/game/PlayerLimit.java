package app.phase.companion.game;

/**
 * Player cap of an "Open to LAN" world ({@code IntegratedServerMixin}): the game holds 8, the mod sets
 * the number from the room. Where the game stores the limit depends on the version; the per-version mixin knows.
 */
public interface PlayerLimit {
	/** Game limit, unchanged. */
	int VANILLA = -1;

	void phase$setPlayerLimit(int limit);
}
