const fs = require('fs');
const path = require('path');

const [mode] = process.argv.slice(2);
const source = process.env.MANIFEST_SOURCE || 'story-pack/manifest.json';
const output = process.env.MANIFEST_OUTPUT || source;
const repository = process.env.GITHUB_REPOSITORY || 'yidoer/fanzha-classroom';
const proxyFile = path.join(__dirname, '..', 'cloudflare', 'worker-url.txt');
const proxyBase = (process.env.GITHUB_DOWNLOAD_PROXY || '').trim()
  || (fs.existsSync(proxyFile) ? fs.readFileSync(proxyFile, 'utf8').trim() : '');
const manifest = JSON.parse(fs.readFileSync(source, 'utf8'));

function downloadUrls(officialUrl) {
  const urls = [];
  if (proxyBase) urls.push(proxyBase.replace(/\/+$/, '/') + officialUrl);
  urls.push(officialUrl);
  return [...new Set(urls)];
}

function integer(name) {
  const value = Number(process.env[name]);
  if (!Number.isInteger(value) || value < 1) throw new Error(`${name} must be a positive integer`);
  return value;
}

function sha(name) {
  const value = (process.env[name] || '').trim().toLowerCase();
  if (!/^[0-9a-f]{64}$/.test(value)) throw new Error(`${name} must be a SHA-256 value`);
  return value;
}

function text(name) {
  const value = (process.env[name] || '').trim();
  if (!value) throw new Error(`${name} is required`);
  return value;
}

if (mode === 'stories') {
  const version = integer('PACK_VERSION');
  manifest.schemaVersion = 2;
  manifest.packVersion = version;
  manifest.minAppVersionCode = integer('MIN_APP_VERSION_CODE');
  manifest.downloadUrl = `https://github.com/${repository}/releases/download/stories-v${version}/story-pack.json`;
  manifest.downloadUrls = downloadUrls(manifest.downloadUrl);
  manifest.sha256 = sha('PACK_SHA256');
  manifest.changelog = text('RELEASE_CHANGELOG');
} else if (mode === 'apk') {
  const code = integer('APP_VERSION_CODE');
  const name = text('APP_VERSION_NAME');
  if (!/^\d+\.\d+\.\d+$/.test(name)) throw new Error('APP_VERSION_NAME must use x.y.z');
  manifest.latestAppVersionCode = code;
  manifest.latestAppVersionName = name;
  manifest.apkUrl = `https://github.com/${repository}/releases/download/app-v${name}/fanzha-classroom-${name}-debug.apk`;
  manifest.apkUrls = downloadUrls(manifest.apkUrl);
  manifest.apkSha256 = sha('APK_SHA256');
  manifest.appChangelog = text('RELEASE_CHANGELOG');
} else {
  throw new Error('usage: node tools/update_release_manifest.js stories|apk');
}

// Refresh both asset types on every release so one configured proxy covers all user downloads immediately.
manifest.downloadUrls = downloadUrls(manifest.downloadUrl);
manifest.apkUrls = downloadUrls(manifest.apkUrl);

fs.writeFileSync(output, JSON.stringify(manifest, null, 2) + '\n', 'utf8');
console.log(JSON.stringify({ mode, output, packVersion: manifest.packVersion, appVersion: manifest.latestAppVersionName }, null, 2));
