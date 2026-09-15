# TASKS.md — 任务看板

> 认领规则：把任务改成 `[进行中]` 并写上操作者+时间；完成后改成 `[完成]`（附 commit）。
> 中途放弃改回 `[待领]` 并注明原因。新任务加到对应分区末尾。
> 同一时间一个任务只能有一个操作者。改别人的任务前先在 MESSAGES.md 沟通。

## 进行中

（空）

## 待领

### [功能] 列车实时位置上地图
- 在路径层上绘制在线列车小圆点/图标。数据已在客户端：`MinecraftClientData.vehicles`（`org.mtr.client.VehicleExtension`，含实时位置与路线 id）。
- 建议：每帧按维度过滤 + 视口剔除；样式参考 Create 的 drawTrains。预计工作量小。
- 验收：世界地图上看到移动的列车点，随快照/vehicles 刷新。

### [功能] 非 TRAIN 轨道渲染开关
- `TrackSampler` 目前只采样 TRAIN 模式轨道。增加配置项按 TransportMode 过滤（BOAT/CABLE_CAR 可选）。
- 验收：配置切换后地图轨道层随之变化。

### [健壮性] Xaero mixin 未命中时显式提示
- `require = 0` 静默跳过导致用户不知道路径层失效。方案：MixinPlugin postApply 已有日志；
  补一个"应用了 mixin 但首帧日志 60 秒未出现"的启动后检查日志（或 README 已有说明，评估是否足够）。

### [文档] walkthrough.md 英文化（可选）
- 面向国际用户的英文版 walkthrough（当前为中文）。

## 完成

- [完成] 统一版：JourneyMap 集成并入主 mod（v2 API，实机验证通过） — ZCode，2026-09-16
- [完成] NeoForge 1.21.1 移植 + 实机验证 — ZCode，2026-09-05，commit fa8ff07
- [完成] 全网同步（方案C）+ 轨道层 + 路径层重建 — ZCode，2026-09-05，commits 1e9ef5f..100bfab
- [完成] 回退事故排查与仓库健康验证 — ZCode，2026-09-16（HEAD 无损，详见 AGENTS.md §4）
