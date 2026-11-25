'use strict';

const { app, ipcMain, shell } = require('electron');
const { APP_DIR } = require('./paths');
const { readAllState } = require('./storage');
const collector = require('./collector');
const zcodeAuth = require('./zcode-auth');
const windowApi = require('./window');
const { refreshMenu, showContextMenu } = require('./tray');

const ZCODE_WEB_URL = 'https://zcode.z.ai/';

/** Fixed enum of external targets; the renderer can never pass a raw path or URL. */
const EXTERNAL_ACTIONS = {
  zcode: () => shell.openExternal(ZCODE_WEB_URL),
  'config-dir': () => shell.openPath(APP_DIR),
};

/** Reject IPC that does not originate from this app's own main window. */
function fromMainWindow(handler) {
  return (event, ...args) => {
    const window = windowApi.getWindow();
    if (!window || event.sender !== window.webContents) {
      return { ok: false, error: 'Untrusted IPC sender' };
    }
    return handler(event, ...args);
  };
}

/** Register every renderer -> main channel. */
function registerIpc() {
  ipcMain.handle('agw:get-state', fromMainWindow(() => readAllState()));

  ipcMain.handle('agw:minimize', fromMainWindow(() => windowApi.minimizeWindow()));

  ipcMain.handle(
    'agw:refresh',
    fromMainWindow(async () => {
      const result = await collector.runCollector({ force: true });
      windowApi.pushState(readAllState());
      return result;
    }),
  );

  ipcMain.handle(
    'agw:zcode-login',
    fromMainWindow(async () => {
      const result = await zcodeAuth.zcodeLogin();
      windowApi.pushState(readAllState());
      refreshMenu();
      return result;
    }),
  );
  ipcMain.handle('agw:zcode-cancel', fromMainWindow(() => zcodeAuth.cancelZCodeLogin()));
  ipcMain.handle(
    'agw:zcode-logout',
    fromMainWindow(async () => {
      const result = await zcodeAuth.zcodeLogout();
      windowApi.pushState(readAllState());
      refreshMenu();
      return result;
    }),
  );

  ipcMain.handle('agw:quit', fromMainWindow(() => app.quit()));
  ipcMain.handle('agw:menu', fromMainWindow(() => showContextMenu()));

  ipcMain.handle(
    'agw:move-window',
    fromMainWindow((_event, dx, dy) => {
      if (!Number.isFinite(dx) || !Number.isFinite(dy)) {
        return { ok: false, error: 'Invalid offset' };
      }
      windowApi.moveWindow(dx, dy);
      return { ok: true };
    }),
  );

  ipcMain.handle('agw:move-end', fromMainWindow(() => windowApi.saveCurrentWindowBounds()));

  ipcMain.handle(
    'agw:open-action',
    fromMainWindow((_event, action) => {
      const handler =
        typeof action === 'string' && Object.prototype.hasOwnProperty.call(EXTERNAL_ACTIONS, action)
          ? EXTERNAL_ACTIONS[action]
          : null;
      if (!handler) return { ok: false, error: 'Unsupported action' };
      handler();
      return { ok: true };
    }),
  );
}

module.exports = { registerIpc, EXTERNAL_ACTIONS };
