# MESSAGES.md — Agent 交流墙（只追加，不改不删）


## [2026-10-22 18:21] Codex-Release — v1.5.2正式附件已换为新包名构建

- 用户明确授权原位替换：六JAR和SHA256SUMS已更新，MC/MTR/loader完整文件名与下载地址保持，下载显示label为空。已重新下载全部附件，SHA256与冻结新构建一致，class路径均为io/github/teamcreating/mtrmap且原始LICENSE齐全。
- 发布正文新增中英文附件更新说明、重新下载提示、精确源码提交与最新完整diff。原tag保留；当前附件来源main959b7e4/compat54a0acd，仍为v1.5.2正式版/Latest。
- 本次仅修改Release和发布记录，不触碰并行Codex-CI的workflow/tools/ci任务；其任务认领保留未纳入本次提交。[Release](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.2)。


## [2026-10-22 18:14] Codex — 命名空间迁移验证及测试包完成

- 六构建及主线86/四兼容各89测试通过，包内无旧命名空间，入口/Mixin/字节码/加载器/原始LICENSE检查通过。Java代码仅包名差异，版本仍1.5.2。
- 本轮NeoForge/Fabric1.21.1及Fabric1.20.4两地图实际加载；兼容独立服务端Done/退出保存，旧JourneyMap5路线/轨道/车辆悬停通过，远处native nearby=0仍有全图1列3节。没有重跑其余三个旧目标的GUI；历史六平台验证不改写。
- 所有测试进程已正常关闭；报告docs/validation/namespace-migration.md，新六包/SHA256/构建来源位于共享工作区test-builds/v1.5.2-teamcreating-namespace。GitHub发布tag和附件保持既有内容。


## [2026-10-22 18:11] Codex — 新团队Java命名空间完成迁移

- 用户指定 `io.github.teamcreating.mtrmap`；双分支源码目录、import、反射字符串、Mixin配置、Fabric/JourneyMap入口、Gradle排除/生成路径、脚本和当前文档已同步，maven_group为io.github.teamcreating。原作者署名保留，六JAR均附原始LICENSE。
- 107/111个Java文件反向替换与原提交核对，仅命名空间差异；mod ID/配置/网络资源ID及协议不变。六构建、86/各89项测试及无旧包/入口/许可证检查通过。旧Gradle编译及生成缓存已归档，避免混包。
- NeoForge/Fabric1.21.1实际新入口和两地图已加载；兼容旧版地图的额外检查进行中。版本保持1.5.2，未改写已发布tag/附件，后续记录将补充运行检查。


## [2026-10-22 15:01] Codex — GitHub v1.5.2正式版已发布

- [v1.5.2](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.2)为正式版/Latest，六个已验证JAR按版本-MC-MTR-loader命名，附SHA256SUMS.txt；下载标签另标明Java/loader环境。已核对远端文件大小和SHA256、正文及末尾v1.5.1...v1.5.2 diff。
- main发布tag v1.5.2固定32e024c，兼容tag v1.5.2-mc1.20固定13b48e0；较已测试源码97ea694/1bb738f仅文档差异，未改二进制。源码与记录均保留独立分支。
- 用户要求的全图列车、曲线/接头/缩放、缓存/通信优化及六平台支持已在中英文release note说明；验证边界和Forge1.20.4首次原生崩溃未隐去。距离/ETA仍暂停，后续版本保持1.5.2直至用户另行指示。


## [2026-10-22 10:00] Codex — 最新1.5.2六平台回归完成

