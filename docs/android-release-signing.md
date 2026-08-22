# Android Release Signing

应用内 APK 覆盖安装要求新旧 APK 使用同一签名证书。GitHub Hosted Runner 自动生成的 debug key 每次都不同，不能用于正式更新链路。本仓库从 `0.7.6` 开始只发布由固定 release keystore 签名的 APK。

## 首次配置

发布密钥和本机参数位于被 Git 忽略的 `signing/` 目录：

- `fanzha-classroom-release.keystore`
- `release.properties`
- `BACKUP.txt`

将整个目录保存到加密的离线备份中。丢失这个 keystore 或其密码后，Android 无法再为已安装的正式版本发布覆盖更新。

在 GitHub 仓库的 **Settings -> Secrets and variables -> Actions -> Secrets** 中创建以下四个 Repository secrets。它们都来自本机的 `signing/release.properties` 或 keystore 文件，绝不能提交到仓库：

| Secret | 值 |
| --- | --- |
| `ANDROID_RELEASE_KEYSTORE_BASE64` | `fanzha-classroom-release.keystore` 的 Base64 单行文本 |
| `ANDROID_RELEASE_STORE_PASSWORD` | `RELEASE_STORE_PASSWORD` |
| `ANDROID_RELEASE_KEY_ALIAS` | `RELEASE_KEY_ALIAS` |
| `ANDROID_RELEASE_KEY_PASSWORD` | `RELEASE_KEY_PASSWORD` |

在 PowerShell 中生成第一项的单行 Base64：

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('signing/fanzha-classroom-release.keystore'))
```

## 后续发布

1. 提升 `app/build.gradle` 的 `versionCode` 和 `versionName`，两者都必须递增并使用 `x.y.z` 版本名。
2. 运行 `./gradlew.bat :app:assembleRelease :app:lintRelease --no-daemon --console=plain`。本机 `signing/release.properties` 会提供签名信息。
3. 提交并推送到 `main`。
4. 在 GitHub Actions 运行 `Release APK`，填入与 `versionName` 完全相同的 version、更新说明和是否为预发布版。
5. 工作流会校验剧情、构建签名 APK、提取证书 SHA-256、上传 APK 与 SHA-256 文件、创建 Release，并回写应用更新清单。

不要手动创建 `app-vX.Y.Z` 标签或手动修改 `story-pack/manifest.json`，除非工作流明确提示其回写被分支保护阻止。

## 一次性迁移

`0.7.5` 及更早的 GitHub Runner debug APK 没有稳定发布证书，无法原地升级到固定签名版本。首次使用 `0.7.6` 时，请从 GitHub Release 下载 APK，卸载旧测试版后重新安装。完成这一次迁移后，之后的固定签名版本可以通过应用内更新覆盖安装。