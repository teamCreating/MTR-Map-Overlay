# MESSAGES.md — Agent 交流墙（只追加，不改不删）

## [2026-10-26 10:36] Codex — 双样式PIDS与dimmed已实现并完成六构建

- 旧式默认折叠信息框、Expand/Navigate与英式/欧式PIDS按用户审阅稿落地；默认英式，命令持久保存。Native Arrivals不增加模拟；物理区段的线路/白块/底衬同步透明，completed与unmatched独立设置，后续重复乘车区段保持明亮。
- Java17 API及统一导航provider注册有可编译文档/消费者，未加入任何特定导航模组适配。四目标移植位于feat/1.5.3-station-board-mc120；双分支均1.5.3，静态v8/车辆v1保持。
- 六构建/产物、101/104单测、20工具回归、六份API ABI/Java17消费者通过；NeoForge两地图computer-use检查两主题/命令/重启保存/折叠及深度。其余五目标新PIDS实机、真实provider联动和大网仍未测；详见docs/validation/pids-and-dimmed-1.5.3.md。未发布，准备更新PR并提供本地六包。


## [2026-10-25 22:14] Codex — 1.5.3基础实施与UI审阅

- 原生目录/站点快照/可选overlay-1与Java17 API、区段视图和初版UI进入实现；主线版本1.5.3。单测及NF/Fabric初版构建通过，NF两地图初版到站板已用computer-use验证。完整六目标移植和最终UI验收仍待办，不是发布完成。
- 用户审阅设计稿后指定恢复旧式站点信息、默认折叠、Expand/Navigate，英式与欧式/机场PIDS，默认英式，指令切换；已走区段和未用线路同时dimmed。继续本任务，不加特定导航模组适配或客户端模拟。

## [2026-10-25 17:52] Codex — 最新公开MTR4.1内部UI研究完成

- 用户要求读取最新4.1内部UI写法。重新ls-remote核对stonecutter HEAD=f05d99efab6f27b3868913438fce2da381e5798f，gradle.properties仍4.1.0-beta.2/Elementa743/UniversalCraft497；不冒称4.1正式版或发布包精确源码。新增docs/plans/mtr-4.1-ui-research.md并更新UI设计。
- 采纳约束式布局、parent/child与稳定ID保留展开、释放判点击/拖动、重叠候选、父页/关闭提交；站点展开后各线路ETA默认可见。排除管理UI颜色/名称身份合并、移开关闭、全行draw/onTick重建和根WindowScreen直接嵌宿主。增加拖出再回原点不点击及刷新后滚动/焦点保持。
- 直接复用有真实打包边界：NF shadow/relocate gg.essential与Kotlin，Fabric include；最新源码版本目录仅1.21.1/1.21.4。4.0六目标兼容不能靠改import证明，独立设置页复用仅候选，地图浮层先保持轻量共享方案；公共API维持纯JDK。
- UTF-8/相对链接/代码围栏、全部MTR固定SHA路径、研究版本与依赖、diff检查通过。未复制实现/素材，未修改功能/版本/依赖、未构建或游戏实测。更新同一PR #1；任务释放。


## [2026-10-25 17:43] Codex — 1.5.3游戏内UI调查与设计补充

- 用户要求增加游戏内UI优化设计并开始调查。新增docs/plans/v1.5.3-ingame-ui.md，联动主计划与API草案：共享图层菜单、固定站点卡片、单一提示、GUIpx拾取/候选消歧、三语言/键盘/小窗口、覆盖状态、外部聚焦所有者与用户暂时全图覆盖；没有加入特定导航模组适配或任意UI注入。
- 源码发现Xaero enabled隐藏绘制但点击未同门控、按钮/地图双提示路径，JM原生标题/车辆自绘提示并存及玩家维度地标集合，设置缺项目Screen、语言仅9个列车键；单机保持运行仍仅依赖车辆层且screen精确匹配，站点ETA需新兴趣上下文。以上未本轮实机复现，不等同已修复。
- 公共JM API固定4a57dee（1.21.1_2.0.0），有可取消PRE点击/拖动但不是完整release/wheel/key桥；兼容JM5现有drawMap mixin需独立验证。参考Create 7ff2760的tooltip仲裁/生命周期，不复制素材。新增U0–U4及六平台视觉/输入/暂停/费用验收。
- UTF-8/Markdown相对链接/源码固定路径/语言键核对/git diff --check通过；未改生产代码/版本，未构建/启动游戏/发布。工作在docs/1.5.3-station-board-api更新PR #1，准备任务释放，实施仍待领。


