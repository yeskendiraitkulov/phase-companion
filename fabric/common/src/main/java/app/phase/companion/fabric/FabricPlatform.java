package app.phase.companion.fabric;

import app.phase.companion.game.Platform;
import net.fabricmc.loader.api.FabricLoader;

/** Fabric loader for {@code Companion}. */
public final class FabricPlatform {
	private FabricPlatform() {}

	/** Mod version from fabric.mod.json: the jar manifest is not read under the Fabric loader. */
	public static Platform get() {
		String version = FabricLoader.getInstance()
				.getModContainer("phase_companion")
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
		return new Platform("fabric", version);
	}
}
