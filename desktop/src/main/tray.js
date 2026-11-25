'use strict';

const fs = require('fs');
const path = require('path');
const { app, Menu, Tray, nativeImage, shell } = require('electron');
const { APP_DIR, STATUS_PATH } = require('./paths');
const { readAllState, readJson } = require('./storage');
const zcodeAuth = require('./zcode-auth');
const { getWindow, hideWindow, pushState, setAlwaysOnTop, showWindow } = require('./window');

const APP_ROOT = path.join(__dirname, '..', '..');
const ASSETS_DIR = path.join(APP_ROOT, 'assets');
const ZCODE_WEB_URL = 'https://zcode.z.ai/';

let tray = null;

/** Autostart registration; reads must use the same path/args as writes. */
function loginItemOptions() {
  return {
    path: process.execPath,
    // Dev builds need the app directory; packaged builds open themselves.
    args: app.isPackaged ? [] : [APP_ROOT],
  };
}

function quit() {
  app.quit();
}

function refreshMenu() {
  if (!tray || tray.isDestroyed()) return;
  tray.setContextMenu(buildMenu());
}

/** ZCode counts as connected only for a verified API session. */
function isZCodeConnected() {
  const status = readJson(STATUS_PATH);
  const zcode = status && status.zcode;
  return Boolean(zcode && zcode.method === 'api' && zcode.auth_ok !== false);
}

function toggleAlwaysOnTop(checked) {
  setAlwaysOnTop(checked);
  // The tray owns its own Menu instance; rebuilding it keeps the checkboxes in
  // sync even when the toggle came from the popup context menu.
  refreshMenu();
}

/** Single source of menu items for both the tray and the popup menu. */
function buildMenu() {
  const window = getWindow();
  const loginSettings = app.getLoginItemSettings(loginItemOptions());

  const template = [
    { label: 'Показать виджет', click: showWindow },
    { label: 'Свернуть в трей', click: hideWindow },
    {
      label: 'Поверх всех окон',
      type: 'checkbox',
      checked: Boolean(window && window.isAlwaysOnTop()),
      click: (item) => toggleAlwaysOnTop(item.checked),
    },
    {
      label: 'Запускать при входе',
      type: 'checkbox',
      checked: Boolean(loginSettings.openAtLogin),
      click: (item) => {
        app.setLoginItemSettings({ openAtLogin: item.checked, ...loginItemOptions() });
        refreshMenu();
      },
    },
    { type: 'separator' },
    { label: 'Открыть папку конфига', click: () => shell.openPath(APP_DIR) },
    { label: 'Открыть Z.ai', click: () => shell.openExternal(ZCODE_WEB_URL) },
  ];

  if (isZCodeConnected()) {
    template.push({
      label: 'Выйти из Z.ai',
      click: () => {
        Promise.resolve()
          .then(() => zcodeAuth.zcodeLogout())
          .then(() => {
            pushState(readAllState());
            refreshMenu();
          })
          .catch(() => {});
      },
    });
  }

  template.push({ type: 'separator' }, { label: 'Выход', click: quit });
  return Menu.buildFromTemplate(template);
}

function createTray() {
  const iconPath = path.join(ASSETS_DIR, 'icon.ico');
  const icon = fs.existsSync(iconPath) ? nativeImage.createFromPath(iconPath) : nativeImage.createEmpty();
  tray = new Tray(icon);
  tray.setToolTip('AG Widgets');

  refreshMenu();
  tray.on('click', () => {
    const window = getWindow();
    if (window && window.isVisible() && !window.isMinimized()) hideWindow();
    else showWindow();
  });
  tray.on('right-click', () => refreshMenu());
}

/** Context menu shown by the widget's title-bar button and right click. */
function showContextMenu() {
  const window = getWindow();
  if (!window) return;
  buildMenu().popup({ window });
}

module.exports = { createTray, showContextMenu, refreshMenu };