## [2026-10-25 17:34] Codex — 1.5.3站点到站板与统一API设计准备完成

- 用户指定目标1.5.3。仅完成公共源码核对与设计，未改生产代码/版本属性，未构建或发布。计划docs/plans/v1.5.3-station-board-extension-api.md，契约docs/api/overlay-api-v1-draft.md；草案并非已有API。
- 单击站点展开固定到站板，默认折叠/按兴趣查询，按线路/方向/目的地/站台展示同源PIDS预测。原生缓存首次空/数量限制不能解释为完整无班次；全图目标维度由新服务端能力执行原生ArrivalsRequest。站点ETA恢复，车体悬停ETA/距离仍暂停。
- API提供只读目录、可关闭区段/样式会话及同源站点订阅，Java17纯JDK契约；不加入特定第三方依赖或适配器。避免目前去重MapRoute充当有序导航路径；明确停站occurrence/路径歧义、半轨原生距离、复合rail ID、异步latest-wins、错代与生命周期。
- 证据固定MTR f05d99e、Core 032baaf、Navigation df53387、howtogo 2b57bac。Core主线不是发布包内精确版本，P0必须实际依赖编译/原生对照。UTF-8/相对链接/固定源码SHA和路径/git diff --check通过，无游戏/性能实测。释放准备任务，实施任务待领。



## [2026-10-22 22:42] Codex-CI — CI/CD六平台与发布演练验收

- 两分支CI Gate、六构建、18项工具测试、远端六包/报告/Manifest复核、六包Candidate、Nightly相同SHA跳过及Snapshot六包已验证；正式版Latest保持，Publish dry-run通过且未发布正式版。记录docs/validation/ci-cd.md。
- Environment已配置；本验收提交推送后启用PR/CI Gate/禁止强推与删除规则。用户明确选择稍后配置RELEASE_TOKEN，其他流程正常可用。任务释放，版本仍1.5.2。


## [2026-10-22 22:22] Codex-CI — GitHub Actions实施与本地验证

- 已认领CI/CD任务；双分支平台源码和版本不变，新增工作流/共享Python工具、CI文档及Snapshot入口。新包名、许可证、JUnit、六包Manifest和不可变候选校验已接入。
- 18项CI工具测试及既有六包/候选完整性检查通过，接下来推送验证GitHub Runner并配置审批环境/规则；当前不触发正式发布。


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

> 格式（追加在最上面）：
>
> ```
> ## [YYYY-MM-DD HH:MM] 操作者标识 — 一句话摘要
> 正文：背景 / 做了什么 / 需要谁注意什么 / 关联 commit 或任务。
> ```

## [2026-10-22 00:34] Codex — Create 参考显示分级与全网车厢视口索引

- 用户要求优化车辆显示。对照公开 Create drawTrains，保留连续朝向/真实双锚点；本项目另加低频帧安装的扫掠包围盒层级索引，降低动画帧全网遍历。全图成员/通信范围不受视口限制，客户端没有行车模拟。
- 共用LOD阈值：远景单列5 quads、中景三节13、近景三节25；保留轮廓/线路色/车顶/方向，修复图标随放大缩短。倒车 leading 选择及悬停节数保持。
- 86测试通过/1生成用例跳过、双构建通过，NeoForge Xaero玩家远处native nearby=0仍三节连续过弯，双地图缩放与悬停通过；客户端保存退出。合成2000列8节局部查询约0.29→0.002ms，帧安装约0.4→1.3～1.8ms，非游戏FPS，真实大网/Fabric运行仍待测。
- 版本1.5.2/静态v8/动态v1/纯2D保持，距离/ETA暂停，stash未恢复。验证docs/validation/train-display-optimization.md；测试包../test-builds/v1.5.2-train-display，本条所在提交。

