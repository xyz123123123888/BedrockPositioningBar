# BedrockPositioningBar

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)

一个 Fabric 客户端模组，解决使用 **ViaFabricPlus** 模组来连接 **Bedrock 服务器**时玩家定位条（Locator Bar）缺失的问题。

目标平台：**Minecraft 26.2**（未混淆版本）· Fabric Loader `0.19.3` · Java 25。

## 为什么需要这个模组？

Minecraft 引入了原版定位栏（Locator Bar），显示其他玩家相对自己的方位。但当玩家通过 ViaFabricPlus 连接 Bedrock 服务器时，ViaFabricPlus 未将 Bedrock 的 `LocatorBarPacket` 翻译为 Java 客户端可消费的路径点数据，导致原版定位栏在 Bedrock 服务器上不显示。

## 构建环境要求

| 组件             | 版本                           |
| ---------------- | ------------------------------ |
| Java             | 25（需 JDK 25，如 Java 25.0.3 LTS） |
| Gradle           | 9.7.0 |
| Fabric Loom      | 1.17+（自动拉取）              |
| Fabric Loader    | 0.19.3                         |
| Fabric API       | 0.157.0+26.2                   |
| Minecraft        | 26.2（stable）                 |

## 构建

# 首次构建（下载 Minecraft + 依赖，可能需要 5-10 分钟）
```bash
gradle build
```

# 仅编译 Java
```bash
gradle compileJava
```

构建产物位于 `build/libs/bedrockpositioningbar.jar`。

> 首次构建需要联网下载 Minecraft 26.2 客户端与服务端 jar、Fabric API 等，请耐心等待。

## 项目结构

```plaintext
BedrockPositioningBar/
├── build.gradle.kts              # 构建脚本（Loom 26.2 未混淆配置）
├── settings.gradle.kts
├── gradle.properties             # 版本号集中管理
├── gradle/wrapper/
│   └── gradle-wrapper.properties
└── src/main/
    ├── java/com/moxi/bedrockpositioningbar/
    │   ├── BedrockPositioningBarMod.java             # 模组主类
    │   ├── client/
    │   │   └── BedrockPositioningBarClient.java      # 客户端入口
    │   ├── config/
    │   │   ├── BedrockPositioningBarConfig.java             # JSON 配置
    │   │   └── BedrockPositioningBarConfigScreen.java       # 自绘配置界面
    │   ├── hud/
    │   │   └── BedrockLocatorBarHud.java                    # 定位条渲染器（方案 B）
    │   └── util/
    │       ├── ViaFabricPlusDetector.java            # Bedrock 会话检测
    │       └── BedrockDebugLog.java                  # 独立日志写入（debug.log）
    └── resources/
        ├── assets/bedrockpositioningbar/icon.png     # 模组 Logo / 头像（Mod Menu 显示）
        ├── fabric.mod.json
        └── bedrockpositioningbar.mixins.json
```

## 配置

配置文件位于 `config/bedrockpositioningbar.json`：

| 键                     | 类型   | 默认值 | 说明                                                         |
| ---------------------- | ------ | ------ | ------------------------------------------------------------ |
| `onlyOnBedrock`        | bool   | `true` | 是否仅在 Bedrock 服务器启用                                  |
| `showPlayers`          | bool   | `true` | 是否显示其他玩家指示器                                       |
| `showOffscreenArrows`  | bool   | `true` | 是否显示越界箭头                                             |

> **定位条布局（2026-09-05 "最后一次更新"起）**：定位条固定绘制在经验条原本紧贴物品栏上方的位置（纯原版布局缩放模式，不再提供 HUD 模组缩放）。有玩家可定位时，定位条激活并让经验条上移 14px 让出空间；无玩家时定位条自动隐藏、经验条回落原版位置。

> 若此前配置过旧版本，`config/bedrockpositioningbar.json` 中残留的 `hudScale` 字段已被移除忽略，可安全保留或删除。

除编辑 JSON 外，游戏内可通过命令 `/bpb gui` 打开**自绘配置界面**（纯原版 `Screen`/`Button`/`CycleButton` 实现，不依赖 Cloth Config / ModMenu），选项即时生效并落盘。

## 独立日志

模组运行日志单独写入 `config/bedrockpositioningbar/debug.log`（追加模式，跨会话保留），与游戏主日志 `latest.log` 分离，便于排查：

- 客户端初始化状态
- 定位条激活/感知切换
- 配置读写失败

启动时每次追加一条分隔线便于区分会话。

## 开源许可

本项目采用 **[GNU GPL-3.0](LICENSE)** 许可（copyleft）。

本模组通过反射集成 **[ViaFabricPlus](https://github.com/ViaVersion/ViaFabricPlus)**（GPL-3.0）以启用 Bedrock 服务器上的定位条显示。为规避 GPL-3.0 衍生作品争议、确保合规，本项目整体采用与依赖一致的 **GPL-3.0** 许可。
