'use strict';

const fs = require('fs');
const path = require('path');
const { app, Menu, Tray, nativeImage } = require('electron');
const { getWindow, hideWindow, setAlwaysOnTop, showWindow } = require('./window');

const APP_ROOT = path.join(__dirname, '..', '..');
const ASSETS_DIR = path.join(APP_ROOT, 'assets');

let tray = null;

function quit() {
  app.quit();
}

function buildMenu() {
  const window = getWindow();
  const loginSettings = app.getLoginItemSettings();

  return Menu.buildFromTemplate([
    { label: 'Показать виджет', click: showWindow },
    { label: 'Свернуть в трей', click: hideWindow },
    {
      label: 'Поверх всех окон',
      type: 'checkbox',
      checked: Boolean(window && window.isAlwaysOnTop()),
      click: (item) => setAlwaysOnTop(item.checked),
    },
    {
      label: 'Запускать при входе',
      type: 'checkbox',
      checked: Boolean(loginSettings.openAtLogin),
      click: (item) => {
        app.setLoginItemSettings({
          openAtLogin: item.checked,
          path: process.execPath,
          // Dev builds need the app directory; packaged builds open themselves.
          args: app.isPackaged ? [] : [APP_ROOT],
        });
      },
    },
    { type: 'separator' },
    { label: 'Выход', click: quit },
  ]);
}

function createTray() {
  const iconPath = path.join(ASSETS_DIR, 'icon.ico');
  const icon = fs.existsSync(iconPath) ? nativeImage.createFromPath(iconPath) : nativeImage.createEmpty();
  tray = new Tray(icon);
  tray.setToolTip('AG Widgets');

  const refreshMenu = () => tray.setContextMenu(buildMenu());
  refreshMenu();
  tray.on('click', showWindow);
  tray.on('right-click', refreshMenu);
}

/** Context menu shown by the widget's title-bar button and right click. */
function showContextMenu() {
  const window = getWindow();
  if (!window) return;
  Menu.buildFromTemplate([
    { label: 'Свернуть в трей', click: hideWindow },
    {
      label: 'Поверх всех окон',
      type: 'checkbox',
      checked: window.isAlwaysOnTop(),
      click: (item) => setAlwaysOnTop(item.checked),
    },
    { type: 'separator' },
    { label: 'Выход', click: quit },
  ]).popup({ window });
}

module.exports = { createTray, showContextMenu };
