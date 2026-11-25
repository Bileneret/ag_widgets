'use strict';

const path = require('path');
const { app, BrowserWindow, screen, session } = require('electron');
const { windowStatePath } = require('./paths');
const { readAllState, readJson, writeJsonAtomic } = require('./storage');
const { logError } = require('./logger');

const WINDOW_WIDTH = 400;
const WINDOW_HEIGHT = 640;
/** Minimum slice of the title bar that must stay on screen while dragging. */
const TITLEBAR_GRAB = 48;
const ASSETS_DIR = path.join(__dirname, '..', '..', 'assets');

let mainWindow = null;
let moveSaveTimer = null;

function loadWindowState() {
  const saved = readJson(windowStatePath());
  if (!saved) return { x: undefined, y: undefined, alwaysOnTop: true };
  return {
    x: typeof saved.x === 'number' ? saved.x : undefined,
    y: typeof saved.y === 'number' ? saved.y : undefined,
    alwaysOnTop: typeof saved.alwaysOnTop === 'boolean' ? saved.alwaysOnTop : true,
  };
}

/** Persist window state; filesystem hiccups must never take the app down. */
function saveWindowState(extra = {}) {
  const window = getWindow();
  if (!window) return;
  try {
    const previous = loadWindowState();
    const [x, y] = window.getPosition();
    writeJsonAtomic(windowStatePath(), {
      ...previous,
      ...extra,
      x,
      y,
      alwaysOnTop: window.isAlwaysOnTop(),
    });
  } catch (error) {
    logError('window-state', error);
  }
}

function clearPendingMoveSave() {
  if (!moveSaveTimer) return;
  clearTimeout(moveSaveTimer);
  moveSaveTimer = null;
}

/** Persist the current bounds immediately, skipping the `moved` debounce. */
function saveCurrentWindowBounds() {
  clearPendingMoveSave();
  saveWindowState();
}

/** Idempotent flush for `before-quit`; safe when the window is already gone. */
function flushWindowState() {
  saveCurrentWindowBounds();
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

  // Hardening: the widget never navigates away, opens no child windows and
  // grants no permissions to web content.
  mainWindow.webContents.on('will-navigate', (event) => event.preventDefault());
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) =>
    callback(false),
  );
  session.defaultSession.setPermissionCheckHandler(() => false);

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
  });

  // Re-read state the moment the widget becomes visible again (tray restore).
  mainWindow.on('show', () => pushState(readAllState()));

  mainWindow.on('moved', () => {
    clearTimeout(moveSaveTimer);
    moveSaveTimer = setTimeout(() => {
      moveSaveTimer = null;
      saveWindowState();
    }, 250);
  });

  // Closing the window (× / Alt+F4 / OS close) quits; use the minimize button
  // or the tray menu to keep the process alive.

  // A frozen renderer must not leave an unusable frame forever: give it 15s to
  // recover before quitting, and cancel the timer on `responsive`.
  let unresponsiveTimer = null;
  mainWindow.on('unresponsive', () => {
    if (unresponsiveTimer) return;
    unresponsiveTimer = setTimeout(() => {
      logError('unresponsive', new Error('Renderer unresponsive for 15s'));
      app.quit();
    }, 15000);
  });
  mainWindow.on('responsive', () => {
    clearTimeout(unresponsiveTimer);
    unresponsiveTimer = null;
  });
  mainWindow.webContents.on('render-process-gone', (_event, details) => {
    const reason = details && details.reason ? details.reason : 'unknown';
    logError('render-process-gone', new Error(`Renderer process gone: ${reason}`));
    app.quit();
  });

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

  // Clamp against the union of every display's work area instead of snapping to
  // the current display, so the widget can cross monitors freely.
  const areas = screen.getAllDisplays().map((display) => display.workArea);
  if (!areas.length) return;
  const left = Math.min(...areas.map((area) => area.x));
  const top = Math.min(...areas.map((area) => area.y));
  const right = Math.max(...areas.map((area) => area.x + area.width));
  const bottom = Math.max(...areas.map((area) => area.y + area.height));

  // Keep TITLEBAR_GRAB pixels of the title bar visible horizontally and the
  // whole title strip below the desktop top edge; on displays shorter than the
  // window the top edge is never pushed off screen.
  next.x = Math.max(left - WINDOW_WIDTH + TITLEBAR_GRAB, Math.min(next.x, right - TITLEBAR_GRAB));
  next.y = Math.max(top, Math.min(next.y, bottom - TITLEBAR_GRAB));
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
  saveCurrentWindowBounds,
  flushWindowState,
};
