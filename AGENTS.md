# AGENTS.md — 多 Agent 协作协议（必读，优先级最高）

> **任何 AI agent（Claude Code / Codex / ZCode / 人类协作者）在本仓库开始工作前，必须完整阅读本文件。**
> 本文件的存在原因：2026-09-16 发生过一起事故——某个 agent 按过时认知把仓库暂存区整体回退到已废弃的
> Forge 1.20.1 架构（丢弃了整个 NeoForge 移植），险些毁掉两周的工作。以下规则是为了让这种情况不再发生。

## 0. 三条铁律（违反 = 事故重演）

1. **平台已定，禁止回退**：本项目当前且未来的目标是 **NeoForge 1.21.1 + MTR 4.1.0-beta.2**。
   main 分支上不存在 Forge 1.20.1 代码（旧代码只存在于 git 历史 `77121f5` 之前）。
   如果你发现工作区/代码"看起来像 Forge 1.20.1"或与你记忆中的项目不符——**是你的认知过时了**，
   请先读 `docs/agents/STATE.md` 和 `walkthrough.md`，而不是"修复"它。
2. **禁止破坏性 git 操作**：不得执行 `git reset --hard`、`git checkout <ref> -- .`、`git push --force`、
   `git clean -fd`、`git branch -D`。工作区与 HEAD 不一致时，用 `git stash`（加说明）或先 diff 确认再逐文件处理。
3. **先领任务，再动代码**：任何非 trivial 修改，必须先在 `docs/agents/TASKS.md` 认领（claim），
   完成后释放。两个 agent 不要同时改同一批文件。

## 1. 工作流程（每个 agent 每次会话执行）

```
① 读 docs/agents/STATE.md        ← 项目当前权威状态（版本/平台/已验证功能/已知问题）
② 读 docs/agents/TASKS.md        ← 任务看板：认领空闲任务，或在 MESSAGES.md 提案新任务
③ 开始工作前：在 TASKS.md 把任务标为 [进行中，操作者=你的名字，时间]
④ 工作（小步提交：每完成一个逻辑步骤 commit & push 一次）
⑤ 完成后：
   - 更新 STATE.md（若改变了项目状态：版本/依赖/架构/验证结果）
   - 在 TASKS.md 标记任务完成
   - 在 MESSAGES.md 追加一条消息告知其他 agent（格式见文件头）
   - commit & push
```

## 2. 消息与状态文件的写法约定

- `STATE.md` / `TASKS.md`：**就地编辑**（它们是"最新状态"，不是日志）。
- `MESSAGES.md`：**只追加，不修改、不删除**他人消息（它是交流记录）。新消息加在最上面。
- 每条状态/消息必须带：操作者标识 + 日期时间（`YYYY-MM-DD HH:MM`）。
- 冲突处理：push 被拒（远端有新提交）时 `git pull --rebase` 解决后再推；不得强推。

## 3. 项目速览（详情见 walkthrough.md，速览可能滞后，以 STATE.md 为准）

- **是什么**：把 MTR 交通网络画到 Xaero's World Map 上的 NeoForge 客户端 mod（对标 Create 列车地图）。
- **平台**：NeoForge 21.1.249 / MC 1.21.1 / MTR 4.1.0-beta.2 / Xaero World Map 1.45.0 / Minimap 26.4.2。
- **构建**：JDK 21，`./gradlew build`（详见 walkthrough.md §6）。
- **测试**：`./gradlew runClient -Pquickplay=TestWorld`，日志检查清单见 walkthrough.md §7。

## 4. 事故记录（为什么有这份协议）

- **2026-09-16 回退事故**：某 agent 将工作区+暂存区整体回退为已废弃的 Forge 1.20.1 代码
  （删除 network/ 包、neoforge.mods.toml、路径层渲染器，共 -1574 行），随后自行 `git reset` 丢弃。
  HEAD 未受损，但暴露了"agent 凭过时记忆行动"的风险。此后任何 agent 若认为项目"坏了/回退了"，
  第一反应应是核对 `STATE.md` 与 `git log`，而不是动手恢复。
