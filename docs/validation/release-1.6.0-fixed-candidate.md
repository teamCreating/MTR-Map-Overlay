# Repaired 1.6.0 candidate acceptance — held

Tester: Codex, 2026-11-11T11:10:36+08:00 (UTC+08:00). Candidate42946243079 is held: Forge1.20.1 rejects MTR-only servers because its optional-channel predicate compares ChannelData to String. A source fix now uses the native acceptMissingOr helper; a new sealed candidate is required before publication.

## Identity

[Candidate 42946243079, attempt 1](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/42946243079) passed six builds/tests/package checks. Main: `ce337357356f8f02b164994f63b47e2bc3e5107d`; compat: `11d8e9e5697effbe3477be84893ede5f4371c6f4`. [Manifest](evidence/release-1.6.0-fixed-candidate/release-manifest.json) SHA-256: `b508207feef70203f38033d4eb402fdb4b643f712443ad739b5dc46ed6bfa033`. [JAR checksums](evidence/release-1.6.0-fixed-candidate/SHA256SUMS).

This supersedes the unrepaired [historical candidate](release-1.6.0-initial-candidate.md). The Forge1.20.4 patch class and refmap match the independently [verified repair](forge1204-startup-fix.md), which passed 48 native UI cases and four diagnostic guards on Forge49.2.0/49.2.13.

## Exact-candidate runtime matrix

| Target | Singleplayer maps × locales | Dedicated sync | Client-only fallback |
|---|---|---|---|
| 1.21.1-neoforge | 8/8 | Pending | Pending |
| 1.21.1-fabric | 8/8 | Pending | Pending |
| 1.20.1-forge | 8/8 | Pending | Pending |
| 1.20.1-fabric | 8/8 | Pending | Pending |
| 1.20.4-forge | 8/8 | Pending | Pending |
| 1.20.4-fabric | 8/8 | Pending | Pending |

All six clients load the downloaded production JAR, verified by SHA-256 and actual protection-domain archive path. Real copied MTR fixtures, native map screens, native KeyboardHandler/MouseHandler input and raw framebuffer captures are used. Forge1.20.4 enters its save through quick-play; other targets use native world menus. No dependency JAR is patched. Both Xaero and JourneyMap pass in en_us, zh_cn, zh_tw and ja_jp: native station selection, translation, provider availability/dimming, arrivals, advancing server timestamps and unpaused maps. LIMITED predictions retain the native status rather than inventing services.

NeoForge tests actual HowToGo0.2.1 alongside MTR Navigation: two-provider chooser, public destination name/coordinates/source and map close pass on both maps. This verifies destination handoff, not road-route search or guidance. The other five profiles have no provider and correctly ignore disabled Navigate clicks.

The separate opt-in QA JAR is not a release asset. Fresh staging exposed an older QA refmap shadowing the production refmap; rebuilt QA artifacts exclude production Mixin configuration/refmaps, and all six pass tools/verify_qa_artifacts.py. The sealed production candidate was unchanged. A first 1.21.1 Fabric attempt timed out because MTR's native reading countdown ignored early Escape. A second attempt was interrupted by a diagnostic Escape opening the pause menu. Both are retained locally and excluded from pass totals. The final fresh run honors the native notice countdown and receives no diagnostic input.

Dedicated tests are still running. They use original matching-loader server installs, MTR, real copied fixtures and loopback-only offline clients. Each target tests an MTR-only server first, then the matching sealed Overlay server JAR; no QA mod is installed server-side. Native map data and rendered-car caches are observed without fabricating packets or vehicles. Preliminary launcher-argument and projection-observer harness failures are retained, excluded from acceptance totals and corrected without changing production JARs. Completion requires reviewed results and raw hover captures.

Large 1000/5000-train performance work remains separately scoped; this small real MTR network makes no FPS/large-network performance claim.
