'use strict';

const fs = require('fs');
const path = require('path');
const { STATE_DIR } = require('./paths');

const ERROR_LOG_PATH = path.join(STATE_DIR, 'desktop_errors.log');
const ERROR_LOG_MAX_BYTES = 256 * 1024;

/** Keep the error log bounded: rotate to `.1` once it grows past 256 KiB. */
function rotate() {
  try {
    if (fs.statSync(ERROR_LOG_PATH).size <= ERROR_LOG_MAX_BYTES) return;
  } catch {
    return; // No log yet.
  }
  try {
    fs.rmSync(`${ERROR_LOG_PATH}.1`, { force: true });
  } catch {
    // A locked previous log is not worth crashing over.
  }
  try {
    fs.renameSync(ERROR_LOG_PATH, `${ERROR_LOG_PATH}.1`);
  } catch {
    // If rotation fails, append instead of losing the entry.
  }
}

/**
 * Append a diagnostic entry. Logging is a best-effort aid: it must never take
 * the widget down, so every filesystem step is guarded.
 */
function logError(kind, error) {
  try {
    fs.mkdirSync(STATE_DIR, { recursive: true });
    rotate();
    const detail = error && error.stack ? error.stack : String(error);
    const line = `[${new Date().toISOString()}] ${kind}: ${detail}\n`;
    fs.appendFileSync(ERROR_LOG_PATH, line, 'utf8');
  } catch {
    // Diagnostics are optional.
  }
}

module.exports = { logError, ERROR_LOG_PATH };
