# 剧情包发布

App 与剧情包独立版本化。功能迭代使用 Android 的 `versionCode` / `versionName`，剧情迭代使用整数 `packVersion`。

## GitHub Actions 发布流程

剧情和 APK 均从 GitHub Actions 的 `workflow_dispatch` 手动发布，不需要自建服务器：

1. 将准备发布的改动合入 `main`，等待 `Validate and build` 通过。
2. 发布剧情时运行 `Release story pack`，标签必须是严格递增的 `stories-vN`，填写更新说明和最低兼容 `versionCode`。
3. 发布 APK 时先提升 `app/build.gradle` 的 `versionCode` / `versionName`，再运行 `Release APK`，输入必须与 `versionName` 完全一致。
4. APK 工作流会从 GitHub Actions Secrets 恢复固定发布证书，校验剧情图、构建签名 APK、Lint、版本与标签，生成 APK/证书 SHA-256，创建 GitHub Release，并把新的 `story-pack/manifest.json` 回写到 `main`。首次配置和密钥备份见 `docs/android-release-signing.md`。
5. APK 与剧情发布共用 `release-manifest` 并发锁，避免同时发布时互相覆盖清单字段。

每次发布都会额外上传 `story-pack-manifest.json` 或 `app-release-manifest.json`。如果分支保护阻止机器人回写，可从 Actions artifact 下载该文件，核对后替换 `story-pack/manifest.json` 并提交。App 中“检查更新”读取清单；下载失败会以指数退避和随机抖动最多尝试四次，校验或结构检查失败时保留旧包。Release/资产发布与清单回写分为两个阶段：前者失败会使任务失败；若只有最后一次清单推送被分支保护拦截，任务会保留已发布结果，并在步骤摘要中给出人工恢复命令。剧情包工作流会在回写前清理本次构建生成的校验文件，因此不会因未跟踪文件的 Git 恢复操作而在 Release 已发布后失败。

在 `gradle.properties` 中把 `STORY_MANIFEST_URL` 设为 Cloudflare Worker 的 `/manifest`，把 `STORY_FALLBACK_MANIFEST_URL` 设为 GitHub Raw 清单。Worker 失败时客户端会自动回退 GitHub。完全不使用 Worker 时，只配置其中任意一个即可。不要在仓库或 APK 中放任何 API 密钥。

下载清单同时保留兼容字段 `downloadUrl` / `apkUrl` 和多源字段 `downloadUrls` / `apkUrls`。新版客户端按数组顺序切换源并重试，旧版客户端仍使用官方单地址字段。正式发布建议在仓库 Settings → Secrets and variables → Actions → Variables 中设置 `GITHUB_DOWNLOAD_PROXY=https://<worker-domain>/download/`；为空时发布脚本仍会保留 GitHub 官方地址。

## 兼容规则

- 只增补内容且字段兼容：提升 `packVersion`。
- 修改剧情包结构：提升 `schemaVersion`，同时发布支持该结构的新 App。
- 新剧情依赖新版引擎：将 `minAppVersionCode` 设置为对应版本。旧 App 会提示升级，不会加载不兼容剧情。
## GitHub Raw 更新地址

当前仓库已配置为无需自建服务器：

- 清单地址：`https://raw.githubusercontent.com/yidoer/fanzha-classroom/main/story-pack/manifest.json`
- 剧情包地址：`https://github.com/yidoer/fanzha-classroom/releases/download/stories-v3/story-pack.json`
- APK 地址由 `story-pack/manifest.json` 的 `apkUrl` 指向当前固定签名 Release。

本地发布前可运行：

```powershell
node tools/validate_story_pack.js app/src/main/assets/fraud_cases.json
node tools/validate_interactive_stories.js app/src/main/assets/interactive_stories.json
node tools/validate_release_manifest.js story-pack/manifest.json
node tools/build_story_pack.js --pack-version=4 --min-app-version-code=7
```

注意：从 v3 剧情包开始，互动故事与普通案例一起随剧情包热更新；下载、SHA-256 校验、备份和回退流程同时覆盖 `cases` 与 `stories`。
