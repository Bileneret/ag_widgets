'use strict';

const { app } = require('electron');
const { readAllState, startWatchers } = require('./src/main/storage');
const { maybePublishActiveChat } = require('./src/main/collector');
const { registerIpc } = require('./src/main/ipc');
const { createTray } = require('./src/main/tray');
const { createWindow, pushState, showWindow } = require('./src/main/window');

app.setAppUserModelId('dev.agwidgets');

const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
  app.quit();
} else {
  app.on('second-instance', showWindow);

  app.whenReady().then(() => {
    registerIpc();
    createWindow();
    createTray();

    const refreshRenderer = () => pushState(readAllState());
    startWatchers(
      refreshRenderer,
      () => maybePublishActiveChat(refreshRenderer),
    );
  });
}