## [2026-10-21 20:09] Codex — 全网简化车辆流实现与NeoForge远处运行验证

- 实现Simulator线程全局车辆采集、两XYZ锚点/节、组合内容定义、动态v1独立能力/租约/原子分片、有界后台编解码/发送预算、客户端无模拟插值、双地图及双加载器生命周期。高频无文字，远坐标使用每列double原点。服务端Core原生查询仍有计算成本。
- 修复实机发现的Xaero单机暂停：启用实时列车层时Minecraft暂停判断放行GuiMap，普通菜单/关闭车辆层行为保持。新mixin为两加载器共享的原版目标注入，专服不加载。
- 81测试通过/1生成用例跳过，双构建通过；独立GlobalTrainSyncTest副本，玩家(2000,2000)，native nearby=0时Xaero仍显示1列3节连续过弯及悬停；三节正文125B。JourneyMap运行/悬停也验证；专服仅装MTR启动Done，无客户端类异常。1000列8节codec正文220899B，不是游戏FPS或网络压缩实测。
- 大车队/多人、复杂网络、跨维度和Fabric实机留后续矩阵；下一站距离/ETA仍暂停，未恢复stash。版本1.5.2/静态v8保持，测试包../test-builds/v1.5.2-global-trains。文档见docs/validation/global-trains.md；本条所在提交。

## [2026-10-21 19:25] Codex — Create 参考两锚点抽象车厢规划

- 用户明确不需侧倾等完整姿态，只需过弯/加减速。global-train-sync.md 改为每节两点 XYZ：双 bogie 取各自中心，单 bogie 保留两方向采样点；定义只需尺寸/车型和静态中心偏移。不调用客户端 MTR PositionAndRotation，也不读取光照。
- 坐标项 128→24B/节（-81.25%），每列另24B double原点；1000列×8节×4Hz含原点约864kB/s，不含其他字段/压缩，均未实测。高频包不带线路/名称等文本；Core原生查询仍有原来的计算/分配成本。
- 全网覆盖/共享采集/无客户端模拟和沿轨求解保持。更新两旧设计入口及STATE/TASKS，距离/ETA暂停，版本1.5.2/静态v8保持。仅规划与文档校验，无功能代码、构建或游戏测试；本条所在提交。

## [2026-10-21 19:16] Codex — 全图原生姿态下发重规划，客户端无模拟

- 用户确认全图所有车辆持续运动，客户端不维护模拟，尽量复用 MTR/TSC。重写 docs/plans/global-train-sync.md：全维度全部实际车辆/车厢原生 bogie 快照，初始共享 4Hz 待测；客户端仅原生车体图形变换/实测插值/2D 绘制。旧概要+视口进度流及客户端路径求解不再是当前方案。
- 完整帧原子提交、共享采集/编码、租约、过期/不外推、定位失败、背压、局部坐标编码与双平台验收均列明。直接读取公共源代码；MTR 的 Core 动态依赖不能当作精确发布内嵌提交的证据。
- 更正带宽：双 bogie 四个 XYZ+tilt double 为 128B/节；1000列×8节×4Hz 仅数值约 4.096MB/s/客户端，不沿用旧进度流估算。MTR 车体变换的光照副作用及急弯插值误差需 G0/G2 实测。
- STATE/TASKS 和旧设计入口同步；下一站距离/ETA 暂停，未恢复 stash。只改文档、无构建/游戏或性能测试，当前功能仍是附近车辆，版本 1.5.2 / 静态 v8 不变。本条所在提交为设计变更。

## [2026-10-21 17:30] Codex — 高铁与普通 Rail 等宽

- 用户明确调整高铁宽度；HIGH_SPEED 权重 1.4→1.0，轻铁仍 0.7，彩白块与缩放/接头逻辑保留。更新共线宽度测试、预览说明及 README；历史验证报告添加现状注记。
- 68 普通测试、NeoForge/Fabric 构建通过；本次未启动游戏。测试包 ../test-builds/v1.5.2-highspeed-standard-width，版本仍 1.5.2、协议 v8。

