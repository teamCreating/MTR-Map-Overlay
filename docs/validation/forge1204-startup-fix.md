# Forge 1.20.4 initial-load ordering repair

Recorded by Codex at 2026-11-11T10:21:58+08:00 (UTC+08:00). **The repair passes the final native matrix below. Version remains 1.6.0; the old Candidate 42879095868 does not contain this fix and must not be published as the repaired build.**

## Cause and correction

Forge 49's initial resource callback called vanilla onResourceLoadFinished before ClientModLoader.completeModLoading. Quick-play can synchronously open an integrated server from vanilla game-load initialization, while Forge's event bus is still stopped. Xaero therefore misses its native ServerStarting initialization and disconnects at player join. This was reproduced without Overlay in the prior [release baseline](release-1.6.0.md).

The target-specific Mixin now evaluates Forge's original completion guard at **onGameLoadFinished HEAD**. Vanilla has already set its own gameLoadFinished marker, but initial screens and quick-play have not been created yet. Success starts the event bus through Forge's normal implementation; warning/error results keep the native diagnostic screen and cancel the vanilla continuation. The original late completion invocation is removed so it is not evaluated twice. Both injection sites require one match.

This boundary matters: the first provisional implementation ran before vanilla's completion marker and failed the warning-acknowledgement rendering test. It was replaced. Its earlier normal-flow successes are not included in the final count. The final build preserves both the native completion marker and diagnostic guards.

## Final runtime verification

Minecraft 1.20.4, Java 17, MTR 4.0.5, Xaero World Map 1.45.0 / Minimap 26.4.2, JourneyMap 5.10.0. Original dependency JARs and isolated copies of the native three-station MTR fixture were used. No dependency JAR was edited.

| Forge | Startup path | Result |
|---|---|---|
| 49.2.0 | quickplay | 8/8 maps × locales |
| 49.2.0 | menu | 8/8 maps × locales |
| 49.2.0 | warning | 8/8 maps × locales |
| 49.2.0 | error | Native error page; no world started |
| 49.2.13 | quickplay | 8/8 maps × locales |
| 49.2.13 | menu | 8/8 maps × locales |
| 49.2.13 | warning | 8/8 maps × locales |
| 49.2.13 | error | Native error page; no world started |

The six world-starting runs passed **48 cases**: both native maps × en_us/zh_cn/zh_tw/ja_jp. Checks include actual rendered-station selection, Navigate dimming without a provider, native arrival boards, advancing server timestamps and unpaused map operation. Native server lifecycle events occur exactly once and both Xaero data objects are initialized by ServerStarted. No synthetic event or injected Xaero state is used.

Two intentional warning and two intentional error boots also verify the native public gameLoadFinished getter is true, while no player/world or server lifecycle event has started. Warnings require the actual Proceed button before menu/world entry; acknowledgement and subsequent map/resource reloads pass. Errors expose the real QA setup exception, offer no Proceed action, and never enter a world. The separate opt-in QA mod provides these default-off fixtures through Forge's public warning/setup-event APIs; it is absent from production JARs.

Runtime-tested production JAR SHA-256: `df9beb778c262f71522c9d1834cc54ef542e1c25b3ff835a93d3c7224fd9b047`. [Artifact audit](evidence/forge1204-startup-fix/artifact-audit.json) records [CI 42944368485](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/42944368485), all four compatibility builds/tests and package isolation. The CI patch class is byte-for-byte identical to the runtime-tested class. Its vanilla method refmap resolves onGameLoadFinished to m_286052_. The patch class/config is present only in Forge 1.20.4, absent from Forge 1.20.1 and both Fabric outputs; QA classes are absent from every production output. Existing compatibility suites pass 110 tests with one explicit world-generator skip per target.

## Reproduction

Build with Java 21 using `./gradlew.bat build navigationQaJar reobfNavigationQaJar -PnavigationQa '-Pminecraft_version=1.20.4'`; run the resulting production mod with Java 17 and original dependencies in a fresh copied fixture profile. Use normal title-screen entry or `--quickPlaySingleplayer Overlay160Test` on each tested Forge version. Drive actual keyboard/mouse input and raw screenshots through the opt-in QA bridge. The loopback listener may be IPv4 or IPv6; MTRMAP_QA_URL selects its observed address.

For native diagnostic cases, add `-Dmtrmap.qa.loadWarning=true` or `-Dmtrmap.qa.failClientSetup=true` to that test client only. Capture the native page and assert gameLoadFinished=true, connected=false and an empty serverEvents list before acknowledging a warning. The test fixtures are not runtime requirements for users.

## Release boundary

This fixes the reproduced 1.20.4 startup defect for Forge 49.2.0 and 49.2.13. It does not rewrite old candidate evidence or approve publication of old binaries. A new sealed six-target candidate and remaining release checks are required before publishing 1.6.0. Future loader changes that alter the matched startup methods require revalidation.

## Selected native captures

- [49.2.0 quickplay: ja_jp-journeymap-board](evidence/forge1204-startup-fix/fix1204-final-49.2.0-quickplay/ja_jp-journeymap-board.png)
- [49.2.0 menu: ja_jp-journeymap-board](evidence/forge1204-startup-fix/fix1204-final-49.2.0-menu/ja_jp-journeymap-board.png)
- [49.2.0 warning: native-load-warning](evidence/forge1204-startup-fix/fix1204-final-49.2.0-warning/native-load-warning.png)
- [49.2.0 warning: en_us-journeymap-board](evidence/forge1204-startup-fix/fix1204-final-49.2.0-warning/en_us-journeymap-board.png)
- [49.2.0 error: native-load-error](evidence/forge1204-startup-fix/fix1204-final-49.2.0-error/native-load-error.png)
- [49.2.13 quickplay: ja_jp-journeymap-board](evidence/forge1204-startup-fix/fix1204-final-49.2.13-quickplay/ja_jp-journeymap-board.png)
- [49.2.13 menu: ja_jp-journeymap-board](evidence/forge1204-startup-fix/fix1204-final-49.2.13-menu/ja_jp-journeymap-board.png)
- [49.2.13 warning: native-load-warning](evidence/forge1204-startup-fix/fix1204-final-49.2.13-warning/native-load-warning.png)
- [49.2.13 warning: en_us-journeymap-board](evidence/forge1204-startup-fix/fix1204-final-49.2.13-warning/en_us-journeymap-board.png)
- [49.2.13 error: native-load-error](evidence/forge1204-startup-fix/fix1204-final-49.2.13-error/native-load-error.png)
