# Cloudflare 无服务器边缘层

Worker 是可选组件。静态剧情更新仍可由 App 直连 GitHub；Worker 提供清单缓存、有限重试、匿名上下文压缩和作者专用候选剧情生成。

1. 修改 `wrangler.toml` 中的 GitHub Raw 清单地址。
2. 在 Cloudflare 控制台启用 Workers AI。
3. 执行 `npx wrangler secret put AUTHOR_TOKEN` 保护 `/draft`。
4. 执行 `npm install` 与 `npm run deploy`。
5. 将 Android 的 `STORY_MANIFEST_URL` 指向 `https://<worker-domain>/manifest`。不使用 Worker 时可直接填 GitHub Raw URL。

`/draft` 只生成候选内容。候选必须提交 GitHub，通过校验、代码审查和人工试玩后才能发布给用户。