- 用户要求先移植最新功能再测试，已完成1.20.1/1.20.4 Forge/Fabric的纯2D、原生曲线/线路样式、全图列车流、显示/视口优化移植；不覆盖main，距离/ETA保持暂停。
- 六目标构建和两地图实机检查完成：主线86项共用测试，四兼容目标各89项通过/1生成器跳过。修复主线Fabric JourneyMap入口，开发依赖完整化；产物检查确保Java17/21、精确MC/loader与无第三方捆绑。
- Forge1.20.4首轮glfw.dll原生崩溃，关闭其他游戏客户端后重试正常；根因未定，保留hs_err报告，首轮不记通过。
- 详情见docs/validation/platform-matrix-1.5.2.md。六包汇总至共享工作区test-builds/v1.5.2-platform-matrix，版本保持1.5.2；未发release/tag。production整合包启动、跨机器联机和大车队性能仍未测。

## [2026-10-01 17:47] Codex — 补发 MC 1.20 兼容 JAR

- 四目标 Forge/Fabric JAR 均完成 build（mod/pack/metadata 版本 1.5.1）；每目标 25 项共用测试、MC/Java/loader 元数据与产物隔离校验通过。所有本地运行时检查结束，Fabric 1.20.1 入服和两地图由 Ben 手动确认。
- 修复 JourneyMap 5 错误图标参数；代码及验证文档 commit `4fce1a6` 已推送兼容分支。
- 四个新附件已上传至既有 GitHub [v1.5.1 release](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.1)：文件名注明 mod v1.5.1、精确 MC 1.20.1 / 1.20.4、MTR 4.0.5-4.0.x、Forge/Fabric。原有 v1.5.1 两个 MC 1.21.1 附件未覆盖；release 正文已解释两组目标。
- production JAR 启动和跨机器联机仍需额外验证；main 未改。


## [2026-09-30 18:58] Codex — 兼容分支补齐 MC 1.20.4 双平台

- 代码 commit `5652056` 已推送 `compat/mtr-4.0-mc-1.20`；同时构建 MC 1.20.1 / 1.20.4 的 Forge/Fabric 产物。每份 JAR 严格匹配对应 MC 版本。
- 1.20.4 增加 Forge 49.2.0 网络接口及 JourneyMap 5.10.0 插件、地图按钮、前景绘制；地标数据/过滤逻辑保持共享。Fabric 两版本均补上必需的 `journeymap` 插件入口。
- 四目标 build 各 25 项共用测试通过；两版本产物检查通过，CI 配置扩展为四目标。默认构建 1.20.1，加 `-Pminecraft_version=1.20.4` 构建独立版本，PowerShell 中该参数加引号。
- 没有启动 Minecraft 或创建公共 release。main 1.21.1 worktree 干净，禁止将本分支旧版本适配覆盖回 main。地图视觉、独立服务端与联机待 Ben 实机验证；任务已释放。

> 格式（追加在最上面）：
>
> ```
> ## [2026-09-30 18:15] Codex — 建立 MTR 4.0.5 / MC 1.20.1 Forge/Fabric 兼容分支

- 用户明确要求独立旧版本分支；从 main v1.5.1 建立 `compat/mtr-4.0-mc-1.20`，worktree `../mtrmap-mc120`，版本保持 v1.5.1；main 1.21.1 工作区未改。
- 保留地图渲染和协议 v5，适配 MTR 4.0 包名/轨道引用、MC 1.20.1 渲染及两加载器网络通道。
- Forge/Fabric build 各 25 项测试通过；打包元数据、Java 17、无加载器交叉引用及生产 Xaero 钩子检查通过。CI 加入双平台测试及产物验证。
- 当前只交付 MC 1.20.1；1.20.4 的 JourneyMap 5.x 集成尚未移植。没有启动游戏或创建 release；请 Ben 实机验证地图和联机。
- 注意：不要将本兼容分支的 Forge 架构覆盖回 main。提交见本分支 git log；相关任务已释放。

## [YYYY-MM-DD HH:MM] 操作者标识 — 一句话摘要
> 正文：背景 / 做了什么 / 需要谁注意什么 / 关联 commit 或任务。
> ```

## [2026-09-25 00:43] Codex — 第二轮代码审查与优化

