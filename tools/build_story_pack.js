const fs = require('fs');
const crypto = require('crypto');

const args = Object.fromEntries(process.argv.slice(2).map((arg) => {
  const [key, value] = arg.replace(/^--/, '').split('=', 2);
  return [key, value];
}));
const manifest = JSON.parse(fs.readFileSync('story-pack/manifest.json', 'utf8'));
const packVersion = Number(args['pack-version'] || process.env.PACK_VERSION || manifest.packVersion);
const minAppVersionCode = Number(args['min-app-version-code'] || process.env.MIN_APP_VERSION_CODE || manifest.minAppVersionCode);
if (!Number.isInteger(packVersion) || packVersion < 1) throw new Error('pack-version must be a positive integer');
if (!Number.isInteger(minAppVersionCode) || minAppVersionCode < 1) throw new Error('min-app-version-code must be a positive integer');

const cases = JSON.parse(fs.readFileSync('app/src/main/assets/fraud_cases.json', 'utf8'));
const stories = JSON.parse(fs.readFileSync('app/src/main/assets/interactive_stories.json', 'utf8'));

const pack = {
  schemaVersion: 2,
  meta: {
    schemaVersion: 2,
    packVersion,
    minAppVersionCode
  },
  cases: cases.cases,
  stories: stories.stories
};

fs.writeFileSync('story-pack.json', JSON.stringify(pack, null, 2), 'utf8');
const bytes = fs.readFileSync('story-pack.json');
const sha = crypto.createHash('sha256').update(bytes).digest('hex');
fs.writeFileSync('story-pack.sha256', sha + '  story-pack.json\n', 'utf8');
console.log(JSON.stringify({
  file: 'story-pack.json',
  schemaVersion: 2,
  packVersion,
  minAppVersionCode,
  cases: pack.cases.length,
  stories: pack.stories.length,
  sha256: sha
}, null, 2));
