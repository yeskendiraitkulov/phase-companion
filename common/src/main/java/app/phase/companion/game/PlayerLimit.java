package app.phase.companion.game;

/**
 * Лимит мира «Открыть для сети» ({@code IntegratedServerMixin}): игра держит 8, мод ставит
 * число из комнаты. Где игра хранит лимит, зависит от версии — это знает миксин версии.
 */
public interface PlayerLimit {
	/** Лимит игры без изменений. */
	int VANILLA = -1;

	void phase$setPlayerLimit(int limit);
}
