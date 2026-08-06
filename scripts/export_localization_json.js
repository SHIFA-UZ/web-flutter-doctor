/**
 * Exports _localizedValues from app_localizations.dart → assets/localization/{en,uz,ru}.json
 * Discovers locale blocks by marker (no fragile line ranges).
 * Run: node scripts/export_localization_json.js
 */

const fs = require('fs');
const path = require('path');

const dartPath = path.join(__dirname, '..', 'lib', 'core', 'localization', 'app_localizations.dart');
const outDir = path.join(__dirname, '..', 'assets', 'localization');

function unescapeDart(s) {
  return s.replace(/\\(.)/g, (_, c) => {
    if (c === "'") return "'";
    if (c === '\\') return '\\';
    if (c === 'n') return '\n';
    if (c === 't') return '\t';
    return c;
  });
}

/** Parse contiguous Dart string literals: 'a' 'b' → "ab" */
function parseDartStringLiteral(src, startIdx) {
  let i = startIdx;
  let value = '';
  while (i < src.length) {
    while (i < src.length && /\s/.test(src[i])) i++;
    if (src[i] !== "'") break;
    i++; // open quote
    while (i < src.length) {
      const c = src[i];
      if (c === '\\' && i + 1 < src.length) {
        const next = src[i + 1];
        if (next === "'") { value += "'"; i += 2; continue; }
        if (next === '\\') { value += '\\'; i += 2; continue; }
        if (next === 'n') { value += '\n'; i += 2; continue; }
        if (next === 't') { value += '\t'; i += 2; continue; }
        value += next; i += 2; continue;
      }
      if (c === "'") { i++; break; }
      value += c;
      i++;
    }
  }
  return { value, end: i };
}

function extractLocaleMap(src, lang) {
  const marker = `'${lang}': {`;
  const start = src.indexOf(marker);
  if (start < 0) throw new Error(`Locale block not found: ${lang}`);
  let i = start + marker.length;
  const obj = {};
  let depth = 1;
  while (i < src.length && depth > 0) {
    const c = src[i];
    if (c === '{') { depth++; i++; continue; }
    if (c === '}') { depth--; i++; continue; }
    if (c === '/' && src[i + 1] === '/') {
      while (i < src.length && src[i] !== '\n') i++;
      continue;
    }
    if (c === "'") {
      // key
      const keyParsed = parseDartStringLiteral(src, i);
      i = keyParsed.end;
      while (i < src.length && /\s/.test(src[i])) i++;
      if (src[i] !== ':') continue;
      i++; // :
      while (i < src.length && /\s/.test(src[i])) i++;
      if (src[i] !== "'") continue;
      const valParsed = parseDartStringLiteral(src, i);
      obj[keyParsed.value] = valParsed.value;
      i = valParsed.end;
      continue;
    }
    i++;
  }
  return obj;
}

const content = fs.readFileSync(dartPath, 'utf8');
if (!fs.existsSync(outDir)) fs.mkdirSync(outDir, { recursive: true });

for (const lang of ['en', 'uz', 'ru']) {
  const obj = extractLocaleMap(content, lang);
  const jsonPath = path.join(outDir, `${lang}.json`);
  fs.writeFileSync(jsonPath, JSON.stringify(obj, null, 2), 'utf8');
  console.log(`Wrote ${jsonPath} (${Object.keys(obj).length} keys)`);
  for (const k of ['more', 'calendarTimezoneMismatchHint', 'setUpClinicWorkspace', 'calendarStaffCalendars']) {
    console.log(`  ${k}: ${obj[k] ? 'OK' : 'MISSING'}`);
  }
}
