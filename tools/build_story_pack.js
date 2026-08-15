const fs = require('fs');
const crypto = require('crypto');

const cases = JSON.parse(fs.readFileSync('app/src/main/assets/fraud_cases.json', 'utf8'));
const stories = JSON.parse(fs.readFileSync('app/src/main/assets/interactive_stories.json', 'utf8'));

const pack = {
  schemaVersion: 2,
  meta: {
    schemaVersion: 2,
    packVersion: 3,
    minAppVersionCode: 7
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
  packVersion: 3,
  cases: pack.cases.length,
  stories: pack.stories.length,
  sha256: sha
}, null, 2));
