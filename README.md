# 防诈课堂 Android App

一款离线优先、真假混合的原生 Android 互动故事应用。每篇故事有 10 个决策节点，选择会同时影响关系、独立证据、信息暴露和资金结果；完整真相只在结局揭晓。

## 构建

要求 JDK 17、Android SDK 35。

```powershell
./gradlew.bat assembleDebug
```

生成的安装包位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 当前里程碑

- `releases/fanzha-classroom-0.1.0-debug.apk`：首个可安装版本，保留初始界面作为迭代基线。
- `releases/fanzha-classroom-0.2.0-debug.apk`：Material 3 界面、卡片收藏、10 节真假混合故事、应用内剧情更新/回退及 App 更新下载。当前建议试用此版本。

本地已通过 Android 构建、Lint、APK 包信息与签名校验。当前机器没有 Emulator，也未连接 Android 真机，设备级截图和触控回归留作下一轮验收。

## 数据更新

案例库存放在 `app/src/main/assets/fraud_cases.json`。字段规范和采集边界见 `DATA_SOURCES.md`。

开源发布使用 GitHub Actions 校验和构建，GitHub Releases 承载剧情包。可选 Cloudflare Worker 提供清单缓存、有限重试、匿名上下文压缩与作者候选剧情生成。详见 `story-pack/README.md` 和 `cloudflare/README.md`。
