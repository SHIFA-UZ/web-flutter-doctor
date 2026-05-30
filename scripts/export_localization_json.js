/**
 * Exports _localizedValues from lib/core/localization/app_localizations.dart
 * to assets/localization/en.json, uz.json, ru.json.
 * Run from repo root: node scripts/export_localization_json.js
 */

const fs = require('fs');
const path = require('path');

const dartPath = path.join(__dirname, '..', 'lib', 'core', 'localization', 'app_localizations.dart');
const outDir = path.join(__dirname, '..', 'assets', 'localization');

// Line ranges (1-based, inclusive): content inside each map
const blocks = [
  { lang: 'en', start: 18, end: 1604 },
  { lang: 'uz', start: 1605, end: 3181 },
  { lang: 'ru', start: 3182, end: 4671 },
];

// Parse a single Dart map line: 'key': 'value', or 'key': 'value'
// Value may contain \' and \n
function parseLine(line) {
  const trimmed = line.trim();
  if (!trimmed.startsWith("'") || trimmed.startsWith('//')) return null;
  const keyMatch = trimmed.match(/^'((?:[^'\\]|\\.)*)'\s*:\s*'/);
  if (!keyMatch) return null;
  const key = unescapeDart(keyMatch[1]);
  let rest = trimmed.slice(keyMatch[0].length);
  let value = '';
  let i = 0;
  while (i < rest.length) {
    const c = rest[i];
    if (c === '\\' && i + 1 < rest.length) {
      const next = rest[i + 1];
      if (next === "'") { value += "'"; i += 2; continue; }
      if (next === '\\') { value += '\\'; i += 2; continue; }
      if (next === 'n') { value += '\n'; i += 2; continue; }
      if (next === 't') { value += '\t'; i += 2; continue; }
      value += next; i += 2; continue;
    }
    if (c === "'") break;
    value += c;
    i += 1;
  }
  return { key, value };
}

function unescapeDart(s) {
  return s.replace(/\\(.)/g, (_, c) => {
    if (c === "'") return "'";
    if (c === '\\') return '\\';
    if (c === 'n') return '\n';
    if (c === 't') return '\t';
    return c;
  });
}

const content = fs.readFileSync(dartPath, 'utf8');
const lines = content.split(/\r?\n/);

if (!fs.existsSync(outDir)) {
  fs.mkdirSync(outDir, { recursive: true });
}

for (const { lang, start, end } of blocks) {
  const obj = {};
  for (let i = start - 1; i < end && i < lines.length; i++) {
    const parsed = parseLine(lines[i]);
    if (parsed) obj[parsed.key] = parsed.value;
  }
  const jsonPath = path.join(outDir, `${lang}.json`);
  fs.writeFileSync(jsonPath, JSON.stringify(obj, null, 2), 'utf8');
  console.log(`Wrote ${jsonPath} (${Object.keys(obj).length} keys)`);
}
