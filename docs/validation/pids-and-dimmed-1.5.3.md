# 1.5.3 站点PIDS与导航dimmed验证

Codex，2026-10-26 10:34（Asia/Taipei）。开发版本1.5.3；没有发布release/tag。旧1.5.2正式附件保持。

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

本轮未重跑其他五目标的新PIDS实机矩阵、第三方真实provider点击/进度联动、大网络/多玩家性能、跨机器联机或production整合包启动。dimmed自动化覆盖完整几何与公开API，未声称完成真实导航模组适配/联动实测。测试夹具的缺预测服务显示LIMITED，不能解释为无车或零ETA。候选数上限不构成Core内部扫描耗时上限；不提供FPS/带宽实测成绩。

API实现与可编译示例见[公共API v1](../api/overlay-api-v1.md)。本地测试包统一放在`../test-builds/v1.5.3-pids-dimmed`，六文件名含MC/MTR/loader，并附API JAR、哈希与源码提交记录。
