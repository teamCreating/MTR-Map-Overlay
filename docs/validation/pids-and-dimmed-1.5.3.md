# 1.5.3 站点PIDS与导航dimmed验证

Codex，2026-10-26 13:32（Asia/Taipei）。开发版本1.5.3；没有发布release/tag。旧1.5.2正式附件保持。功能源码仍为main `c1e0314` / compat `e041b0a`，本轮补实机QA，未改生产代码。

## 交付

- 两地图共用旧式紫边站点信息框，原站点/站台与线路描述保留。点击固定后默认折叠；Expand才订阅原生站点预测；Collapse、关闭地图或断线释放兴趣。小窗口压缩重复线路摘要，长文本可悬停查看完整内容，面板滚动不缩放地图。
- 英式black/amber为默认；欧式/机场方向为白字、蓝灰表头、轻交替行和黄色ETA。固定Destination/Line/Platform/ETA列与行距，ETA右对齐。`/mtrmap config pidsStyle british|european`设置并持久化，无参数查询。
- 可选Java17 API JAR、原生站点/线路/停站ordinal与Depot路径目录、显式路径歧义、可关闭的独立区段视图。`ViewRequest.navigation`同时选择待乘和已走区段；已走默认建议.35、未用.15，调用方可调整；重叠的后续乘车区段优先保持明亮。`spanStyles`精确到原生半轨，线路色、白块、羽化和物理底衬一同淡化，源缓存不修改。
- Navigate预留入口通过owner注册回调接入；零provider禁用，多个provider可选择。没有Navigation/howtogo专用适配，没有本地寻路、导航进度推断或车辆模拟。
- 静态v8和车辆v1不变；站点/目录使用独立可选overlay-1。服务器共享Simulator线程原生ArrivalsRequest读取及后台编码，客户端后台解码和展示；租约/容量/分片/错代/限额/玩家与兴趣轮转，长目录传输不独占所有站点发送机会。

## 构建与自动验证

| MC / loader | MTR | 构建 | JUnit |
| --- | --- | --- | --- |
| 1.21.1 NeoForge | 4.1.0-beta.2 | 通过 | 101通过、1生成用例跳过 |
| 1.21.1 Fabric | 4.1.0-beta.2 | 通过 | 共享主线101项；本目标不单独重复运行 |
| 1.20.1 Forge | 4.0.5 | 通过 | 104通过、1生成用例跳过 |
| 1.20.1 Fabric | 4.0.5 | 通过 | 104通过、1生成用例跳过 |
| 1.20.4 Forge | 4.0.5 | 通过 | 104通过、1生成用例跳过 |
| 1.20.4 Fabric | 4.0.5 | 通过 | 104通过、1生成用例跳过 |

- 实际依赖编译验证Core4.0/4.1公开接口；原生Simulator夹具保留重复停站、Depot半轨及平台/站点映射。PIDS模型验证native arrivalAt，不重复加deviation；已到站、过期班次、无预测和同站台不同目的地保持区分。
- 几何用例覆盖同一物理轨道的已走/待乘/未用三段、下一次复用同区段的优先级、被v8绘制缓存去重的原生线路身份、真实XYZ/LOD/长度、白块及底衬alpha和源快照不可变。
- `python tools/verify_platform_matrix.py`六产物namespace、loader/MC/MTR metadata、LICENSE、字节码、Mixin和共享测试检查通过。新API始终Java17，其余主线Java21/兼容Java17；校验器仅对精确api/子包放行Java17，并禁止泄漏MC/Core/地图类型。
- CI工具20项回归通过（包含新API/新输入桥六目标正例、API错误字节码与MC类型泄漏拒绝）。
- 六目标独立API JAR与内嵌API公开签名对照及Java17消费者编译检查；Fabric remap可能重排常量池，不以逐字节相同代替ABI验证。

## computer-use实机范围

NeoForge1.21.1，独立存档副本`run/saves/Overlay153Test`，未修改用户原存档。已直接操作Xaero与JourneyMap6：

- 默认旧信息框与折叠、点击Expand显示原生Local/Local Return倒计时及Express缺预测横杠；悬停能读完整平台名；保持固定、不因移出关闭。
- 英式琥珀表、指令切换european的客户端反馈、欧式白字/底色/右对齐ETA；正常退出重启后仍保留european，证明持久化。
- 小窗口压缩模式与最大化后的完整线路列表；旧ROUTES/TRACKS控件恢复；初版面板内部滚动不改变3.9x缩放。
- JourneyMap轨道不再盖住卡片；Xaero初版玩家箭头穿过背景，最终改为host post-render/独立UI深度，重启复查箭头已被站点面板覆盖。

