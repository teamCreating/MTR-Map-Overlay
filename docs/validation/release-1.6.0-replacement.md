# 1.6.0 replacement publication

Verified 2026-11-11T17:11:24+08:00 (UTC+08:00), Codex. User explicitly requested replacing 1.6.0 and using the 1.5.3 release-note format.

[Release v1.6.0](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.6.0) is formal Latest. [Candidate42991712783](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/42991712783) passed all six builds, package checks and tests (main104 pass, compatibility107 pass each, one explicit generator skip). [Protected Publish42992406825](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/42992406825) succeeded, attempt3. Both version tags now identify the replacement source commits.

All ten public assets were downloaded and matched the sealed candidate and remote SHA-256. [Publication identity](evidence/release-1.6.0-replacement/publication.json); [manifest](evidence/release-1.6.0-replacement/release-manifest.json); [checksums](evidence/release-1.6.0-replacement/SHA256SUMS).

[Six-JAR class audit](evidence/release-1.6.0-replacement/artifact-audit.json): the two HowToGo-specific classes are absent and ClientOverlayRuntime no longer registers that adapter. Every other production class is byte-identical to the prior runtime-tested candidate, including the generic API and Forge1.20.4 startup repair. The experimental Forge47 patch is absent. The previous 72 runtime cases remain historical; no fresh full native matrix is claimed. Forge47 launcher quick-play can time out; the normal multiplayer list is the previously verified path.

The public notes follow 1.5.3: bilingual Added/Optimized sections, exact MC/MTR builds, then two comparison links. They describe generic provider-owned Navigate integration. No HowToGo integration or internal publication narrative is advertised. The candidate notes file retains its original source snapshot; the final public body matches the separately committed revised docs/releases/v1.6.0.md exactly.

The original publication identity and ten downloads are retained locally at build/release160-original.json and build/published160-verification; its tracked [verification](evidence/release-1.6.0-final/publication.json) remains historical. The reusable old draft lost its version association when its original tag was removed, causing two upload attempts to fail before any assets were uploaded. That empty draft was removed; attempt3 created the correct new draft and completed normal protected publication. No force-push was used.
