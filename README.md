<div align="center">
  <img src="src/main/resources/assets/mtrmap/icon.png" alt="MTR Map Overlay logo" width="128" height="128">

  <h1>MTR Map Overlay</h1>

  <p>Minecraft Transit Railway routes, rails and stations on your map.</p>

  <p><a href="README.md">English</a> · <a href="README.zh-CN.md">简体中文</a> · <a href="https://github.com/teamCreating/MTR-Xareo-Mapper/releases/tag/v1.4.6">Download v1.4.6</a></p>
</div>

MTR Map Overlay is a Minecraft 1.21.1 add-on for **NeoForge or Fabric**. It reads [Minecraft Transit Railway (MTR)](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) data and integrates with Xaero's World Map and JourneyMap. It does not depend on MTR Surveyor's map.

## Features

| Map | Overlay |
| --- | --- |
| Xaero's World Map | Physical rail geometry, route-coloured ribbons, and compact station, platform and depot icons. Hover to inspect routes and landmarks. |
| JourneyMap | Station, platform and depot markers on the fullscreen map. |

These are **map-only icons**, not ordinary Xaero waypoints: they do not fill the waypoint list, compass, minimap or in-world HUD. Multiple routes on one physical rail occupy adjacent colour bands rather than overwriting one another. The station and platform icons follow the same map transform as the rails while panning and zooming.

When the mod is installed on the server as well as the client, it can request a **whole-network snapshot** for each dimension. Without the server component it still works, but can show only the nearby data MTR has sent to the client. Xaero and JourneyMap are optional integrations; install either or both.

## Requirements and installation

| Component | Requirement |
| --- | --- |
| Minecraft | 1.21.1, Java 21 |
| Mod loader | NeoForge 21.1.x **or** Fabric Loader with Fabric API |
| MTR | 4.1.0-beta.2, built for the same loader |
| Map mod | Xaero's World Map 1.45.0+ and/or JourneyMap 6.0.8+ for the respective loader |
| Xaero's Minimap | Optional; used only to remove old `[MTR]` waypoints created by earlier releases |