- `MapTrack` 在构造时计算包围盒；Xaero 整轨视口/悬停剔除和 JourneyMap 共用包围盒，减少每帧逐段检查。轨道采样遇到非有限坐标时丢弃该轨道。
- JourneyMap 前景图标在纹理、颜色与透明度未变化时合批，减少逐图标 `flush()`；变更状态前仍刷新以保持叠放顺序和着色。
- 新增独立分块重组器，校验错序/重复/越界块、哈希和总大小；快照解码按剩余字节约束元素计数。无服务端支持时探测遵循退避，切服清除客户端回退缓存。
- 代码 commit `d63ea19`；NeoForge 与 Fabric `build --no-daemon`、相关单测通过。协议字段和版本未变，尚未做 Minecraft 实机验证。

## [2026-09-25 00:32] Codex — 共用轨道采样与寻路热路径优化

- 服务端快照的 TRACK 几何一次采样后供 Depot 实际路径和无 Depot 回退路线复用；没有回退路线时不构建铁路图。
- 客户端车辆路线复用当前 TRACK 几何；Dijkstra 遍历不再为每个节点分配过滤边列表，建图时复用已知轨道方向；修正路径片段采样上限的一点越界。
- 新增共享几何及缺失轨道的单测；NeoForge 和 Fabric 的 `build --no-daemon` 均通过。代码 commit `78ae6fc`；未改协议或发布版本，未进行 Minecraft 实机验证。

## [2026-09-23 21:12] Codex — 版本更正为 v1.4.6 并重新发布
- 按 Ben 指示保留版本 1.4.6，没有继续升 patch。以 v1.4.4 为上一正式版核对 git diff，发布说明只列出此后新增/变化的内容。
- 正式 release：https://github.com/teamCreating/MTR-Xareo-Mapper/releases/tag/v1.4.6，附 NeoForge/Fabric 两个 JAR；tag 指向 8357133。
- 误发的 v1.4.7 预发布及 tag 已删除，旧链接和附件不可恢复；源码提交仍在 git 历史中。两平台无头构建通过，实机验证由 Ben 完成。

## [2026-09-23 15:36] Codex — v1.4.7 双平台预发布完成
- GitHub Release: https://github.com/teamCreating/MTR-Xareo-Mapper/releases/tag/v1.4.7；附 NeoForge 与 Fabric 两个 JAR，tag 指向 f41e62e。
- 两平台构建、JAR 元数据及跨加载器依赖静态检查通过；NeoForge 测试通过。未启动 Minecraft；Fabric Xaero 图层与跨端联机由 Ben 手动验证。

## [2026-09-23 15:32] Codex — v1.4.7 Fabric 并行构建与双平台发布准备
- 新增 Fabric Loom 独立构建；共用数据/渲染/协议 v5，平台入口、配置、客户端命令和网络适配各自实现，NeoForge 未移除。
- Fabric 与 NeoForge 无头构建已通过，最终 1.4.7 JAR 与 GitHub Release 待完成；没有自动启动 Minecraft，待 Ben 手动实机验证。

## [2026-09-23 14:09] Codex — v1.4.6 图标稳定性修复准备提交
- 项目改名已由独立提交完成；本次仅提交 Xaero/JourneyMap 图标修复与版本、任务记录，不包含改名变更。
- `mod_version` 升至 1.4.6，构建通过后推送；Minecraft 仍由 Ben 手动验证。

## [2026-09-23 14:09] Codex — 站点/站台图标固定到地图变换并修复贴图裁切
- Xaero 图标锚点改用与轨道相同的世界坐标 PoseStack；仅图标尺寸抵消缩放，悬停继续使用连续屏幕坐标。
- Xaero 与 JourneyMap 的站点/车辆段贴图都改为完整采样 32×32 PNG；`gradlew build --no-daemon` 通过，未启动 Minecraft，待 Ben 手动验证。
- 当前工作区已有另一批项目改名的暂存及未暂存改动，本修复暂未提交或推送，以免将他人的改名工作卷入提交。

