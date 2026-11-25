'use strict';

const { spawn } = require('child_process');
const { COLLECTOR_PATH, CONFIG_PATH, ROOT_DIR, STATUS_PATH } = require('./paths');
const { readJson, readActiveChat } = require('./storage');

const COLLECTOR_TIMEOUT_MS = 90_000;
const ACTIVE_PUBLISH_THROTTLE_MS = 15_000;

let running = false;
let lastActivePublishAttempt = 0;

/** Run ``collector.py --force`` with the first Python interpreter that exists. */
function runCollector(onSuccess) {
  if (running) return Promise.resolve({ ok: true, busy: true });
  running = true;

  const config = readJson(CONFIG_PATH) || {};
  const candidates = [config.python_path, process.env.PYTHON, 'python', 'py'].filter(Boolean);

  return new Promise((resolve) => {
    let index = 0;

    const tryNext = () => {
      if (index >= candidates.length) {
        running = false;
        resolve({ ok: false, error: 'Python not found' });
        return;
      }

      const python = candidates[index++];
      let settled = false;
      let stderr = '';
      const child = spawn(python, [COLLECTOR_PATH, '--force'], {
        cwd: ROOT_DIR,
        windowsHide: true,
      });

      const finish = (result) => {
        if (settled) return;
        settled = true;
        clearTimeout(timer);
        if (result.ok && onSuccess) onSuccess();
        running = false;
        resolve(result);
      };

      const timer = setTimeout(() => {
        try {
          child.kill();
        } catch {
          // The process may have already exited.
        }
        finish({ ok: false, error: 'Collector timeout' });
      }, COLLECTOR_TIMEOUT_MS);

      child.stderr.on('data', (chunk) => {
        stderr += chunk.toString();
      });

      child.on('error', (error) => {
        if (error && error.code === 'ENOENT' && !settled) {
          settled = true;
          clearTimeout(timer);
          tryNext();
          return;
        }
        finish({ ok: false, error: String((error && error.message) || error) });
      });

      child.on('close', (code) => {
        finish({ ok: code === 0, code, error: code === 0 ? '' : stderr.slice(-300) });
      });
    };

    tryNext();
  });
}

/**
 * Publish immediately when the Antigravity annotation file points at a chat
 * different from the last published summary. This keeps the phone in sync
 * within seconds of switching tabs on the desktop.
 */
function maybePublishActiveChat(onPublished) {
  if (running) return;

  const active = readActiveChat();
  if (!active || !active.id) return;

  const status = readJson(STATUS_PATH) || {};
  const publishedId = status.context_summary && status.context_summary.conversation_id;
  if (publishedId === active.id) return;
  if (Date.now() - lastActivePublishAttempt < ACTIVE_PUBLISH_THROTTLE_MS) return;

  lastActivePublishAttempt = Date.now();
  runCollector(onPublished);
}

module.exports = { runCollector, maybePublishActiveChat, isRunning: () => running };
