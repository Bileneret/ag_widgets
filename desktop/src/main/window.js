'use strict';

const path = require('path');
const { app, BrowserWindow, screen } = require('electron');
const { WINDOW_STATE_PATH } = require('./paths');
const { readAllState, readJson, writeJsonAtomic } = require('./storage');

const WINDOW_WIDTH = 400;
const WINDOW_HEIGHT = 640;
const ASSETS_DIR = path.join(__dirname, '..', '..', 'assets');

let mainWindow = null;

function loadWindowState() {
  const saved = readJson(WINDOW_STATE_PATH);
  if (!saved) return { x: undefined, y: undefined, alwaysOnTop: true };
  return {
    x: typeof saved.x === 'number' ? saved.x : undefined,
    y: typeof saved.y === 'number' ? saved.y : undefined,
    alwaysOnTop: typeof saved.alwaysOnTop === 'boolean' ? saved.alwaysOnTop : true,
  };
}

function saveWindowState(extra = {}) {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  const previous = loadWindowState();
  const [x, y] = mainWindow.getPosition();
  writeJsonAtomic(WINDOW_STATE_PATH, {
    ...previous,
    ...extra,
    x,
    y,
    alwaysOnTop: mainWindow.isAlwaysOnTop(),
  });
}

function isPositionOnAnyDisplay(x, y, width, height) {
  if (typeof x !== 'number' || typeof y !== 'number') return false;
  return screen.getAllDisplays().some((display) => {
    const area = display.workArea;
    return (
      x + width > area.x + 40 &&
      x < area.x + area.width - 40 &&
      y + height > area.y + 40 &&
      y < area.y + area.height - 40
    );
  });
}

/** Keep a restored window fully inside a display after monitor changes. */
function clampToWorkArea(x, y, width, height) {
  const areas = screen.getAllDisplays().map((display) => display.workArea);
  const primary = screen.getPrimaryDisplay().workArea;
  const target =
    areas.find(
      (area) =>
        x + width > area.x + 40 &&
        x < area.x + area.width - 40 &&
        y + height > area.y + 40 &&
        y < area.y + area.height - 40,
    ) || primary;
  return {
    x: Math.max(target.x, Math.min(x, target.x + target.width - width)),
    y: Math.max(target.y, Math.min(y, target.y + target.height - height)),
  };
}

function pushState(state) {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  mainWindow.webContents.send('agw:state', state || readAllState());
}

function createWindow() {
  const saved = loadWindowState();
  const display = screen.getPrimaryDisplay().workArea;
  const defaultX = display.x + display.width - WINDOW_WIDTH - 24;
  const defaultY = display.y + 24;
  const position = isPositionOnAnyDisplay(saved.x, saved.y, WINDOW_WIDTH, WINDOW_HEIGHT)
    ? clampToWorkArea(saved.x, saved.y, WINDOW_WIDTH, WINDOW_HEIGHT)
    : { x: defaultX, y: defaultY };

  mainWindow = new BrowserWindow({
    width: WINDOW_WIDTH,
    height: WINDOW_HEIGHT,
    x: position.x,
    y: position.y,
    frame: false,
    title: 'AG Widgets',
    resizable: false,
    minimizable: true,
    maximizable: false,
    fullscreenable: false,
    icon: path.join(ASSETS_DIR, 'icon-256.png'),
    alwaysOnTop: saved.alwaysOnTop,
    hasShadow: true,
    transparent: false,
    backgroundColor: '#00000000',
    backgroundMaterial: 'acrylic',
    show: false,
    webPreferences: {
      preload: path.join(__dirname, '..', '..', 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });

  if (saved.alwaysOnTop) mainWindow.setAlwaysOnTop(true, 'floating');

  // The widget is a fixed-size surface: dragging or a DPI change must not resize it.
  mainWindow.setMinimumSize(WINDOW_WIDTH, WINDOW_HEIGHT);
  mainWindow.setMaximumSize(WINDOW_WIDTH, WINDOW_HEIGHT);
  let sizeGuard = false;
  const enforceSize = () => {
    if (!mainWindow || mainWindow.isDestroyed() || sizeGuard) return;
    const [width, height] = mainWindow.getSize();
    if (width === WINDOW_WIDTH && height === WINDOW_HEIGHT) return;
    sizeGuard = true;
    mainWindow.setSize(WINDOW_WIDTH, WINDOW_HEIGHT, false);
    setTimeout(() => {
      sizeGuard = false;
    }, 60);
  };
  mainWindow.on('resize', enforceSize);
  mainWindow.on('maximize', () => mainWindow.unmaximize());
  mainWindow.on('enter-full-screen', () => mainWindow.setFullScreen(false));

  mainWindow.once('ready-to-show', () => {
    mainWindow.show();
    pushState(readAllState());
  });

  let moveSaveTimer = null;
  mainWindow.on('moved', () => {
    clearTimeout(moveSaveTimer);
    moveSaveTimer = setTimeout(() => saveWindowState(), 250);
  });

  // A frozen or crashed renderer must never leave an unclosable frame behind.
  mainWindow.on('unresponsive', () => app.quit());
  mainWindow.webContents.on('render-process-gone', () => app.quit());

  mainWindow.loadFile(path.join(__dirname, '..', '..', 'index.html'));
  return mainWindow;
}

function getWindow() {
  return mainWindow && !mainWindow.isDestroyed() ? mainWindow : null;
}

function showWindow() {
  const window = getWindow();
  if (!window) return;
  if (window.isMinimized()) window.restore();
  window.show();
  window.focus();
}

function hideWindow() {
  const window = getWindow();
  if (window) window.hide();
}

function minimizeWindow() {
  const window = getWindow();
  if (window) window.minimize();
}

function setAlwaysOnTop(value) {
  const window = getWindow();
  if (!window) return;
  window.setAlwaysOnTop(Boolean(value), 'floating');
  saveWindowState({ alwaysOnTop: Boolean(value) });
}

function moveWindow(dx, dy) {
  const window = getWindow();
  if (!window) return;
  const bounds = window.getBounds();
  const next = {
    x: Math.round(bounds.x + dx),
    y: Math.round(bounds.y + dy),
    width: WINDOW_WIDTH,
    height: WINDOW_HEIGHT,
  };
  const area = screen.getDisplayMatching(next).workArea;
  next.x = Math.max(area.x, Math.min(next.x, area.x + area.width - WINDOW_WIDTH));
  next.y = Math.max(area.y, Math.min(next.y, area.y + area.height - WINDOW_HEIGHT));
  window.setBounds(next, false);
}

module.exports = {
  createWindow,
  getWindow,
  showWindow,
  hideWindow,
  minimizeWindow,
  setAlwaysOnTop,
  moveWindow,
  pushState,
  saveWindowState,
};