## [2026-09-23 01:37] Codex — v1.4.5 地标改为全屏地图内小图标
- 删除 Xaero 普通 waypoint 创建实现和 `syncWaypoints` 命令；Xaero World Map 直接绘制 12px 站点、5px 站台和 10px 车辆段图标。
- JourneyMap `MarkerOverlay` 限定 `Context.UI.Fullscreen`，图标缩小且无常驻文字标签，不出现在 Minimap。
- 两套地图都使用协议 v5 全量 `MapLandmark`；纯客户端回退也会生成附近图标。旧 `[MTR]` Xaero waypoint 自动清理。
- `gradlew build --rerun-tasks --no-daemon` 通过；未启动 Minecraft，实机视觉验证由 Ben 手动完成。commit `4b6146d`。

## [2026-09-22 20:31] Codex — v1.4.4 全图航点与共线彩虹带完成
- 协议升级 v5：`MapTrack` 使用稳定 rail ID，路线只传 rail 引用；完整快照新增 station/platform/depot landmarks。
- Xaero 对完整快照做增量航点对账，默认同时显示站点和站台；半径回退不再删除已保存的远处航点。
- 每根物理轨道在路线层只画一次，共线颜色按 route ID 稳定排序并横向切分为 TRACK 同形 ribbon；悬停检测真实轨道。
- `gradlew build --rerun-tasks --no-daemon` 通过；按 Ben 要求未启动 Minecraft。实现 commit `6a95614`，发布收尾 commit 后补。

## [2026-09-20 22:33] Codex — v1.4.3 ROUTE 改为直接复用 TRACK 管线
- 删除路线专用的拼接 path 数据模型；每条路线现在持有若干个 TRACK 同款 `MapTrack`，每根物理轨道均由
  `TrackSampler.sample()` 生成，Depot、纯客户端车辆路径和铁路网回退三条数据源统一。
- ROUTE/TRACK 共同调用同一个 `drawPolyline()`，线宽、透明度、逐段视口裁剪和 quad 生成完全相同；
  ROUTE 只替换颜色。由逐点裁剪造成的断线也随独立绘制逻辑一起移除。
- 快照协议升级到 v4，按路线传输独立轨道折线，客户端不再拼接轨道。
- `gradlew build --rerun-tasks --no-daemon` 通过；按 Ben 要求未启动 Minecraft。commit `46a87b9`。

## [2026-09-20 18:35] Codex — 接管 ZCode 中断工作并完成 v1.4.2 严格贴轨回退
- 已从 ZCode session `sess_48dca44a-81eb-4a22-a9d7-b45b673d658f` 接管因配额中断的未提交改动。
- Depot 真实路径仍优先；无 Depot 路径时在 `positionsToRail` 上寻路，并逐轨复用
  `TrackSampler.sample()`，保证路线顶点与灰色轨道层采样一致、lane=0。
- 移除所有站点间直线回退：断路时跳过该路线并记录 warning；环线末站→首站独立寻路。
- `gradlew build --rerun-tasks --no-daemon` 通过；产物 pack 与 mod 元数据均为 v1.4.2。
- 按 Ben 要求未启动 Minecraft，实机视觉验证由 Ben 手动执行。实现 commit `e436796`。

## [2026-09-20 05:10] ZCode — v1.5.0：真实路径染色 + 哈希探测热更
- 按需求移除自研 Dijkstra 寻路，路线颜色改用 MTR 车辆段生成的真实驾驶路径
  （`Depot.getPath()`，公开 getter；`writePathCache()` 刷新轨道引用）。
  颜色按列车途经站台解析（platform.routes ∩ depot.routes），染色叠加在轨道几何上。
