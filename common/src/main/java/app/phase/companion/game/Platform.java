package app.phase.companion.game;

/**
 * Что {@link Companion} знает о загрузчике: его имя и версию мода из метаданных этого
 * загрузчика (манифест jar под Fabric, Forge и NeoForge читается по-разному, поэтому версию
 * отдаёт сам загрузчик). Оба значения уходят в {@code companion.ok}.
 *
 * @param loader  {@code fabric}, {@code forge} или {@code neoforge}
 * @param version версия мода, как её видит загрузчик ({@code 0.1.0+forge.1.21.1})
 */
public record Platform(String loader, String version) {}
