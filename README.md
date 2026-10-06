# Manosaba

一个 Minecraft NeoForge 1.21.1 客户端模组，用于自定义标题屏幕和启动画面，灵感来源于游戏《魔法少女ノ魔女裁判》。

## 构建环境

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 21 | 编译目标与运行环境 |
| Gradle | 8.10.2 | **勿升至 9.x**：与 Kotlin 2.1.0 插件不兼容，会导致无法应用 kotlin 插件而构建失败 |
| Kotlin | 2.1.0 | 官方兼容 Gradle 8.7–8.10 |
| Compose Multiplatform | 1.7.3 | 与 Kotlin 2.1.0 配套 |
| ModDevGradle | 2.0.148 | 最低要求 Gradle 8.8 |
| NeoForge | 21.1.252 | — |

构建：`gradlew.bat build`；启动调试：`gradlew.bat runClient`。

## 资源声明

**`src/main/resources/assets/` 目录下的所有资源文件均来自游戏《魔法少女ノ魔女裁判》解包获得。**

**这些资源的版权全部归属于原开发商所有，不属于本项目 Apache-2.0 许可证的授权范围。**

**资源文件的任何使用均需遵守原权利方要求；未经原权利方许可，包含这些资源的构建产物（如模组 jar）不得用于商业用途。如有侵权，请联系删除。**

## 致谢

感谢 B 站 UP 主 **真寻酱哟-** 提供的代码思路。

## 许可证

本项目源代码采用 [Apache-2.0](LICENSE) 许可证。

本项目由 LingyueNerina 制作，基于 [Shiiyuko 的 Manosaba-Title-Screen](https://github.com/Shiiyuko/Manosaba-Title-Screen)（Fabric 版，同样采用 Apache-2.0）移植并修改为 NeoForge 1.21.1 版本；原作品版权归 Shiiyuko 所有。

资源文件版权归原开发商所有，不在 Apache-2.0 许可证范围内。