### 2026-10-26 补齐其余五目标

按用户要求直接使用computer-use，逐个启动客户端；同时只保留一个Minecraft图形客户端。新增独立存档副本，原测试存档与用户存档均保留。Gradle开发启动器运行当前源码，不能等同production JAR/整合包启动。

| MC / loader | MTR | Xaero 1.45.0 | JourneyMap | 运行方式 |
| --- | --- | --- | --- | --- |
| 1.21.1 NeoForge 21.1.249 | 4.1.0-beta.2 | 通过（前轮） | 6.0.8，通过（前轮） | 独立单人副本 |
| 1.21.1 Fabric 0.16.14 | 4.1.0-beta.2 | 通过 | 6.0.8，通过 | 独立单人副本 |
| 1.20.1 Fabric 0.16.14 | 4.0.5 | 通过 | 6.0.6，通过 | 独立单人副本 |
| 1.20.4 Fabric 0.16.14 | 4.0.5 | 通过 | 5.10.0，通过 | 独立单人副本 |
| 1.20.1 Forge 47.4.0 | 4.0.5 | 通过 | 6.0.6，通过 | 独立单人副本 |
| 1.20.4 Forge 49.2.0 | 4.0.5 | 独立服通过；单人启动失败 | 5.10.0，独立服通过 | loopback独立服；限制见下 |

- 五目标均在Xaero检查旧式信息默认折叠、点击固定、Expand显示英式原生预测、Express缺预测横杠、Local/Local Return各自目的地与ETA、行悬停完整平台名、Escape关闭和面板内部滚轮不缩放地图。地图显示三条线路、六条轨道、七个landmark和一列全网车；多次观察可见车体位置变化。
- 五目标均在实际游戏聊天栏执行`/mtrmap config pidsStyle european`，确认成功反馈，再打开JourneyMap点击站点并展开，核对欧式表头/交替行/黄色ETA、原生目的地、轨道与车辆不穿过面板。两个1.20.4目标额外核对JourneyMap5面板滚轮与行悬停；Forge1.20.1也检查JourneyMap6滚轮。Fabric1.21.1与其他旧目标的滚轮结论分别来自其Xaero检查，不声称逐地图执行所有相同操作。
- 表格ETA持续变化，例如Fabric1.20.1的Local从16min降至12min、Forge1.20.4从6min降至3min/2min。它们是测试网络Core的原生预测，不能把长等待解释成客户端模拟或错误加时；仍未逐一对照世界内实体PIDS。
- Fabric1.21.1本地目录：主线`build/runtime-qa-153/fabric-1.21.1/saves/Overlay153Test`；启动日志`build/runtime-qa-153/fabric-1211-launch.log`。其余目录：兼容worktree `.gradle/runtime-qa-153/<loader>-<mc>/saves/Overlay153Test`，日志`fabric-1201-launch.log`、`fabric-1204-launch.log`、`forge-1201-launch.log`。
- Forge1.20.4首次单人quick-play失败，`forge-1204-launch.log`保留Xaero Minimap/WorldMap `CommonEvents.onPlayerWorldJoin`中`serverData == null`的异常和`Error loading server minimap properties. Please retry.`。失败发生于Overlay快照应用前，根因尚未确认；不能宣称单人支持已通过，也没有用独立服成功覆盖该失败。
- 随后用只含MTR和Overlay的Forge49服务端绑定`127.0.0.1:25589`，同一版本客户端连接后双地图及原生PIDS通过。日志`forge-1204-server-launch.log` / `forge-1204-dedicated-client.log`及对应目录`logs/latest.log`。客户端正常退出，服务端用其本地RCON发送`stop`正常保存退出。RCON凭据不进文档或测试包。
- 本轮没有新增Overlay运行时异常；旧目标MTR cargo loader/unloader loot-table错误仍存在，按上游资源问题单独记录。六目标功能源码对应的GitHub CI Gate均通过；自动检查与这里的UI证据分别计数。

仍未实测第三方真实provider点击/进度联动、大网络/多玩家性能、跨机器联机或production整合包启动。dimmed自动化覆盖完整几何与公开API，未声称完成真实导航模组适配/联动实测。测试夹具的缺预测服务显示LIMITED，不能解释为无车或零ETA。候选数上限不构成Core内部扫描耗时上限；不提供FPS/带宽实测成绩。

API实现与可编译示例见[公共API v1](../api/overlay-api-v1.md)。本地测试包统一放在`../test-builds/v1.5.3-pids-dimmed`，六文件名含MC/MTR/loader，并附API JAR、哈希与源码提交记录。
