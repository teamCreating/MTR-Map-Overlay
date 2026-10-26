# STATE.md — MC 1.20 兼容分支权威状态

- 2026-10-26 14:18，Codex-Release：按用户提供的1.5.2内容与形式精简1.5.3公开release note，保留中英文新增/优化、MC/MTR说明及末尾双分支diff，并按用户明确要求移除Forge1.20.4限制段。已更新GitHub正文并回读比对；十附件ID/名称/大小/SHA256/时间戳及正式版标记保持。只改说明，未更改tag、版本、二进制或已封存Candidate。
- 2026-10-26 14:07，Codex-Release：按用户授权发布GitHub [v1.5.3](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.3)，正式版/Latest。Candidate 40276489811、Publish 40277036324通过；锁定main 29ab1e17b3496da2899cc0a6740de8afbd5e7faf / compat e272b35534ab726ea0364a2d38aca9ebe007c655，双tag未移动。六JAR和四验证附件全部重新下载，与候选及远端SHA256相同；完整文件名无自定义label。中英文说明标明MC/MTR/loader、功能/优化、Forge49单人失败及上次release diff。见[发布验收](../validation/release-1.5.3.md)。版本继续1.5.3，旧1.5.2附件保持。
- 最后更新：2026-10-26 14:18，操作者：Codex-Release
- 2026-10-26 13:32，Codex：补齐Fabric1.21.1和四旧目标的computer-use实机回归，六平台双地图均完成站点折叠/展开、原生ETA和英式/欧式样式检查；各loader滚轮和悬停检查通过。Forge1.20.4单人quick-play遇到Xaero serverData为空而断线，独立本地服务器连接后的双地图通过，单人问题保留待查。未改生产代码，功能源码仍main c1e0314 / compat e041b0a；未发布。详见[验证](../validation/pids-and-dimmed-1.5.3.md)。真实导航provider端到端和大网性能仍未测。
- 分支：`compat/mtr-4.0-mc-1.20`；原基于 main `6afde36`（v1.5.1），现移植 main `df9df96` 最新功能。
- 开发版本：**v1.5.3**（用户指定，禁止自动递增）；mod ID `mtrmap`；包 `io.github.teamcreating.mtrmap`；客户端命令 `/mtrmap`。
- 2026-10-26 10:36，Codex：站点原生快照、Java17 API、英式/欧式PIDS/持久命令、默认折叠旧信息和导航dimmed已移植至feat/1.5.3-station-board-mc120；Forge47/49网络和滚轮API及JM5桥独立适配。四目标各104测试、构建、产物及API消费者通过；此处10:36的验收尚未包含旧目标新PIDS实机；后续补测及Forge49单人限制见上方最新QA记录。见[验证](../validation/pids-and-dimmed-1.5.3.md)；未发布。
- 主线继续保留 NeoForge/Fabric MC 1.21.1，独立 worktree `../mtrsurveyor` 保留 1.21.1；本轮主线另修复 Fabric JourneyMap 入口，不以兼容代码覆盖主线。

- 2026-10-22 18:11，Codex：用户指定命名空间迁移至 `io.github.teamcreating.mtrmap`，maven_group=`io.github.teamcreating`；四目标各89项测试及产物检查通过，无旧包类/入口，原始LICENSE已打包。Java文件仅包名差异，mod ID/配置/网络协议保持。Fabric1.20.4独立服/JourneyMap5实际加载、绘制、悬停通过；迁移验收时未发布；现已按用户要求替换GitHub v1.5.2附件，tag保留。

- 2026-10-22 18:21，Codex-Release：按用户明确要求，GitHub v1.5.2的六个旧JAR已替换为新命名空间io.github.teamcreating.mtrmap构建，SHA256SUMS和中英文说明同步。重新下载七附件核对哈希/新类路径/LICENSE通过，完整文件名显示无自定义label，保持正式版/Latest。当前附件源码main959b7e4、compat54a0acd，正文包含精确源码链接和与v1.5.1的更新diff；原tag保留未强推。版本继续1.5.2。

## 平台与依赖

