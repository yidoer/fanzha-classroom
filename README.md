# 防诈课堂 Android App

一款离线优先、真假混合的原生 Android 互动故事应用。每篇故事由多分支决策节点组成，选择会同时影响关系、独立证据、信息暴露和资金结果；每个结局都有完整复盘、伏笔回收和反诈提醒。

## 构建

要求 JDK 17、Android SDK 35。

```powershell
$env:GRADLE_USER_HOME="C:\Users\34748\.gradle"
.\gradlew.bat :app:assembleDebug --offline
```

生成的安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 当前里程碑

- `releases/fanzha-classroom-0.1.0-debug.apk`：首个可安装版本，保留初始界面作为迭代基线。
- `releases/fanzha-classroom-0.2.0-debug.apk`：Material 3 界面、卡片收藏、10 节真假混合故事、应用内剧情更新/回退及 App 更新下载。
- `releases/fanzha-classroom-0.6.0-debug.apk`：12 篇互动故事、48 种结局、节点跳转、结局收集、总结页复盘、语义化 UI token、矢量结局图标与 48dp 触控目标。当前建议试用此版本。
- `releases/fanzha-classroom-0.7.1-debug.apk`：当前版本，纸面主题收口、止损步骤卡、空状态图标、故事选项动效与箭头、进度条容器优化，恢复 GitHub 自动构建发布工作流。

本地校验包含案例库结构、互动剧情路径、Android 构建、Lint、APK 包信息与签名校验。模拟器截图验收会覆盖首页、故事页和总结页，确保标题栏避开状态栏、底部导航收缩、文字不重叠。

## 数据更新

案例库存放在 `app/src/main/assets/fraud_cases.json`。字段规范和采集边界见 `DATA_SOURCES.md`。

开源发布使用 GitHub Actions 校验和构建，GitHub Releases 承载剧情包。可选 Cloudflare Worker 提供清单缓存、有限重试、匿名上下文压缩与作者候选剧情生成。详见 `story-pack/README.md` 和 `cloudflare/README.md`。