## [2026-10-21 14:39] Codex — 接头过渡与 Create 参考轨道缩放

- 用户截图两处对应：结束色带没有收束导致台阶、无线路灰轨底衬再次加上邻线宽度导致楔形。RouteRibbonProfile 改为两侧共用实际截面/绝对底衬宽度，结束色带在本轨道内平滑缩到边界；白块窄带 inset 钳制。
- 参考固定 Create 源码 XaeroTrainMap 世界空间缩放；现在轨道/共线总宽随地图缩小，仅整束保留一个 0.3 GUIpx 半宽下限，肩部/边缘同步收细，不按每路线累计屏幕宽度。保留轻/普/高宽度权重和四路线总宽上限；高铁白块普通缩放保留、窄带简化。列车样式/数据不改。
- 68 普通测试、双构建通过，NeoForge Xaero 3×/1.403×和 JourneyMap 实机复查；新增静态 taper 切分有每端 64 上限，车辆更新不重建。不声称大网 FPS；无网络改动、1.5.2 / v8 / 纯 2D 保持。见 docs/validation/route-joins-zoom.md 与 ../test-builds/v1.5.2-route-joins-zoom。

## [2026-10-21 14:08] Codex — Create 参考列车可辨识样式

- 车厢从真实窄宽同色填充改为最小 7 GUIpx 白轮廓/深框/线路色侧边/浅顶/端窗；实际 reversed 决定金色车头箭头，缩远显示定向列车图标；仅显示层夸张，真实 XYZ 与连续朝向保持。
- 65 普通测试与双构建通过，NeoForge Xaero 三节弯道/缩远和 JourneyMap 悬停实测通过。每三节 25 quads（旧 12），常数成本增加，无新网络或静态重算；全网 FPS 未测，Fabric 仅构建。详见 docs/validation/train-style.md，交付 ../test-builds/v1.5.2-train-style。
- 1.5.2 / 静态 v8 / 纯 2D 保持；全图动态流与 PIDS ETA 仍未实现。本条所在提交包含样式与验证。

## [2026-10-21 13:38] Codex — 细线原生曲线与屏幕误差细分

- 原固定 8 格折线在高倍露出折角，接头修复不能恢复曲率。新增 NativeRailCurve 保留原生几何定义，客户端原生求值/切向、按屏幕误差细分，灰色底衬逐顶点 alpha 过渡；曲线网格按保守 2 倍 LOD 有界复用，车辆更新不重算。
- **静态协议升 v8 / `_v8`，两端一起更新；mod_version 仍 1.5.2，纯 2D 保持。** 每轨道额外 184 bytes，zoom 不传细分点；Core 构造期 0.1 格包围盒扫描有界化，仍用服务端原包围盒，不改变位置解析函数。
- 61 普通测试和双构建通过。S 弯在 scale=32 的中心线误差从 4.606802 降到 0.022786 GUIpx；全形状/竖曲线/反向端点/远坐标往返、LOD 复用、边缘 alpha 和绕序均测试。不是 FPS 成绩。
- 已用 computer-use 实机检查 Xaero 约 34× 绿色轻铁弯道、最终缓存版本 JourneyMap 红白 S 弯/轻铁与行驶车辆，客户端保持运行；v7/v8 混连、全图大网 FPS 未验收。见 docs/validation/native-curves.md，交付 ../test-builds/v1.5.2-native-curves。全网动态列车与 ETA 不在本次范围。

## [2026-10-21 12:55] Codex — NeoForge 实机复现列车与线路接头

