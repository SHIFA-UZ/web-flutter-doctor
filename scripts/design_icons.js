// Generates the icon drawables the Kotlin code references, from the design's icon library.
//   node scripts/design_icons.js
// Source: the `ic` map in docs/design/boards/DaApp.dc.html (24-unit stroke icons, Lucide style).
// For every `Res.drawable.ic_<name>_<size>` in composeApp/src, writes composeResources/drawable/ic_<name>_<size>.xml:
// a <size> dp vector of icon <name> (camelCase in the map, snake_case here). `<name>_filled` fills instead of stroking, `<name>_bold` strokes at 2.5 (selected tabs).
// Re-run after using a new icon or size; it deletes the generated files nothing references any more.
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const html = fs.readFileSync(path.join(root, 'docs/design/boards/DaApp.dc.html'), 'utf8');
const start = html.indexOf('    const C = (cx, cy, r)');
const end = html.indexOf('    };', html.indexOf('    const ic = {')) + 6;
const ic = new Function(html.slice(start, end) + '; return ic;')();
const snake = (s) => s.replace(/([A-Z])/g, '_$1').toLowerCase();
const bySnake = Object.fromEntries(Object.entries(ic).map(([k, v]) => [snake(k), v]));
const out = path.join(root, 'composeApp/src/commonMain/composeResources/drawable');
const marker = 'docs/design icon';

const used = new Set();
const walk = (dir) => {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p);
    else if (e.name.endsWith('.kt')) for (const m of fs.readFileSync(p, 'utf8').matchAll(/Res\.drawable\.(ic_[a-z0-9_]+?_\d+)\b/g)) used.add(m[1]);
  }
};
walk(path.join(root, 'composeApp/src'));

const missing = [];
for (const id of used) {
  const m = id.match(/^ic_(.+)_(\d+)$/);
  const filled = m[1].endsWith('_filled');
  const bold = m[1].endsWith('_bold');
  const name = filled ? m[1].slice(0, -7) : bold ? m[1].slice(0, -5) : m[1];
  const d = bySnake[name];
  if (!d) { missing.push(id); continue; }
  const paint = filled
    ? 'android:fillColor="#FF000000"'
    : `android:strokeWidth="${bold ? '2.5' : '2'}"\n        android:strokeColor="#FF000000"\n        android:strokeLineCap="round"\n        android:strokeLineJoin="round"`;
  fs.writeFileSync(path.join(out, id + '.xml'), `<?xml version="1.0" encoding="utf-8"?>
<!-- ${marker} "${name}" at ${m[2]} dp (scripts/design_icons.js). Tinted by the caller. Generated, do not edit. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="${m[2]}dp"
    android:height="${m[2]}dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="${d}"
        ${paint} />
</vector>
`);
}
for (const f of fs.readdirSync(out)) {
  if (f.startsWith('ic_') && !used.has(f.slice(0, -4)) && fs.readFileSync(path.join(out, f), 'utf8').includes(marker)) fs.unlinkSync(path.join(out, f));
}
console.log(`${used.size - missing.length} icons written` + (missing.length ? `; not in the design library: ${missing.join(', ')}` : ''));
if (missing.length) process.exitCode = 1;
