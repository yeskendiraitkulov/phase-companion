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

	/**
	 * Мод пустил игроков без проверки аккаунта: вход под ником, который уже в мире,
	 * отклоняется (игра без проверки иначе выбросила бы того, кто уже играет, — и хоста
	 * тоже). Привязку ника к устройству делает сам Phase в туннеле.
	 */
	boolean phase$guardNames();

	void phase$setGuardNames(boolean guard);
}