- 已启动本地 TestWorld 并用 computer-use 实际查看 Xaero/JourneyMap：附近 1 train / 3 cars / 0 capture failures；车型、2/3 和 XYZ 悬停均确认。远离车库时客户端原生 vehicles=0，也明确复现了全网动态数据仍缺失，不能宣称全图车辆已完成。
- 修复 getIsOnRoute 误过滤待发车辆、无营运线路的中性色、缩远标记黑白轮廓与限频诊断；修复曲线浮点端点身份、共享 miter、岔口主线相位、共线渐变、同色往返重复带宽、Depot 未实际染色路线误抑制回退。纯 2D / 1.5.2 / 静态 v7 保持。
- 测试存档原本无车型且部分站台方向错误，已备份后修为三站/S 弯/三车厢。生成用例现在必须 -PgenerateTestWorld，普通 build/test 不会改运行存档。53 普通测试通过，生成用例单独执行通过；双平台构建通过，Fabric 本轮未实机。
- 证据与限制：docs/validation/runtime-trains-routes.md；本地测试包/截图在 ../test-builds/v1.5.2-runtime-debug。仅验证待发/停放车辆，营运移动、全网动态流、ETA、复杂编织岔口与 FPS 仍待验收。最后一次 NeoForge 调试客户端保持运行。

## [2026-10-21 11:55] Codex — 落实 Create 式静态/动态绘制缓存

- PlanarMapScene 拆分 immutable static quads 与动态车厢，合并视图不复制静态引用；PlanarMapCache 按 snapshot identity、精确 scale、轨道/线路开关、视口 guard 缓存。小幅 pan 预取，姿态列表不变直接复用 scene，pose 更新只建车厢和 hover 目标。
- 两 host 独立缓存，每个最多保留 100000 总 quads，超限正确 uncached；源 snapshot/pose key 为弱引用，避免 key 钉住整个过时全网数据。clearClientData 现有登录/登出路径清两缓存，不加载可选地图类。MapCar bounds 去掉临时 corner list。
- 48 测试零失败，双平台构建通过。PlanarCacheProbe 合成 10000 段/4 线路/500 车厢、120 帧/40 pose revisions，static builds 120→1、car builds 120→40、线程分配 673.835→13.665MiB；仅 headless geometry，非 FPS，顶点/GPU/UI/hover 不包含。说明见 docs/validation/planar-cache.md。
- 当前仍为纯 2D、附近车辆及静态全网图；本次未实现全网动态通信/PIDS ETA、持久 GPU mesh/纹理或插值。保持版本 1.5.2，未启动游戏。

## [2026-10-21 00:44] Codex — 按用户要求改为纯 2D，版本仍 1.5.2

- PlanarMapScene 替换 HeightMapScene，先全部底衬、再线路色带/白块、最后车厢；无高度细分/排序/下穿遮挡。渲染用 primitive X/Z quads，消除每 quad 的四个 MapVertex 及顶点 List 包装。悬停只逆序检查一个 body/marker 目标/可见车厢，重叠跟随绘制顺序。
- XYZ 采集、原生坡度/侧倾姿态、tooltip、端点 Y 身份和协议 v7 保留。当前依旧附近车辆；全网动态流/PIDS ETA 未因本次变更而实现或取消。AGENTS 和当前文档同步，旧高度方案注明已被用户要求取代。
- 39 测试零失败，NeoForge/Fabric 构建通过；数量工具在平地/轨道坡道/车厢坡道三个案例均 52000 quads（原最高 209000）；TrainPlanarPreview 合成预览已检查。无游戏内或帧时间实测。测试包整理到 test-builds/v1.5.2-planar/。

## [2026-10-21 00:32] Codex — 真实高度下穿时空复杂度分析

- 用户要求下穿复杂度，核对 HeightMapScene/RouteRibbonGeometry/MapTrack/两渲染器。报告 `docs/plans/underpass-complexity.md`，区分全输入扫描、候选段、实际片段、色带/白块、动态分片、排序和拾取；不将包围盒剔除当空间索引，不称 O(车厢×轨道)。
- 新增生产几何数量 probe 并运行：10000 段×4 普通线路＋500 车厢，平地 52000、轨道高差 1.6 时 202000、加车厢高差 4 时 209000 quads。仅确定数量，未测帧耗时/JFR/GPU；对象字节和分配率明确为布局模型估算。
- 说明 0.5 高度细分不能保证通用精确遮挡；缓存静态排序＋动态合并减少重复，段级索引减少扫描；独立深度可去掉高度专用分片/CPU 总排序，但仍有片元/显存及共面装饰、透明度、hover 同步成本。运行功能与版本 1.5.2 不变。

