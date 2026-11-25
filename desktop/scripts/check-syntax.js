'use strict';

/**
 * Syntax-check every JavaScript file under desktop/ with `node --check`.
 * Uses only the Node standard library, so `npm run check` works without
 * installing dependencies.
 */

const { spawnSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const SKIP_DIRS = new Set(['node_modules', 'assets']);

function collect(dir, files) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    if (entry.name.startsWith('.') || SKIP_DIRS.has(entry.name)) continue;
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      collect(fullPath, files);
    } else if (entry.isFile() && entry.name.endsWith('.js')) {
      files.push(fullPath);
    }
  }
}

const files = [];
collect(ROOT, files);
files.sort();

let failed = 0;
for (const file of files) {
  const result = spawnSync(process.execPath, ['--check', file], { stdio: 'inherit' });
  if (result.status !== 0) {
    failed += 1;
    console.error(`Syntax error: ${path.relative(ROOT, file)}`);
  }
}

console.log(`Checked ${files.length} file(s), ${failed} failed.`);
process.exit(failed === 0 ? 0 : 1);