- 热更：新增 `NetworkSyncProbe`/`NetworkProbeResponse`（协议 v3）。
  服务端哈希 = 轨道 hexId 摘要 + 路线 id/名 + 车辆段 lastGeneratedMillis；
  客户端只对哈希变化的维度发起按维度过滤的全量请求。
- 数据生成器经验：MTR 的 Depot 生成需要 siding 真实连入铁路网
  （siding rail 必须加入 simulator.rails，否则 updateRailCache 失败被 prune）；
  生成管线在游戏 tick 中异步完成，instantDeployDepots 不保证同步生成 path。
## [2026-09-20 03:10] ZCode — v1.4.0 路线寻路吸附上线
- 路线层不再走直线：服务端快照对每条路线做铁路网 Dijkstra（站台平台轨道为锚点），
  输出真实轨道几何；共线路线按方向分组分配 ±1/±2 车道偏移，渲染为平行走线。
- 协议 v2：路由条目新增 path 点列（x/z/lane）。旧协议直接失效（同 jar 双端，无兼容负担）。
- 重要经验：MTR 数据对象（Station/Platform/Route）的 `data` 背引用必须指向所属 Simulator，
  否则 `updateRailCache` 在错误的 positionsToRail 上查找，saved rail 会被 sync 当作无效删除。
- 新增两个测试：`RoutePathfinderTest`（无头寻路测试 5 断言）与
  `TestWorldGeneratorTest`（生成带真实 MTR 网络的 TestWorld 存档，实机验证用）。

## [2026-09-16 02:20] ZCode — v1.3.0 统一版：JourneyMap 集成并入主 mod
- journeymap 分支的地标功能已移植进 main（NeoForge 1.21.1）。注意 JourneyMap 6.x 移除了旧
  `journeymap.client.api`（v1），现在使用 **v2 API**（`journeymap.api.v2.*`），依赖来自官方 maven
  `info.journeymap:journeymap-api-neoforge:2.0.0-1.21.1-SNAPSHOT`（jm.gserv.me，已在 build.gradle 配置）。
- 集成入口 `integration/journeymap/JourneyMapIntegration`（无 JM imports，类加载隔离）；
  JM API 触点全部在 `JourneyMapLandmarkManager` 与 `MTRJourneyMapPlugin`（仅 JM 在场时加载）。
- 标记纹理现在位于 `assets/mtrsurveyor/textures/atlas/marker/`（旧分支的 mtrjourneymap 命名空间
  与实际 RL 不匹配的隐患已一并修复）。
- 实机验证：JM 6.0.8 下插件被发现、landmark 同步运行、testMarker 在全屏地图上实际渲染（有截图）。
- journeymap 分支自此可视为归档（功能已并入 main）。


## [2026-09-16 01:05] ZCode — 回退事故排查完毕，仓库健康，协作协议上线
- 今晚 00:50 有 agent 对本仓库执行了 `git reset`，此前曾把工作区+暂存区整体回退为废弃的 Forge 1.20.1
  架构（-1574 行：network/ 包、neoforge.mods.toml、路径层渲染器全被删）。所幸该次回退只停留在暂存区，
  已被 reset 丢弃；HEAD(v1.2.0, fa8ff07) 完好，`git diff HEAD` 为空，`./gradlew build` 通过，
  远端 origin/main 与本地一致。
- 措施：新建 AGENTS.md（三条铁律+工作流程）、本交流墙、STATE.md、TASKS.md。
- 提醒：所有 agent 开工前必读 AGENTS.md；平台已从 Forge 1.20.1 迁移到 NeoForge 1.21.1，
  旧记忆里的 "Forge 版本/main 分支" 描述一律以 STATE.md 为准。

- 更正（2026-10-01）：按 Ben 明确要求，兼容分支及四个 MC 1.20 附件的模组、pack、metadata 版本均保持 1.5.1；已用 `CRTools-MTR-Map-Overlay-1.5.1-MC<版本>-MTR4.0.5-<loader>.jar` 命名替换先前误标的附件，不创建新版本。
