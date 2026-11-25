'use strict';

const path = require('path');
const os = require('os');
const { app } = require('electron');

const LOCAL_APP_DATA = process.env.LOCALAPPDATA || path.join(app.getPath('home'), 'AppData', 'Local');

/** Project root (the directory containing collector/, desktop/, and hook/). */
const ROOT_DIR = path.join(__dirname, '..', '..', '..');
const APP_DIR = path.join(LOCAL_APP_DATA, 'ag-widgets');
const STATE_DIR = path.join(APP_DIR, 'state');
const CONFIG_PATH = path.join(APP_DIR, 'config.json');
const SECRETS_PATH = path.join(APP_DIR, 'secrets.json');
const CONTEXT_PATH = path.join(STATE_DIR, 'context.json');
const STATUS_PATH = path.join(STATE_DIR, 'status.json');
const WINDOW_STATE_PATH = path.join(app.getPath('userData'), 'window-state.json');
const ANNOTATIONS_DIR = path.join(os.homedir(), '.gemini', 'antigravity', 'annotations');
const COLLECTOR_PATH = path.join(ROOT_DIR, 'collector', 'collector.py');

module.exports = {
  ROOT_DIR,
  STATE_DIR,
  CONFIG_PATH,
  SECRETS_PATH,
  CONTEXT_PATH,
  STATUS_PATH,
  WINDOW_STATE_PATH,
  ANNOTATIONS_DIR,
  COLLECTOR_PATH,
};
