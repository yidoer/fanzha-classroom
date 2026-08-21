const json = (body, status = 200, headers = {}) => new Response(JSON.stringify(body), {
  status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', ...headers }
});

async function fetchWithRetry(url, init = {}, attempts = 4) {
  let last;
  for (let i = 0; i < attempts; i++) {
    try {
      const response = await fetch(url, { ...init, signal: AbortSignal.timeout(8000) });
      if (response.ok) return response;
      if (response.status < 500 && response.status !== 429) return response;
      last = new Error('upstream ' + response.status);
    } catch (error) { last = error; }
    if (i + 1 < attempts) await new Promise(resolve => setTimeout(resolve, 250 * 2 ** i + Math.random() * 200));
  }
  throw last || new Error('upstream unavailable');
}

function releaseAssetUrl(pathname, repository) {
  const prefix = '/download/';
  if (!pathname.startsWith(prefix)) return null;
  let source;
  try { source = new URL(decodeURIComponent(pathname.slice(prefix.length))); }
  catch { return null; }
  const expectedPrefix = '/' + repository + '/releases/download/';
  if (source.protocol !== 'https:' || source.hostname !== 'github.com' || !source.pathname.startsWith(expectedPrefix)) return null;
  return source.toString();
}

async function proxyReleaseAsset(request, source, cors) {
  const headers = { accept: request.headers.get('accept') || '*/*' };
  const range = request.headers.get('range');
  if (range) headers.range = range;
  try {
    const upstream = await fetchWithRetry(source, { headers }, 3);
    const responseHeaders = new Headers(upstream.headers);
    responseHeaders.set('access-control-allow-origin', cors['access-control-allow-origin']);
    responseHeaders.set('cache-control', 'public, max-age=3600');
    responseHeaders.set('x-content-type-options', 'nosniff');
    return new Response(upstream.body, { status: upstream.status, headers: responseHeaders });
  } catch {
    return json({ error: 'release asset unavailable' }, 502, cors);
  }
}

function compactContext(input) {
  return {
    schemaVersion: 1, storyId: String(input.storyId || '').slice(0, 80),
    truth: input.truth === 'legitimate' ? 'legitimate' : 'scam',
    state: {
      relationship: Number(input.relationship) || 0, evidence: Number(input.evidence) || 0,
      exposure: Number(input.exposure) || 0, loss: Math.max(0, Number(input.loss) || 0)
    },
    recentDecisions: Array.isArray(input.recentDecisions) ? input.recentDecisions.slice(-6).map(String) : [],
    unresolved: Array.isArray(input.unresolved) ? input.unresolved.slice(0, 5).map(String) : []
  };
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const cors = { 'access-control-allow-origin': env.ALLOWED_ORIGIN || '*' };
    if (request.method === 'OPTIONS') return new Response(null, { headers: cors });
    const releaseAsset = releaseAssetUrl(url.pathname, env.GITHUB_REPOSITORY || 'yidoer/fanzha-classroom');
    if (releaseAsset && request.method === 'GET') return proxyReleaseAsset(request, releaseAsset, cors);
    if (url.pathname === '/manifest' && request.method === 'GET') {
      try {
        const upstream = await fetchWithRetry(env.GITHUB_MANIFEST_URL, { headers: { accept: 'application/json' } });
        if (!upstream.ok) return json({ error: 'manifest unavailable' }, 502, cors);
        const text = await upstream.text(); JSON.parse(text);
        return new Response(text, { headers: { 'content-type': 'application/json', 'cache-control': 'no-store', ...cors } });
      } catch { return json({ error: 'manifest unavailable' }, 502, cors); }
    }
    if (url.pathname === '/compact' && request.method === 'POST') return json(compactContext(await request.json()), 200, cors);
    if (url.pathname === '/draft' && request.method === 'POST') {
      if (!env.AUTHOR_TOKEN || request.headers.get('authorization') !== 'Bearer ' + env.AUTHOR_TOKEN) return json({ error: 'unauthorized' }, 401, cors);
      const input = compactContext(await request.json());
      const prompt = 'Create a candidate Chinese anti-fraud interactive story revision. Return JSON only. It needs 10 logical decision stages, three choices per stage, legitimate non-scam possibilities, no victim blaming, and independent verification without cruelty. Compact context: ' + JSON.stringify(input);
      const result = await env.AI.run('@cf/meta/llama-3.1-8b-instruct', { prompt, max_tokens: 3500 });
      return json({ candidate: result.response, context: input, warning: 'Human review and GitHub validation are required before release.' }, 200, cors);
    }
    return json({ service: 'fanzha-story-edge', routes: ['/manifest', '/download/<github-release-url>', '/compact', '/draft'] }, 200, cors);
  }
};
