const { contextBridge, ipcRenderer } = require('electron');

/**
 * The only surface the renderer gets. Every channel is re-validated in
 * `src/main/ipc.js` against the main window's own webContents.
 */
contextBridge.exposeInMainWorld('agWidgets', {
  getState: () => ipcRenderer.invoke('agw:get-state'),
  minimizeWindow: () => ipcRenderer.invoke('agw:minimize'),
  refresh: () => ipcRenderer.invoke('agw:refresh'),
  zcodeLogin: () => ipcRenderer.invoke('agw:zcode-login'),
  zcodeCancelLogin: () => ipcRenderer.invoke('agw:zcode-cancel'),
  zcodeLogout: () => ipcRenderer.invoke('agw:zcode-logout'),
  openExternalAction: (action) => ipcRenderer.invoke('agw:open-action', action),
  quitApp: () => ipcRenderer.invoke('agw:quit'),
  showMenu: () => ipcRenderer.invoke('agw:menu'),
  moveWindow: (dx, dy) => ipcRenderer.invoke('agw:move-window', dx, dy),
  saveWindowPosition: () => ipcRenderer.invoke('agw:move-end'),
  onStateUpdate: (cb) => {
    const handler = (_event, payload) => cb(payload);
    ipcRenderer.on('agw:state', handler);
    return () => ipcRenderer.removeListener('agw:state', handler);
  },
});
