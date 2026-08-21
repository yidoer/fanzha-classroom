# Cloudflare 无服务器边缘层

Worker 是可选组件。静态剧情更新仍可由 App 直连 GitHub；Worker 提供清单缓存、有限重试、匿名上下文压缩和作者专用候选剧情生成。

1. 修改 `wrangler.toml` 中的 GitHub Raw 清单地址。
2. 在 Cloudflare 控制台启用 Workers AI。
3. 执行 `npx wrangler secret put AUTHOR_TOKEN` 保护 `/draft`。
4. 执行 `npm install` 与 `npm run deploy`。
5. 将 Android 的 `STORY_MANIFEST_URL` 指向 `https://<worker-domain>/manifest`。不使用 Worker 时可直接填 GitHub Raw URL。

当前 Worker 已部署，域名：`https://fanzha-story-edge.yidoer.workers.dev`。发布脚本会读取仓库中的 `cloudflare/worker-url.txt`，自动把 Worker 下载代理写进剧情包和 APK 的多源更新地址；如果该文件不存在，则只保留 GitHub 官方地址。

## GitHub Release 下载加速

Worker 还提供受限的 `/download/<GitHub Release URL>` 路由，只允许代理 `GITHUB_REPOSITORY` 指定仓库的 Release 文件，不会成为任意网址的开放代理。部署后在 GitHub 仓库的 Actions variables 中新增：

`GITHUB_DOWNLOAD_PROXY=https://<worker-domain>/download/`

APK 与剧情发布工作流会把这个地址写入更新清单的首选下载源，同时保留 GitHub 官方地址作为自动回退。客户端依次换源重试，最终仍以 SHA-256 校验文件，代理返回的内容无法绕过完整性检查。未配置变量时，工作流只发布 GitHub 官方地址，不会依赖不受控制的公共代理。

`/draft` 只生成候选内容。候选必须提交 GitHub，通过校验、代码审查和人工试玩后才能发布给用户。
