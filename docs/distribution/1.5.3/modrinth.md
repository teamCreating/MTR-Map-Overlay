# MTR Map Overlay

Your Minecraft Transit Railway network, inside Xaero's World Map and JourneyMap: coloured routes, real rails, moving trains and native station arrivals.

Explore [v1.5.3 pre-release](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3), or use [v1.5.2 stable](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.2).

## See your railway network

- Physical rail geometry and route-coloured ribbons follow the MTR network. Shared tracks show parallel colours; light rail is slimmer and high speed uses colour/white blocks.
- Whole-map live trains show moving carriages throughout the viewed dimension, even far from players. Zoom out for compact markers, or hover for available route, destination and consist details.
- Compact station, platform and depot icons stay inside the fullscreen map. They do not fill the ordinary Xaero waypoint list, compass, minimap or in-world HUD.
- Independent TRACKS and ROUTES controls let you explore each layer in either map.

## New in 1.5.3: station arrivals and PIDS

Click a station to pin its classic information card. Choose **Expand** for native MTR/TSC arrivals by destination, line and platform. The board starts collapsed; missing predictions are shown explicitly.

Choose British black/amber PIDS or European/airport styling. `/mtrmap config pidsStyle british|european` changes and saves the theme. Collapsing the board or closing the map releases its arrival subscription.

## Ready for navigation extensions

A public API lets other mods read native network data, subscribe to station boards and control independent map views. Registered providers enable **Navigate** on station cards. A navigation mod supplies planning and progress; Overlay can highlight upcoming sections while separately dimming completed and unused tracks.

No navigation provider is bundled. [Developer API guide](https://github.com/teamCreating/MTR-Map-Overlay/blob/main/docs/api/overlay-api-v1.md).

## Compatibility

| Minecraft | Loader | Java | MTR | Map versions |
| --- | --- | --- | --- | --- |
| 1.21.1 | NeoForge 21.1.x / Fabric + Fabric API | 21 | 4.1.0-beta.2 | Xaero's World Map 1.45.0+ / JourneyMap 6.0.8+ |
| 1.20.1 | Forge 47.x / Fabric + Fabric API | 17+ | 4.0.5 (>=4.0.5, <4.1) | Xaero's World Map 1.45.0+ / JourneyMap 6.0.6+ |
| 1.20.4 | Forge 49.x / Fabric + Fabric API | 17+ | 4.0.5 (>=4.0.5, <4.1) | Xaero's World Map 1.45.0+ / JourneyMap 5.10.0 |

Choose the exact Minecraft and loader combination; the six JAR filenames include the tested MTR version. Fabric needs the matching Fabric API. Install either supported map or both, using the same game version and loader. These are three specific Minecraft versions, not every version between 1.20.1 and 1.21.1.

## Client and server setup

- Install one matching Overlay JAR, MTR and a supported fullscreen map on the client.
- For the full rail network, whole-map live trains and native station arrivals, install matching Overlay and MTR on the server too. The server does not need a map mod.
- Client-only installations use nearby data already sent by MTR. Native station arrivals require matching 1.5.3 server support.
- Use `/mtrmap config trainCars false` to hide the live train layer. Train-hover next-stop distance and ETA are not included; station arrivals are a separate feature.

## Links and support

[Website and documentation](https://mtrmapoverlay.benli06.site/en/) · [Source and issues](https://github.com/teamCreating/MTR-Map-Overlay) · [Release notes](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3)

Support development on [Ko-fi](https://ko-fi.com/Z8C424REOO) or [Afdian](https://afdian.com/a/benli06).

MIT licensed. Original attribution is retained in the project license. Developed independently for Minecraft Transit Railway.
