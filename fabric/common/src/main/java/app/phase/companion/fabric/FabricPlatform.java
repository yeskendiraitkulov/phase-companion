package app.phase.companion.fabric;

import app.phase.companion.game.Platform;
import net.fabricmc.loader.api.FabricLoader;

/** Загрузчик Fabric для {@code Companion}. */
public final class FabricPlatform {
	private FabricPlatform() {}

	/** Версия мода из fabric.mod.json: манифест jar под загрузчиком Fabric не читается. */
	public static Platform get() {
		String version = FabricLoader.getInstance()
				.getModContainer("phase_companion")
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
		return new Platform("fabric", version);
	}
}
