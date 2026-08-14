# 剧情包发布

App 与剧情包独立版本化。功能迭代使用 Android 的 `versionCode` / `versionName`，剧情迭代使用整数 `packVersion`。

## 发布流程

1. 更新 `story-pack.json` 的 `meta.packVersion`，并运行仓库校验脚本。
2. 计算 SHA-256：`Get-FileHash story-pack.json -Algorithm SHA256`。
3. 创建 GitHub Release，例如 `stories-v2`，上传 `story-pack.json`。
4. 更新仓库中的 `manifest.json`：填写新版本、最低 App 版本、Release 下载链接和小写 SHA-256。
5. App 中的“检查更新”读取该清单。下载失败会以指数退避和随机抖动最多尝试四次；校验或结构检查失败时保留旧包。

在 `gradle.properties` 中把 `STORY_MANIFEST_URL` 设为 Cloudflare Worker 的 `/manifest`，把 `STORY_FALLBACK_MANIFEST_URL` 设为 GitHub Raw 清单。Worker 失败时客户端会自动回退 GitHub。完全不使用 Worker 时，只配置其中任意一个即可。不要在仓库或 APK 中放任何 API 密钥。

## 兼容规则

- 只增补内容且字段兼容：提升 `packVersion`。
- 修改剧情包结构：提升 `schemaVersion`，同时发布支持该结构的新 App。
- 新剧情依赖新版引擎：将 `minAppVersionCode` 设置为对应版本。旧 App 会提示升级，不会加载不兼容剧情。
## 当前 GitHub Raw 发布方式

当前仓库已配置为无需自建服务器：

- 清单地址：`https://raw.githubusercontent.com/yidoer/fanzha-classroom/master/story-pack/manifest.json`
- 剧情包地址：`https://raw.githubusercontent.com/yidoer/fanzha-classroom/master/releases/story-pack-v2.json`
- APK 地址：`https://raw.githubusercontent.com/yidoer/fanzha-classroom/master/releases/fanzha-classroom-0.6.0-debug.apk`

更新普通案例剧情时：

1. 修改 `app/src/main/assets/fraud_cases.json`，并提升 `meta.packVersion`。
2. 运行 `node tools/validate_story_pack.js`。
3. 将文件复制为 `releases/story-pack-v{版本号}.json`。
4. 运行 `Get-FileHash releases/story-pack-v{版本号}.json -Algorithm SHA256`。
5. 更新 `story-pack/manifest.json` 的 `packVersion`、`downloadUrl`、`sha256` 和 `changelog`。
6. 提交并推送到 GitHub。用户在应用内点击“检查剧情更新”即可下载，失败时会自动重试并保留旧包。

注意：`interactive_stories.json` 的多结局互动故事目前仍随 APK 发布。若要让互动故事也应用内热更新，下一版需要把互动故事文件纳入剧情包校验、下载、回退流程。
