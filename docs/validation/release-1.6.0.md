# 1.6.0 final candidate acceptance

**Publication authorization — 2026-11-11T13:38:47+08:00:** The user explicitly instructed “直接发布，不要搞了”, superseding the previous repair-before-release condition. Publish the already sealed Candidate42953810741 with the Forge47.4.0 quick-play limitation disclosed. The experimental EventLoop patch and diagnostic branches are excluded from this release.
Tester: Codex. Recorded 2026-11-11T12:11:04+08:00 (UTC+08:00). **All six targets passed the normal-entry map/runtime matrix. The user explicitly requires the remaining Forge47 quick-play multiplayer handshake defect fixed before publication; no limitation exception is approved.**

## Candidate identity

- [Candidate42953810741, attempt1](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/42953810741): six builds/tests/package checks passed.
- Main: `0e65abde81938f6d6a854a1a3b9c9eeea8cd3034`; compatibility: `5e758c8feec7124c7109e511f18492a1f8e3a925`.
- [Sealed manifest](evidence/release-1.6.0-final/release-manifest.json), SHA-256 `647c58fe0b072a29ab8608a46ad737d9306fad4d6384c8d9c7ce7258e2091414`; [six JAR checksums](evidence/release-1.6.0-final/SHA256SUMS).
- [Artifact audit](evidence/release-1.6.0-final/artifact-audit.json): both corrected classes match independently native-tested production classes; startup guard is present only in Forge1.20.4; QA classes are absent from all production JARs.

The original [Candidate42879095868](release-1.6.0-initial-candidate.md) lacked the startup repair. The later [Candidate42946243079](release-1.6.0-fixed-candidate.md) exposed the Forge1.20.1 absent-channel bug. Neither is approved for publication. This report covers the new, actually downloaded binaries.

## Production runtime matrix

| Target | Singleplayer maps × locales | Dedicated full sync | MTR-only server fallback |
|---|---|---|---|
| 1.21.1-neoforge | 8/8 | 2/2 maps | 2/2 maps |
| 1.21.1-fabric | 8/8 | 2/2 maps | 2/2 maps |
| 1.20.1-forge | 8/8 | 2/2 maps | 2/2 maps |
| 1.20.1-fabric | 8/8 | 2/2 maps | 2/2 maps |
| 1.20.4-forge | 8/8 | 2/2 maps | 2/2 maps |
| 1.20.4-fabric | 8/8 | 2/2 maps | 2/2 maps |

Both Xaero and JourneyMap pass with actual copied MTR fixtures. Singleplayer runs cover en_us, zh_cn, zh_tw and ja_jp: native station selection, localized controls, unavailable Navigate behavior, actual predicted arrivals, advancing server timestamps and unpaused map operation. Forge1.20.4 uses quick-play into its save; the other targets use native title/world menus. Every client verifies the JAR SHA-256 and the actual protection-domain archive path. CLI controls the real KeyboardHandler/MouseHandler; captures are raw framebuffers. The separate opt-in QA mod is never a release asset and never runs on dedicated servers.

NeoForge includes actual HowToGo0.2.1 and MTR Navigation: the two-provider chooser and public HowToGo destination name/coordinates/source, with map closed, pass in both maps. This verifies destination handoff, not road-route search/guidance. Five other profiles have no provider and correctly ignore disabled Navigate clicks. Player-defined station/route names remain native.

All six matching-loader dedicated servers run original native loaders, MTR and real copied saves; Fabric also includes its matching Fabric API. Local offline fixtures bind only to127.0.0.1. With no server Overlay, real nearby MTR routes/tracks/stations/car geometry remain available, native caches identify nearby data, Navigate stays disabled, and arrival boards explicitly say “Server has no station-arrivals service” with no invented rows. Installing the matching sealed Overlay server JAR supplies full static/global train data and nonempty native arrivals with advancing timestamps. Collapse stops the board. Both maps' full-server native train tooltips were visually reviewed: [12 raw-capture reviews](evidence/release-1.6.0-final/hover-review.json).

## Fixes and startup boundary

The [Forge1.20.4 startup repair](forge1204-startup-fix.md) preserves vanilla completion state and the real Forge warning/error paths. Independent verification covered Forge49.2.0/49.2.13, quick-play/menu/acknowledged-warning entry, 48 map/locale cases and four diagnostic guards. There is no forced bus start, synthetic lifecycle event, private Xaero initialization, exception suppression or patched dependency. Its patch class is identical in this candidate.

The [Forge1.20.1 optional-channel correction](forge1201-optional-channel-fix.md) uses Forge47.4.0's native acceptMissingOr helper instead of comparing ChannelData to String. All three channel predicates accept missing/vanilla peers and retain exact installed-protocol matching. Native full/fallback cases pass on this candidate.

**Forge47.4.0 quick-play multiplayer is not claimed fixed.** It times out during the base Forge handshake in a plain four-mod MTR/JourneyMap/Xaero control without Overlay or QA as well as with Overlay. Its [control evidence](evidence/forge1201-optional-channel-fix/control-without-overlay/result.json) remains explicit. Final Forge1.20.1 dedicated acceptance uses the ordinary native multiplayer list, which passes. This is separate from the repaired Forge1.20.4 singleplayer startup path.

## Evidence and scope

[Publish integrity dry-run42956402233](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/42956402233) passed. It did not approve an exception, create tags or publish a release.

Per-target results, dependency inventories, native client/server logs and raw board/hover captures are under [final evidence](evidence/release-1.6.0-final/acceptance-summary.json). Local raw archive: `MTR-Map-Overlay-1.6.0-final-runtime-evidence.zip`, 11365996 bytes, SHA-256 `85286f74c07e4e2cc99ae31a26a9696a8954ff0b1e104cf96e21aca9c20bfd65`. [Archive identity](evidence/release-1.6.0-final/raw-archive-identity.json). Earlier failed launcher/observer attempts are retained and not counted as passes. The final Fabric1.20.1 full-server first hover attempt over-zoomed native markers; the bounded zoom correction was applied to the CLI runner, the failed attempt was preserved, and the same unchanged candidate passed the fresh rerun. Production candidate files were never modified.

Main suite:107 pass/one explicit world-generator skip; compatibility suite:110 pass/one skip per target. The release makes no 1000/5000-train FPS or complex-network performance claim; that work remains separately scoped.