## [2026-10-21 00:25] Codex — Create 列车地图源码研究及计划调整

- 按用户提供仓库固定 mc1.21.1/dev 提交 `7ff2760e4924d35d4bc2e22c4b19923f0178cd16`，读取地图适配/订阅/codec/客户端/Renderer/Manager、全局铁路管理、Add/RemoveTrain 和图校验修复。报告 `docs/plans/create-train-map-research.md`。
- 事实：定义登录先同步，地图按租约推全网两点 XYZ float/车厢，正常 20TPS 合计约 4Hz，full/light 各约 2Hz；light 的 codec 仍含文本。每轮完整集合缺失删除不可直接套到我们分片/增量。静态 128×128 纹理缓存值得借鉴。
- 高度仅有轨道 alpha 量化和整列平均 Y 的 GUI 偏置，不是轨道/每车厢局部真实 Y 的统一遮挡；我们继续保留高度/拾取一致性。Create 所读地图悬停路径显示导航目标站距，没有 ETA，PIDS 方案不变。
- 计划调整：G0 增加全网原生 bogie 姿态端到端原型，以实际规模和真值对照决定路径进度优化时机，静态几何缓存前移；没有实现新功能、导入第三方代码/图片、改版本或运行性能测试。版本保持 1.5.2。

## [2026-10-20 00:25] Codex — 用户要求全图列车，完成通信深度规划

- 全图是正式范围；当前 nearby collector 不满足目标。实际 Core 4.1 可从 Simulator/Siding 遍历远处实际车辆，无须强制加载 Minecraft 区块。新增 `docs/plans/global-train-sync.md`，并在旧方案顶部指向更新方案。
- 推荐全网概要 2Hz＋地图视口/选中列车 4Hz 进度，原生 RailMath/有序路径/编组定义按 revision 缓存，客户端纯数学沿轨重建车厢；两个地图提交其查看维度，不按玩家距离过滤。明确 G1 全图标记不等于完成全图每节车厢，须 G2/G3 验收。
- 核对 Create mc1.21.1/dev 订阅/共享集合与实际 codec，未声称 light 必然少传全部文本。文档包含能力、独立动态 v1、Simulator 线程采集、有界队列、原子分片、失效/成员关系/删除、PIDS 按需共享、带宽计算和性能目标。
- 本轮仅设计与源码/字节码核对，未实现动态流、未运行新功能构建或基准。版本保持 1.5.2，静态协议保持 v7；无服务端能力才回退附近。

## [2026-10-20 00:06] Codex — 按用户要求恢复并固定 1.5.2

- 用户明确要求“版本号仍然 1.5.2，我说变你再变”。已恢复构建版本及当前文档，AGENTS.md 改为仅用户明确要求才变更版本，功能提交不自动递增。
- NeoForge/Fabric 已重建，检查两个 JAR 内声明版本均为 1.5.2；正确测试包在 `test-builds/v1.5.2-train-height/`。上一条 1.5.3 交付记录已被本次更正取代，功能和协议 v7 保持不变。

## [2026-10-20 00:04] Codex — 真实 XYZ 绘制交付记录

- 功能提交 `45cc002`；37 项测试零失败，最终 NeoForge/Fabric 构建通过，已复制 MC 1.21.1 测试包及合成预览至工作区 `test-builds/v1.5.3-train-height/`。未创建发布或修改 MC 1.20 兼容分支。

## [2026-10-19 23:52] Codex — main v1.5.3 真实 XYZ 附近车厢与高度排序

