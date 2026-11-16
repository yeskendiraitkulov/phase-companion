package app.phase.companion.fabric;

/**
 * Что мод добавляет серверу ({@code MinecraftServerMixin}): свой лимит игроков. Встроенный
 * сервер в 26.x отвечает «8» прямо из {@code IntegratedServer.getMaxPlayers()}, поле лимита
 * в {@code PlayerList} убрано; {@code IntegratedServerMixin} подменяет ответ.
 */
public interface PhaseServer {
	/** Лимит игры без изменений. */
	int VANILLA = -1;

	int phase$playerLimit();

	void phase$setPlayerLimit(int limit);
}
