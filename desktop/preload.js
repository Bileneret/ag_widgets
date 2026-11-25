const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('agWidgets', {
  getState: () => ipcRenderer.invoke('agw:get-state'),
  hideWindow: () => ipcRenderer.invoke('agw:hide-window'),
  minimizeWindow: () => ipcRenderer.invoke('agw:minimize'),
  refresh: () => ipcRenderer.invoke('agw:refresh'),
  zcodeLogin: () => ipcRenderer.invoke('agw:zcode-login'),
  quitApp: () => ipcRenderer.invoke('agw:quit'),
  showMenu: () => ipcRenderer.invoke('agw:menu'),
  moveWindow: (dx, dy) => ipcRenderer.invoke('agw:move-window', dx, dy),
  onStateUpdate: (cb) => {
    const handler = (_event, payload) => cb(payload);
    ipcRenderer.on('agw:state', handler);
    return () => ipcRenderer.removeListener('agw:state', handler);
  },
});