- 用户要求先做绘制，本次不接 ETA、不实现全网列车动态流。MTR 原生 getVehicleCarsAndPositions/PositionAndRotation 保留真实车体 XYZ、坡度/侧倾及转向架偏移；悬停显示线路、目的地、车型 ID、车厢序号和 XYZ，标注附近数据。
- 轨道采样保留真实 Y，快照 v7 double XYZ、独立 _v7 通道；静态纹理连接点也区分高度。两地图改用 HeightMapScene，使上层轨道/线路覆盖下穿车厢，遮挡后的车辆不被悬停选中。trainCars 默认启用，可通过客户端 config 命令关闭。
- 高度排序为坡道细分的画家算法，不是像素级三维深度，也不含地形、建筑或隧道顶板遮挡。仅玩家所在维度附近车辆，MTR 客户端模拟外的列车暂不显示。
- 37 项测试及 NeoForge/Fabric 构建通过；合成 XYZ 无头预览已检查，未启动游戏或做性能基准。见 docs/validation/train-height.md。MC 1.20 worktree 未修改。

## [2026-10-19 20:50] Codex — 列车车厢、运行线路和 ETA 深度规划（未实现）

- 新增 `docs/plans/train-map-design.md`；核对实际 MTR 4.1.0-beta.2 JAR，而非把旧 Core JAR/master 当作当前依赖。
- 当前已公开 Siding.iterateVehicles/getVehicleById，4.1姿态结构与4.0不同；railProgress仍受保护。ArrivalResponse未保留实际车辆、Siding、班次偏移及完整停靠 occurrence，需要先验证稳定桥接/唯一关联。
- 路径和编组定义＋进度流为首选正式方案，逐车厢姿态作原型及对照。现有二维 MapTrack 长度、去重 MapRoute.trackIds、静态纹理方向均不能直接当列车路径。
- P0身份/API验证 → P1附近车厢 → P2全网动态流 → P3可靠ETA → P4兼容。ETA为Core预测，显式区分时刻表/无数据/过期；不按距离÷速度或线路颜色猜测。
- 更新旧列车待办范围；未修改功能源码/版本，未构建、运行游戏或做性能基准。文档中的频率、带宽、耗时是方案参数和验收目标，不是测试成绩。

## [2026-10-18 23:50] Codex — main v1.5.2 三种路线样式

- 代码 commit：`5dce52c`。
- Core 路线类型贯通服务端、客户端回退、快照与 palette。普通实线；轻铁 70%；高铁 140%，彩白交替矩形块及连续线路色描边。
- Xaero/JourneyMap 共用纯 Java 几何；混合共线按类型宽度分配，连续链累计块相位、反向轨道保持顺序、闭环无相位接缝；低缩放或色带过窄时简化。
- 快照协议 v6，四种 payload 改用 `_v6` 通道，防止 Fabric 新旧包误解码；NeoForge registrar 同步升级。全网视图需要匹配的客户端/服务端版本。
- NeoForge/Fabric 构建通过，29 项测试通过；`tools/RouteStylePreview.java` 用实际几何生成预览。未启动 Minecraft，游戏内验证待 Ben 完成。
- 仅 main；兼容 worktree 保持 v1.5.1。详情见 `docs/validation/route-styles.md`。

## [2026-10-17 13:27] Codex — README 增加开发路线图

- 中英文 README 按用户指定顺序列出：TSC 的 light rail / rail / highspeed 三种线路样式；列车位置与站点、车体预计到站时间（参考 Create）；显示效果优化。
- 所有条目均标为未完成计划，不承诺版本或日期；仅修改文档，未改版本号。

## [2026-10-01 23:52] Codex — README 平台信息与捐赠入口同步

- 按最新 GitHub v1.5.1 发布同步中英文 README：1.21.1 NeoForge/Fabric、1.20.1 与 1.20.4 Forge/Fabric 六个 JAR，列出对应 Java/MTR/地图依赖，移除过时预发布说明。
- 增加 Modrinth/CurseForge cozy badge 和 Ko-fi、自制爱发电 badge；资产在 `docs/assets/badges/`。官网在下载后加入捐赠板块，中英文内容、桌面与 390px 窄屏已检查，控制台无相关报错。
- 未修改模组源码、依赖、版本或发布附件。官网文件位于独立目录 `../mtrsurveyor-site`，尚未部署。

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
