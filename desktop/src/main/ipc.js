'use strict';

const { app, ipcMain } = require('electron');
const { readAllState } = require('./storage');
const { runCollector } = require('./collector');
const { zcodeLogin } = require('./zcode-auth');
const {
  hideWindow,
  minimizeWindow,
  moveWindow,
  pushState,
} = require('./window');
const { showContextMenu } = require('./tray');

/** Register every renderer -> main channel. */
function registerIpc() {
  ipcMain.handle('agw:get-state', () => readAllState());

  ipcMain.handle('agw:hide-window', () => hideWindow());
  ipcMain.handle('agw:minimize', () => minimizeWindow());

  ipcMain.handle('agw:refresh', () => runCollector(() => pushState(readAllState())));
  ipcMain.handle('agw:zcode-login', () => zcodeLogin());
  ipcMain.handle('agw:quit', () => app.quit());
  ipcMain.handle('agw:move-window', (_event, dx, dy) => moveWindow(dx, dy));
  ipcMain.handle('agw:menu', () => showContextMenu());
}

module.exports = { registerIpc };
