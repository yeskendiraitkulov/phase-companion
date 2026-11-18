package app.phase.companion.game;

/**
 * What {@link Companion} knows about the loader: its name and the mod version from that
 * loader's metadata (the jar manifest is read differently on Fabric, Forge and NeoForge, so the
 * loader supplies the version). Both values go into {@code companion.ok}.
 *
 * @param loader  {@code fabric}, {@code forge} or {@code neoforge}
 * @param version mod version as the loader sees it ({@code 0.1.0+forge.1.21.1})
 */
public record Platform(String loader, String version) {}
