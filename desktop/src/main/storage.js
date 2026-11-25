'use strict';

const fs = require('fs');
const path = require('path');
const {
  ANNOTATIONS_DIR,
  CONTEXT_PATH,
  STATE_DIR,
  STATUS_PATH,
} = require('./paths');

/** Read and parse a JSON file; returns null when missing or malformed. */
function readJson(filePath) {
  try {
    if (!fs.existsSync(filePath)) return null;
    const raw = fs.readFileSync(filePath, 'utf8').trim();
    return raw ? JSON.parse(raw) : null;
  } catch {
    return null;
  }
}

/** Write JSON atomically so readers never observe a partially written file. */
function writeJsonAtomic(filePath, data) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  const tmp = `${filePath}.${process.pid}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(data, null, 2), 'utf8');
  fs.renameSync(tmp, filePath);
}

/**
 * The newest Antigravity annotation file corresponds to the chat currently
 * open in the app. Only ids and titles are exposed; message text never leaves
 * Antigravity.
 */
function readActiveChat() {
  try {
    const entries = fs
      .readdirSync(ANNOTATIONS_DIR, { withFileTypes: true })
      .filter((entry) => entry.isFile() && entry.name.endsWith('.pbtxt'));

    let latest = null;
    for (const entry of entries) {
      const filePath = path.join(ANNOTATIONS_DIR, entry.name);
      const mtimeMs = fs.statSync(filePath).mtimeMs;
      if (!latest || mtimeMs > latest.mtimeMs) {
        latest = { filePath, name: entry.name, mtimeMs };
      }
    }
    if (!latest) return null;

    const text = fs.readFileSync(latest.filePath, 'utf8');
    const title = text.match(/title\s*:\s*"([^"]*)"/);
    const viewedAt = text.match(/last_user_view_time\s*:\s*\{\s*seconds\s*:\s*(\d+)/);
    return {
      id: latest.name.replace(/\.pbtxt$/, ''),
      title: title ? title[1] : '',
      viewedAt: viewedAt ? Number(viewedAt[1]) : Math.round(latest.mtimeMs / 1000),
    };
  } catch {
    return null;
  }
}

/** Snapshot consumed by the renderer. */
function readAllState() {
  return {
    context: readJson(CONTEXT_PATH),
    status: readJson(STATUS_PATH),
    activeChat: readActiveChat(),
    now: Date.now(),
  };
}

/**
 * Watch the state directory and Antigravity annotations.
 * `onStateChange` is debounced; `onAnnotationsChange` fires for chat switches.
 */
function startWatchers(onStateChange, onAnnotationsChange) {
  let timer = null;
  const schedule = () => {
    clearTimeout(timer);
    timer = setTimeout(onStateChange, 120);
  };

  try {
    fs.mkdirSync(STATE_DIR, { recursive: true });
    fs.watch(STATE_DIR, { persistent: false }, (_event, filename) => {
      if (!filename || filename === 'context.json' || filename === 'status.json') schedule();
    });
  } catch {
    // The polling interval below keeps the UI updated even if fs.watch fails.
  }

  try {
    fs.watch(ANNOTATIONS_DIR, { persistent: false }, () => {
      schedule();
      if (onAnnotationsChange) onAnnotationsChange();
    });
  } catch {
    // Antigravity may not be installed; the rest of the widget still works.
  }

  setInterval(onStateChange, 5000);
}

module.exports = { readJson, writeJsonAtomic, readActiveChat, readAllState, startWatchers };
