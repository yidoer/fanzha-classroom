const fs = require('fs');
const crypto = require('crypto');

const file = process.argv[2] || 'app/src/main/assets/fraud_cases.json';
const root = JSON.parse(fs.readFileSync(file, 'utf8'));
const errors = [];
if (root.meta?.schemaVersion !== 1) errors.push('meta.schemaVersion must be 1');
if (!Number.isInteger(root.meta?.packVersion) || root.meta.packVersion < 1) errors.push('meta.packVersion must be positive');
if (!Array.isArray(root.cases) || root.cases.length < 1) errors.push('cases must not be empty');
const ids = new Set();
for (const [index, story] of (root.cases || []).entries()) {
  for (const key of ['id', 'title', 'category', 'summary', 'story', 'sourceName']) {
    if (typeof story[key] !== 'string' || !story[key].trim()) errors.push('cases[' + index + '].' + key + ' is required');
  }
  if (ids.has(story.id)) errors.push('duplicate id: ' + story.id);
  ids.add(story.id);
  if (!Array.isArray(story.warningSigns) || story.warningSigns.length < 3) errors.push(story.id + ': 3 warning signs required');
  if (!Array.isArray(story.response) || story.response.length < 3) errors.push(story.id + ': 3 responses required');
  if (story.isScam === false && !story.materialType?.includes('非骗局')) errors.push(story.id + ': legitimate story label missing');
}
if (errors.length) { console.error(errors.join('\n')); process.exit(1); }
const bytes = fs.readFileSync(file);
console.log(JSON.stringify({
  file, schemaVersion: root.meta.schemaVersion, packVersion: root.meta.packVersion, stories: root.cases.length,
  scams: root.cases.filter(x => x.isScam !== false).length, legitimate: root.cases.filter(x => x.isScam === false).length,
  sha256: crypto.createHash('sha256').update(bytes).digest('hex')
}, null, 2));