| 项 | MC 1.20.1 | MC 1.20.4 |
| --- | --- | --- |
| Minecraft / Java | 精确 1.20.1 / Java 17 | 精确 1.20.4 / Java 17 |
| Forge | 47.4.0 | 49.2.0 |
| Fabric Loader / API | 0.16.14 / 0.92.6+1.20.1 | 0.16.14 / 0.97.3+1.20.4 |
| MTR | 4.0.5 | 4.0.5 |
| Xaero World Map / Minimap | 1.45.0 / 26.4.2 | 1.45.0 / 26.4.2 |
| JourneyMap | 1.20.1-6.0.6 / v2 API | 5.10.0 / 旧 API |
| pack_format | 15 | 22 |

Gradle 使用 JDK 21；ForgeGradle 6.0.54 / Gradle 8.8，Loom 1.17.21 / Gradle 9.5.0。
MTR 4.0.5 是 2026-09-30 从 Modrinth 官方项目 API 核实的最新稳定 4.0.x，发布包同时提供上述四个目标。
JourneyMap 6 和 Fabric JourneyMap 5 的嵌套 API 校验后提取为编译依赖；Forge JourneyMap 5 的 API 直接在原发布包中。均不捆绑进本模组。

## 架构与移植

- 2026-10-22 22:22，Codex-CI：接入新命名空间双分支CI及稳定CI Gate、参数化产物校验；main新增自动Snapshot、按双SHA跳过的Nightly、固定六包Candidate及手动审批Publish。18项CI工具回归测试、既有六包校验和候选封装/篡改拒绝本地通过；GitHub Runner六目标及CI Gate、远端产物复核、Candidate、Nightly完整/跳过、Snapshot六包及Publish dry-run验收通过；Environment已设，维护分支规则在验收提交推送后启用。RELEASE_TOKEN按用户选择稍后配置，详见docs/validation/ci-cd.md。当前不发布正式版、不改版本或已发布产物。说明见docs/CI-CD.md。

已移植最新 1.5.2：原生曲线、接头/缩放、轻铁0.7和Rail/高铁1.0宽度、高铁彩白块；真实XYZ保留，纯2D绘制；全图列车动态流、车厢三级缩放、视口索引和共用悬停。客户端无行车模拟，距离/ETA继续暂停。
MTR mod 侧包为 `org.mtr.mod.*`，主入口 accessor 指向 `org.mtr.mod.Init.main`。
Forge 使用方向受限的可选 SimpleChannel；Fabric 使用 1.20 原始包通道；静态 v8 保留 hash 探测、200KB 分块和重组校验，新增独立动态 v1 通道/能力/租约/完整帧和清理。
物理客户端初始化与回调保持隔离。MTR 4.0 平台轨道按端点精确匹配，再按距离回退。
Forge Xaero mixin 接受开发名称和 SRG 运行时名称；Fabric 接受 intermediary 运行时名称。

1.20.4 专用代码在 `compat/mc1204/`：Forge 49 的 ChannelBuilder / 直接 Context / Connection 发送；JourneyMap 5 插件、两加载器按钮事件桥及前景绘制。
可选 mixin 在 JourneyMap 5 `drawMap` 返回后运行，早于工具栏；投影从 gridRenderer 的连续坐标采样并加 getMouseDrag 的临时偏移。
`gradle/minecraft-target.gradle` 为地标构建器与存在性检查生成旧 API 适配，仅转换命名空间、显式 marker ID 和 UI EnumSet，数据处理逻辑保持共享。
Fabric 两版本均声明 `journeymap` 插件入口；不能只依赖 Forge 使用的注解扫描。未安装地图模组时相关类与 mixin 保持可选。

Core 4.0 通过只读 SidingVehiclesAccessor 在 Simulator 线程读取已有车辆集合，再取原生 bogie Vector 对中点；不反射、不扫描实体、不自行模拟。Java17辅助只适配集合/API调用。

## 验证

- Codex，2026-10-22：四目标最新1.5.2 build、各 **89项测试通过/1生成器跳过**，两组产物检查通过；四个无地图模组的独立服务端启动/连线/退出保存通过。Xaero及JourneyMap6/5全图移动、缩放/过弯/悬停/图层开关检查通过，玩家在(2000,-59,2000)、native nearby=0仍显示1列3节。详见[六平台验证](../validation/platform-matrix-1.5.2.md)。Forge1.20.4首轮glfw.dll原生崩溃，单客户端重试通过，根因未定且报告已保留。

