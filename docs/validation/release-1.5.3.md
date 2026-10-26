# v1.5.3发布验收

Codex-Release，2026-10-26 14:07（Asia/Taipei）。用户明确授权发布；版本保持1.5.3。

- [正式Release / Latest](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3)
- [主线PR #3](https://github.com/teamCreating/MTR-Map-Overlay/pull/3)与[兼容PR #2](https://github.com/teamCreating/MTR-Map-Overlay/pull/2)通过CI Gate后合并。原PR #1仅合并早期设计，后续实现已由#3纳入main。
- [六平台Candidate 40276489811](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/40276489811)
- [Publish只读演练 40276843060](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/40276843060)
- [正式Publish 40277036324](https://github.com/teamCreating/MTR-Map-Overlay/actions/runs/40277036324)
- `v1.5.3` → `29ab1e17b3496da2899cc0a6740de8afbd5e7faf`；`v1.5.3-mc1.20` → `e272b35534ab726ea0364a2d38aca9ebe007c655`。标签与候选源SHA一致，未覆盖旧标签。
- Manifest SHA256：`d3f8bf3613555a0240d3003bacbeb7ede8eca36daa50704e19698bb44b9d139a`。

## 检查结果

1. 六目标构建、主线101/兼容各104测试和完整候选清单/哈希/metadata/LICENSE/namespace/mapping检查通过。
2. 六份候选与本地1.5.3构建的ZIP条目清单及所有class字节完全一致；资源仅LF/CRLF差异，NeoForge Manifest另有构建时间戳差异。此前六目标两地图开发运行环境PIDS证据适用于相同实现，未冒称重新进行production启动器/整合包验收；候选原始人工checklist未伪造打勾，正文验收记录明确此边界。
3. 用户配置`github-release`环境的`RELEASE_TOKEN`，既有审批完成后工作流重新下载锁定Artifact并发布，没有重新编译或复制本地登录凭据。
4. Release为非Draft、非Prerelease且Latest。六个JAR与`SHA256SUMS`、`release-manifest.json`、`MANUAL-VALIDATION.md`、`validation-evidence.zip`共10附件全部重新下载，逐个与候选字节及GitHub SHA256 digest核对；所有附件label为空，完整MC/MTR/loader文件名保留。
5. 中英文说明包含新增功能/优化、依赖选择、单人Forge49失败、其他未验证范围与上一release双分支diff，正文末尾再次附主线/兼容diff。没有修改1.5.2正式附件。

## 六个正式JAR的SHA256

| 文件 | SHA256 |
| --- | --- |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.21.1-MTR4.1.0-beta.2-neoforge.jar` | `bd959645ab86317197aa78ddbe79c8d3c58f510c102920ca054b809cfbc42a76` |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.21.1-MTR4.1.0-beta.2-fabric.jar` | `4140368fbc1bba849ed2150575778f5e605dd6d5e827c4954550f8dacff2f337` |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.20.1-MTR4.0.5-forge.jar` | `11d4ff19ac1eb6c4e355b1ce26aa95005a4709b8e17e5d0bba68457795268143` |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.20.1-MTR4.0.5-fabric.jar` | `49db984939488c1a31b03cf788ff4d8902969e7c92474c974f076a459a5a37b7` |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.20.4-MTR4.0.5-forge.jar` | `a62d96e7f83c65b0ee326408ef419ca0a5a3c7f828aa2de5eb3c63e45b218647` |
| `CRTools-MTR-Map-Overlay-1.5.3-MC1.20.4-MTR4.0.5-fabric.jar` | `f4893bdadc4c316f3f18d27579c25be35d0e71fc87d64c013d6464d611017898` |

已知Forge1.20.4单人quick-play在Xaero初始化阶段断线；独立服双地图通过，根因未确认。真实导航provider、production启动器、大网/多玩家及跨机器联机仍未实测。见[完整PIDS验证](pids-and-dimmed-1.5.3.md)。
