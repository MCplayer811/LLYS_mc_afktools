# LLYS MC AFK Tools

## 中文介绍

一个面向 Minecraft Java 版 Fabric 客户端的挂机宏模组，支持录制并按随机间隔顺序播放游戏内操作，也可以随机发送自定义聊天句子。

### 功能

- **F6 / F7**：开始录制 / 停止并保存宏，记录键盘、字符输入、鼠标按键、视角移动和滚轮操作。
- **F8**：开启或关闭挂机播放。
- **F10**：打开中文设置页面。聊天间隔、逐行聊天句子和宏列表可在同一个滚动页面中管理。
- 每个宏可单独设置最短/最长播放间隔、播放后是否恢复原视角、是否加入挂机队列，以及自定义注释。
- 宏按编号顺序播放；未加入挂机队列的宏会跳过。聊天和宏操作依到期顺序串行执行。
- 挂机期间切换窗口时继续播放；关闭挂机后恢复 Minecraft 原有的失焦暂停选项。

### 兼容性与构建

- Minecraft Java 版 **26.3**
- Fabric Loader **0.19.5**
- 构建需要 **JDK 25** 和 **Gradle 9.7.1 或更新版本**。

执行 `gradle build` 后，模组 JAR 位于 `build/libs/`。

> 宏播放按 Minecraft 客户端 tick 调度，实际时间精度会受到帧率、服务端延迟和游戏状态影响。请仅在服务器允许的情况下使用自动化功能。

## English

A client-side macro recorder and AFK helper for Minecraft Java Edition with Fabric. Record in-game inputs, replay queued macros with randomized intervals, and optionally send custom chat messages at random intervals.

### Features

- **F6 / F7**: Start recording / stop and save a macro. Captures keyboard input, typed characters, mouse buttons, camera movement, and scrolling.
- **F8**: Toggle AFK playback.
- **F10**: Open the Chinese settings screen. Manage chat intervals, one-message-per-line chat text, and macros in a single scrollable page.
- Configure each macro's minimum/maximum delay, whether to restore the original view after playback, whether it is included in the AFK queue, and a custom comment.
- Macros play in recording-number order; macros excluded from the queue are skipped. Chat and macro actions are serialized and run in due-time order.
- Playback continues while the game window is unfocused. The previous Minecraft pause-on-lost-focus setting is restored when AFK mode is turned off.

### Compatibility and build

- Minecraft Java Edition **26.3**
- Fabric Loader **0.19.5**
- Build with **JDK 25** and **Gradle 9.7.1 or newer**.

Run `gradle build`; the mod JAR is written to `build/libs/`.

> Macro playback is scheduled on Minecraft client ticks. Timing may vary with frame rate, server latency, and in-game state. Use automation only where permitted by the server.
