package app.phase.companion.game;

/** Что мод добавляет любому серверу ({@code MinecraftServerMixin}). */
public interface PhaseServer {
	/**
	 * Мод пустил игроков без проверки аккаунта: вход под ником, который уже в мире,
	 * отклоняется (игра без проверки иначе выбросила бы того, кто уже играет, — и хоста
	 * тоже). Привязку ника к устройству делает сам Phase в туннеле.
	 */
	boolean phase$guardNames();

	void phase$setGuardNames(boolean guard);
}