1. Download the **NeoForge** or **Fabric** JAR from [Releases](https://github.com/teamCreating/MTR-Xareo-Mapper/releases/tag/v1.4.6). Install **one**, not both, in the client's `mods` directory.
2. Install MTR and your chosen map mod for that same loader. Fabric additionally needs Fabric API.
3. Optionally install the matching MTR Map Overlay JAR and MTR on the server to enable the whole-network view. Client and server must use the `mtrmap` mod ID; older `mtrsurveyor` builds are not compatible with this release.
4. Open Xaero's World Map or JourneyMap's fullscreen map. On Xaero, use the `ROUTES` and `TRACKS` buttons at the top left to toggle those layers. Hover over a line or icon for details.

The server component is not required for client-only use. Fabric gameplay, including Xaero's render hook and cross-machine networking, has not yet been manually verified for v1.4.6; the build and static JAR checks passed. See [release notes](RELEASE_NOTES.md).

## Commands and configuration

Commands are registered on the **client**, so they are available even when the server does not run this mod.

| Command | Purpose |
| --- | --- |
| `/mtrmap syncRoutes` | Request a whole-network snapshot, if the server supports it. |
| `/mtrmap syncLandmarks` | Refresh JourneyMap landmarks. |
| `/mtrmap testMarker` | Place a JourneyMap diagnostic marker at the player. |
| `/mtrmap mode station\|platform\|both` | Select the client-data fallback landmark mode. |
| `/mtrmap config enabled <true\|false>` | Enable or disable the map overlay. |
| `/mtrmap config showStations <true\|false>` | Show or hide station icons. |
| `/mtrmap config showPlatforms <true\|false>` | Show or hide platform icons. |
| `/mtrmap config showDepots <true\|false>` | Show or hide depot icons. |
| `/mtrmap config routeLines <true\|false>` | Show or hide route ribbons on Xaero. |
| `/mtrmap config trackLines <true\|false>` | Show or hide physical rails on Xaero. |

NeoForge stores settings in `config/mtrmap.toml` and copies an existing `mtrsurveyor.toml` on first launch when the new file is absent. Fabric uses `config/mtrmap.properties` with its own defaults. The two formats are not automatically interchangeable. Important options include `networkSync.enabled` (default `true`), `networkSync.refreshIntervalSeconds` (default `300`), and the station/platform/depot visibility switches.

## Build from source

Use a Java 21 toolchain. The loader builds have separate Gradle wrappers because they use different build plugins:

| Loader | Windows | macOS / Linux | Output |
| --- | --- | --- | --- |
| NeoForge | `.\gradlew.bat build` | `./gradlew build` | `build/libs/CRTools-MTR-Map-Overlay-1.4.6.jar` |
| Fabric | `.\fabric\gradlew.bat -p fabric build` | `./fabric/gradlew -p fabric build` | `fabric/build/libs/CRTools-MTR-Map-Overlay-fabric-1.4.6.jar` |

The NeoForge build runs the shared JUnit tests. A successful build does not replace an in-game compatibility check, especially when Xaero's internal map renderer changes.

## Source guide

The NeoForge sources are under [`src/main/java/com/lx862/mtrmap`](src/main/java/com/lx862/mtrmap); [`fabric/`](fabric) contains Fabric-specific entry points and adapters and compiles the shared Java sources. The two builds share textures and the same `mtrmap` identity.

| Area | Main responsibility |
| --- | --- |
| [`mapdata/`](src/main/java/com/lx862/mtrmap/mapdata) | `MapDataCache` selects server snapshots or nearby MTR client data. `TrackSampler` samples physical rails; `TrackRoutePalette` assigns stable colour bands to shared rails. |
| [`network/`](src/main/java/com/lx862/mtrmap/network) | Protocol-v5 payloads and `NetworkSnapshotCodec` transfer routes, tracks and landmarks. `ServerNetworkCollector` reads MTR simulators on their own threads; `ClientNetworkSync` probes, requests and reassembles snapshots. |
| [`integration/xaero/`](src/main/java/com/lx862/mtrmap/integration/xaero) | `XaeroRouteRenderer` draws tracks, route ribbons and map-only icons in world-map coordinates and handles hover tooltips. |
| [`integration/journeymap/`](src/main/java/com/lx862/mtrmap/integration/journeymap) | Optional JourneyMap v2 plugin and fullscreen `MarkerOverlay` lifecycle. |
| [`mixin/`](src/main/java/com/lx862/mtrmap/mixin) | Access to MTR data and the Xaero render hook; Fabric supplies its own Xaero hook variant. |
| [`config/`](src/main/java/com/lx862/mtrmap/config) and [`fabric/src/main/java/`](fabric/src/main/java) | Loader-specific configuration, initialization, client commands and network registration. |

Data flow: MTR simulator/client data → dimension-specific `MapDataCache` → Xaero renderer or JourneyMap markers. With a modded server, the client first probes dimension hashes, requests changed snapshots, reassembles chunked payloads and updates the cache. Without one, the cache falls back to MTR's radius-limited client data.

## Troubleshooting

- **Only nearby stations appear:** the server has not supplied a whole-network snapshot. Install the matching mod on the server, or use the client-only fallback as intended.
- **No Xaero lines:** check that Xaero's **World Map** is installed and that the log contains `Path layer render hook into Xaero's World Map is active`. Xaero internal changes can break the render hook.
- **No minimap waypoints:** expected. Landmarks are intentionally fullscreen-map overlays.
- **Migrating from an older build:** replace the old `mtrsurveyor` JAR rather than installing it beside this one; the mod ID and command are now `mtrmap` and `/mtrmap`.

## License and attribution

The project is MIT-licensed. The original copyright and license notice for AmberFrost's contributions remains in [`LICENSE`](LICENSE); later work is maintained by BenLi06. The existing Git commit history and attribution are preserved.
