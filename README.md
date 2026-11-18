# Phase Companion

A free mod for the host of a world opened through the Phase app. It styles the world that is open to LAN. Install it only on the host's game; guests don't need it.

- a header and footer in the player list (Tab), plus a bar shown for 10 seconds when someone joins;
- nickname colors and tags such as `[HOST]`, and player glow;
- more than 8 slots in an "Open to LAN" world, up to 50;
- joining without account verification when the host enables it; a second login under a name that is already taken is rejected.

Settings come from the Phase window: the mod reads `phase/room.json` in the world folder. The mod makes no network requests, collects no data, and does nothing without Phase.

## Versions

| Loader | Minecraft | Tested in game |
|---|---|---|
| Fabric | 26.3 | yes |
| Forge | 1.21.1 | yes |
| Fabric | 1.21.1 | not yet |
| NeoForge | 1.21.1 | not yet |

Phase installs the right file for you. To install manually, download the jar from the [releases](https://github.com/yeskendiraitkulov/phase-companion/releases/latest) and drop it into the `mods` folder.

On Forge and NeoForge a player without mods can join only if none of the host's other mods requires itself on the client.

## Building

```bash
./gradlew build
```

Requires Java 25 or newer; Gradle downloads the Java versions the game needs. The jars end up in `<loader>/<version>/build/libs`.

## License

[MPL-2.0](LICENSE).

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
