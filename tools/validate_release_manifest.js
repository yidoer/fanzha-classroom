const fs = require('fs');

const file = process.argv[2] || 'story-pack/manifest.json';
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
const errors = [];
const requireText = (key) => {
  if (typeof manifest[key] !== 'string' || !manifest[key].trim()) errors.push(`${key} is required`);
};
const requireUrlList = (key, legacyKey) => {
  const values = manifest[key];
  // Legacy manifests remain valid so the first release after this migration can generate the arrays.
  if (values === undefined) return;
  if (!Array.isArray(values) || values.length < 1) {
    errors.push(`${key} must be a non-empty array when present`);
    return;
  }
  if (new Set(values).size !== values.length) errors.push(`${key} must not contain duplicates`);
  for (const value of values) {
    if (typeof value !== 'string' || !value.startsWith('https://')) errors.push(`${key} entries must use HTTPS`);
  }
  if (!values.includes(manifest[legacyKey])) errors.push(`${key} must include ${legacyKey}`);
};

if (manifest.schemaVersion !== 2) errors.push('schemaVersion must be 2');
for (const key of ['packVersion', 'minAppVersionCode', 'latestAppVersionCode']) {
  if (!Number.isInteger(manifest[key]) || manifest[key] < 1) errors.push(`${key} must be a positive integer`);
}
for (const key of ['downloadUrl', 'sha256', 'changelog', 'latestAppVersionName', 'apkUrl', 'apkSha256', 'appChangelog']) requireText(key);
requireUrlList('downloadUrls', 'downloadUrl');
requireUrlList('apkUrls', 'apkUrl');
if (manifest.sha256 && !/^[0-9a-f]{64}$/.test(manifest.sha256)) errors.push('sha256 must be lowercase SHA-256');
if (manifest.apkSha256 && !/^[0-9a-f]{64}$/.test(manifest.apkSha256)) errors.push('apkSha256 must be lowercase SHA-256');
if (manifest.latestAppVersionName && !/^\d+\.\d+\.\d+$/.test(manifest.latestAppVersionName)) errors.push('latestAppVersionName must use x.y.z');
if (manifest.downloadUrl && !manifest.downloadUrl.includes(`/stories-v${manifest.packVersion}/story-pack.json`)) errors.push('downloadUrl does not match packVersion');
if (manifest.apkUrl && !manifest.apkUrl.includes(`/app-v${manifest.latestAppVersionName}/fanzha-classroom-${manifest.latestAppVersionName}-debug.apk`)) errors.push('apkUrl does not match latestAppVersionName');

if (errors.length) {
  console.error(errors.join('\n'));
  process.exit(1);
}
console.log(JSON.stringify({ file, packVersion: manifest.packVersion, appVersion: manifest.latestAppVersionName }, null, 2));
