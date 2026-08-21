// Structural validation for app/src/main/assets/interactive_stories.json.
// Enforces the rules the project depends on: no dangling links, no cycles,
// every ending reachable, every ending key present, and no text reused across stories.
const fs = require('fs');

const file = process.argv[2] || 'app/src/main/assets/interactive_stories.json';
const root = JSON.parse(fs.readFileSync(file, 'utf8'));
const errors = [];
const report = [];

const ENDING_KEYS = {
  ending_good: 'best',
  ending_exposed: 'exposed',
  ending_partial: 'partial_loss',
  ending_major: 'major_loss',
  ending_estranged: 'estranged',
  ending_unguarded: 'unguarded',
  ending_mixed: 'mixed',
};

const MAX_LABEL = 26;
const MAX_FEEDBACK_TITLE = 20;
const MAX_CHAPTER = 12;
const MIN_DECISIONS_PER_PATH = 2;
const EFFECT_KEYS = ['relationship', 'evidence', 'exposure', 'loss'];

if (!Array.isArray(root.stories) || root.stories.length < 1) errors.push('stories must not be empty');

const textOwners = new Map();
const storyIds = new Set();
const claimText = (value, owner) => {
  const key = (value || '').trim();
  if (!key) return;
  if (!textOwners.has(key)) textOwners.set(key, []);
  textOwners.get(key).push(owner);
};