以下为1.5.1历史验证：

- Forge 1.20.1 / Fabric 1.20.1 / Forge 1.20.4 / Fabric 1.20.4：`build` 全部成功，每目标 **25 项共用测试通过**。
- 每个构建默认跳过 1 项会写测试存档的生成器；`-PmtrmapGenerateTestWorld=true` 可显式启用。
- 包测试覆盖真实消息编解码、中文维度、最大分块、异常长度和 hash 数量；保留寻路、轨道几何、地图投影、快照及分块重组测试。
- `python tools/verify_artifacts.py` 与 `python tools/verify_artifacts.py --minecraft 1.20.4`：四目标元数据、Java 17、加载器隔离、未捆绑依赖、生产 Xaero 钩子、JourneyMap API/可选 mixin/Fabric 插件入口及测试报告通过。
- 检查 JourneyMap 5 原始 Forge/Fabric JAR：绘制钩子、投影字段/方法和插件发现机制匹配当前发布包。CI 配置已覆盖四目标；没有把本地通过表述为远端 CI 已通过。
- Codex，2026-10-01：四目标开发环境独立服务端均启动至 Done；1.20.4 Forge/Fabric 客户端均收到 2 routes / 5 rails / 6 landmarks 全网快照；两地图四种开关、平移与缩放检查通过。Ben 于 2026-10-01 手动确认 Fabric 1.20.1 入服及 Xaero/JourneyMap 渲染、开关、平移和缩放正常。四目标本地开发运行实机检查完成，尚未验证 production JAR 启动或跨机器联机。
- MC 1.20 兼容代码修复 JourneyMap 5 工具栏旧 API 参数语义：第二参数为合法主题图标名，原有大写带空格的开关文案导致 ResourceLocationException、地图初始化中断及后续 charTyped 空指针。早期两次客户端曾出现 glfw.dll 原生崩溃，最终回归未复现，原生根因未明；证据保留在隔离运行目录。

## 构建与产物

当前1.5.2四包位于根项目/Fabric的 `build/libs` 或 `build/mc1.20.4/libs`；最终测试包汇总至共享工作区 `../test-builds/v1.5.2-platform-matrix/`。GitHub [v1.5.2](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.2) 已正式发布并设Latest；兼容源码固定tag `v1.5.2-mc1.20`（13b48e0）。六附件文件名标明MC/MTR/loader，远端SHA256已核对。

默认构建 1.20.1；加 `-Pminecraft_version=1.20.4` 选择 1.20.4。PowerShell 中该参数需加引号。两加载器各自使用其 Gradle wrapper；完整命令见 README。

历史1.5.1发布附件暂存于 `.gradle/runtime-smoke/release-staging/`：
- `CRTools-MTR-Map-Overlay-1.5.1-MC1.20.1-MTR4.0.5-forge.jar`
- `CRTools-MTR-Map-Overlay-1.5.1-MC1.20.1-MTR4.0.5-fabric.jar`
- `CRTools-MTR-Map-Overlay-1.5.1-MC1.20.4-MTR4.0.5-forge.jar`
- `CRTools-MTR-Map-Overlay-1.5.1-MC1.20.4-MTR4.0.5-fabric.jar`

## 限制

- 覆盖 MC 1.20.1 / 1.20.4，未覆盖整个 1.20.x；每份 JAR 只允许其对应的精确 MC 版本。
- MTR 元数据范围为 >=4.0.5 且 <4.1；只针对当前 4.0.5 做了构建验证。
- 两加载器共享数据格式，但不宣称跨加载器联机兼容；需使用同加载器服务端/客户端。
- 路径层只采样 TRAIN 模式；全图列车要求两端支持动态v1，无服务端能力仅附近回退；洞穴层下线路悬浮。真实大车队/多人性能、production JAR整合包启动和跨机器联机未验证。
- 四个以 v1.5.1 命名并将 mod、pack、metadata 保持为 1.5.1，同时标明精确 MC / MTR 4.0.5 / Forge 或 Fabric 的兼容 JAR 已附加到 [GitHub v1.5.1 release](https://github.com/teamCreating/MTR-Map-Overlay/releases/tag/v1.5.1)。发布说明区分原始 1.5.1 MC 1.21.1 二进制与新兼容附件；未新建 release/tag，也未推回 main。