for (const story of root.stories || []) {
  const sid = story.id || '(missing id)';
  if (storyIds.has(sid)) errors.push('duplicated story id ' + sid);
  storyIds.add(sid);
  for (const key of ['id', 'title', 'teaser', 'shelf', 'reveal', 'clueReveal', 'lesson']) {
    if (typeof story[key] !== 'string' || !story[key].trim()) errors.push(sid + ': ' + key + ' is required');
  }
  if (!Array.isArray(story.timeline) || story.timeline.length < 4) errors.push(sid + ': timeline needs at least 4 beats');

  const nodes = story.nodes || [];
  const byId = new Map(nodes.map((node) => [node.id, node]));
  if (byId.size !== nodes.length) errors.push(sid + ': node ids must be unique');
  const endings = nodes.filter((node) => !(node.choices || []).length).map((node) => node.id);
  if (endings.length < 3) errors.push(sid + ': needs at least 3 endings, found ' + endings.length);

  for (const node of nodes) {
    if ((node.chapter || '').length > MAX_CHAPTER) errors.push(sid + '/' + node.id + ': chapter too long for the title bar');
    const choices = node.choices || [];
    if (choices.length) {
      if (choices.length < 2) errors.push(sid + '/' + node.id + ': decision node needs at least 2 choices');
      if (new Set(choices.map((choice) => choice.nextNode)).size < 2) {
        errors.push(sid + '/' + node.id + ': every choice converges immediately; add a genuinely different result');
      }
      if (!(node.scene || '').trim()) errors.push(sid + '/' + node.id + ': decision node needs a scene');
      if (!(node.prompt || '').trim()) errors.push(sid + '/' + node.id + ': decision node needs a prompt');
      claimText(node.scene, sid + '/' + node.id + '/scene');
      claimText(node.prompt, sid + '/' + node.id + '/prompt');
    }
    const choiceIds = new Set();
    for (const choice of choices) {
      if (choiceIds.has(choice.id)) errors.push(sid + '/' + node.id + ': duplicated choice id ' + choice.id);
      choiceIds.add(choice.id);
      for (const key of ['id', 'label', 'feedbackTitle', 'feedback', 'nextNode']) {
        if (!(choice[key] || '').trim()) errors.push(sid + '/' + node.id + ': choice missing ' + key);
      }
      if ((choice.label || '').length > MAX_LABEL) errors.push(sid + '/' + choice.id + ': label longer than ' + MAX_LABEL + ' chars');
      if ((choice.feedbackTitle || '').length > MAX_FEEDBACK_TITLE) errors.push(sid + '/' + choice.id + ': feedbackTitle longer than ' + MAX_FEEDBACK_TITLE + ' chars');
      if (!byId.has(choice.nextNode)) errors.push(sid + '/' + choice.id + ': dangling nextNode ' + choice.nextNode);
      for (const key of EFFECT_KEYS) {
        if (!Number.isFinite(choice.effects && choice.effects[key])) errors.push(sid + '/' + choice.id + ': effects.' + key + ' must be a number');
      }
      claimText(choice.label, sid + '/' + choice.id + '/label');
      claimText(choice.feedbackTitle, sid + '/' + choice.id + '/feedbackTitle');
      claimText(choice.feedback, sid + '/' + choice.id + '/feedback');
    }
  }

  // Every ending node id must map to a declared ending copy block.
  const usedEndingKeys = new Set();
  for (const ending of endings) {
    const endingNode = byId.get(ending);
    const explicitKey = (endingNode.endingKey || '').trim();
    if (explicitKey) {
      if (!/^[a-z][a-z0-9_]*$/.test(explicitKey)) errors.push(sid + '/' + ending + ': invalid endingKey ' + explicitKey);
      if (!story.endings || !story.endings[explicitKey]) errors.push(sid + ': endings.' + explicitKey + ' copy missing for ' + ending);
      usedEndingKeys.add(explicitKey);
      continue;
    }
    const key = Object.entries(ENDING_KEYS).find(([prefix]) => ending.includes(prefix));
    if (!key) { errors.push(sid + ': unknown ending node id ' + ending); continue; }
    if (!story.endings || !story.endings[key[1]]) errors.push(sid + ': endings.' + key[1] + ' copy missing for ' + ending);
    usedEndingKeys.add(key[1]);
  }
  if (!story.endings || !story.endings.default) errors.push(sid + ': endings.default is required as a fallback');
  const endingCopies = new Map();
  for (const [key, copy] of Object.entries(story.endings || {})) {
    if (key !== 'default' && !usedEndingKeys.has(key)) errors.push(sid + ': endings.' + key + ' is never reached');
    if (!copy || typeof copy.title !== 'string' || !copy.title.trim() || typeof copy.body !== 'string' || !copy.body.trim()) {
      errors.push(sid + ': endings.' + key + ' needs title and body');
      continue;
    }
    const signature = copy.title.trim() + '\n' + copy.body.trim();
    if (endingCopies.has(signature)) errors.push(sid + ': endings.' + key + ' duplicates endings.' + endingCopies.get(signature));
    else endingCopies.set(signature, key);
  }

  // Enumerate every path from the start node; detects cycles and unreachable endings.
  const start = nodes.length ? nodes[0].id : null;
  const reached = new Set();
  const lengths = [];
  const walk = (id, seen, depth) => {
    if (endings.includes(id)) { reached.add(id); lengths.push(depth); return; }
    const node = byId.get(id);
    if (!node) return;
    for (const choice of node.choices || []) {
      if (seen.has(choice.nextNode)) { errors.push(sid + ': cycle ' + id + ' -> ' + choice.nextNode); continue; }
      walk(choice.nextNode, new Set([...seen, choice.nextNode]), depth + 1);
    }
  };
  if (start) walk(start, new Set([start]), 0);
  for (const ending of endings) if (!reached.has(ending)) errors.push(sid + ': unreachable ending ' + ending);
  const shortPaths = lengths.filter((depth) => depth < MIN_DECISIONS_PER_PATH);
  if (shortPaths.length) {
    errors.push(sid + ': ' + shortPaths.length + ' path(s) end before ' + MIN_DECISIONS_PER_PATH + ' decisions');
  }

  const orphans = new Set(nodes.map((node) => node.id));
  orphans.delete(start);
  for (const node of nodes) for (const choice of node.choices || []) orphans.delete(choice.nextNode);
  if (orphans.size) errors.push(sid + ': orphan nodes ' + [...orphans].join(','));

  report.push({
    id: sid,
    isScam: story.isScam !== false,
    decisionNodes: nodes.length - endings.length,
    endings: endings.length,
    paths: lengths.length,
    steps: lengths.length ? Math.min(...lengths) + '-' + Math.max(...lengths) : '0',
  });
}

for (const [text, owners] of textOwners) {
  if (owners.length > 1) errors.push('duplicated text used by ' + owners.join(', ') + ': ' + text.slice(0, 40));
}

if (errors.length) { console.error(errors.join('\n')); process.exit(1); }
console.log(JSON.stringify({
  file,
  stories: report.length,
  scams: report.filter((x) => x.isScam).length,
  legitimate: report.filter((x) => !x.isScam).length,
  totalEndings: report.reduce((sum, x) => sum + x.endings, 0),
  totalPaths: report.reduce((sum, x) => sum + x.paths, 0),
  detail: report,
}, null, 2));
