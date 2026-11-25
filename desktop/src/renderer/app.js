'use strict';

/**
 * Renderer bootstrap: theme, window controls, dragging, refresh, and ZCode login.
 * All view modules attach to `window.AGW`.
 */
(function (AGW) {
  const params = new URLSearchParams(location.search);
  const previewName = params.get('preview');
  const THEME_KEY = 'agw-theme';

  let latestState = { context: null, status: null, activeChat: null };

  function applyTheme(theme, persist) {
    document.documentElement.dataset.theme = theme;
    if (persist) {
      try {
        localStorage.setItem(THEME_KEY, theme);
      } catch {
        // Private mode or a restricted profile: keep the in-memory theme.
      }
    }
  }

  function storedTheme() {
    try {
      return localStorage.getItem(THEME_KEY);
    } catch {
      return null;
    }
  }

  applyTheme(params.get('theme') || storedTheme() || 'dark', false);

  document.getElementById('theme-btn').addEventListener('click', () => {
    const next = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
    applyTheme(next, true);
  });

  const bridge = window.agWidgets || (previewName ? AGW.preview.bridge(previewName) : null);

  document.getElementById('menu-btn').addEventListener('click', () => {
    if (bridge && typeof bridge.showMenu === 'function') bridge.showMenu();
  });
  document.getElementById('min-btn').addEventListener('click', () => {
    if (!bridge) return;
    if (typeof bridge.minimizeWindow === 'function') bridge.minimizeWindow();
    else if (typeof bridge.hideWindow === 'function') bridge.hideWindow();
  });
  document.getElementById('quit-btn').addEventListener('click', () => {
    if (bridge && typeof bridge.quitApp === 'function') bridge.quitApp();
    else window.close();
  });
  window.addEventListener('contextmenu', (event) => {
    event.preventDefault();
    if (bridge && typeof bridge.showMenu === 'function') bridge.showMenu();
  });

  // Frameless dragging through pointer events. CSS app-region:drag would swallow
  // real clicks on the buttons in Electron, so the title bar drives IPC instead.
  const titlebar = document.querySelector('.titlebar');
  let dragPoint = null;

  titlebar.addEventListener('pointerdown', (event) => {
    if (event.button !== 0 || event.target.closest('button')) return;
    dragPoint = { x: event.screenX, y: event.screenY };
    titlebar.setPointerCapture(event.pointerId);
    event.preventDefault();
  });
  titlebar.addEventListener('pointermove', (event) => {
    if (!dragPoint) return;
    const dx = event.screenX - dragPoint.x;
    const dy = event.screenY - dragPoint.y;
    if (!dx && !dy) return;
    dragPoint = { x: event.screenX, y: event.screenY };
    if (bridge && typeof bridge.moveWindow === 'function') bridge.moveWindow(dx, dy);
  });
  const stopDrag = (event) => {
    if (!dragPoint) return;
    dragPoint = null;
    try {
      titlebar.releasePointerCapture(event.pointerId);
    } catch {
      // The pointer may already be released.
    }
  };
  titlebar.addEventListener('pointerup', stopDrag);
  titlebar.addEventListener('pointercancel', stopDrag);

  function updateClock() {
    const element = document.getElementById('updated-time');
    if (element) {
      element.textContent = new Date().toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    }
  }

  function renderHeader(state) {
    const timeEl = document.getElementById('updated-time');
    const dot = document.getElementById('live-dot');
    const status = state.status;
    const current = AGW.context.currentConversation(state.context, state.activeChat);
    const timestamp = (current && current.updated_at) || (status && status.updated_at);
    updateClock();

    if (!timestamp) {
      timeEl.title = 'Нет данных о последнем ответе';
      dot.className = 'live-dot off';
      return;
    }

    const date = new Date(timestamp);
    if (Number.isNaN(date.getTime())) {
      timeEl.title = 'Нет данных о последнем ответе';
      dot.className = 'live-dot off';
      return;
    }

    timeEl.title = `Последнее обновление: ${date.toLocaleString('ru-RU')}`;
    const ageMinutes = (Date.now() - date.getTime()) / 60000;
    dot.className = ageMinutes > 120 ? 'live-dot off' : ageMinutes > 15 ? 'live-dot stale' : 'live-dot';
  }

  function render(state) {
    latestState = state || { context: null, status: null, activeChat: null };
    renderHeader(latestState);
    AGW.context.render(latestState.context, latestState.status, latestState.activeChat);
    AGW.quotas.render(latestState.status);
    AGW.zcode.render(latestState.status);
  }

  const refreshButton = document.getElementById('refresh-btn');
  let refreshing = false;

  async function refreshNow() {
    if (refreshing) return;
    refreshing = true;
    refreshButton.classList.add('refreshing');
    refreshButton.title = 'Обновляю данные…';
    try {
      if (bridge && typeof bridge.refresh === 'function') {
        const result = await bridge.refresh();
        if (!result || result.ok === false) throw new Error((result && result.error) || 'collector failed');
        const state = await bridge.getState();
        if (state) render(state);
      }
    } catch (error) {
      refreshButton.classList.add('error');
      refreshButton.title = `Не удалось обновить: ${error.message}`;
      setTimeout(() => {
        refreshButton.classList.remove('error');
        refreshButton.title = 'Обновить данные (F5)';
      }, 3000);
    } finally {
      refreshing = false;
      refreshButton.classList.remove('refreshing');
      refreshButton.title = 'Обновить данные (F5)';
    }
  }

  refreshButton.addEventListener('click', refreshNow);

  const loginButton = document.getElementById('zcode-login');
  loginButton.addEventListener('click', async () => {
    if (AGW.zcode.getLogin().state === 'pending') return;
    AGW.zcode.setLogin('pending');
    AGW.zcode.render(latestState.status);

    try {
      if (!bridge || typeof bridge.zcodeLogin !== 'function') throw new Error('Вход недоступен');
      const result = await bridge.zcodeLogin();
      if (!result || !result.ok) throw new Error((result && result.error) || 'вход не выполнен');

      AGW.zcode.setLogin(
        'success',
        result.refreshed ? 'Вход выполнен · обновляю баланс…' : 'Токен сохранён · нажмите ⟳ для обновления',
      );
      AGW.zcode.render(latestState.status);

      const state = await bridge.getState();
      if (state) render(state);

      setTimeout(() => {
        if (AGW.zcode.getLogin().state === 'success') {
          AGW.zcode.setLogin('idle');
          AGW.zcode.render(latestState.status);
        }
      }, 4500);
    } catch (error) {
      AGW.zcode.setLogin('error', String(error.message || error).slice(0, 90));
      AGW.zcode.render(latestState.status);
      setTimeout(() => {
        if (AGW.zcode.getLogin().state === 'error') {
          AGW.zcode.setLogin('idle');
          AGW.zcode.render(latestState.status);
        }
      }, 6000);
    }
  });

  window.addEventListener('keydown', (event) => {
    if (event.key === 'F5') {
      event.preventDefault();
      refreshNow();
    }
  });

  setInterval(updateClock, 1000);

  if (bridge) {
    bridge.onStateUpdate(render);
    bridge.getState().then(render).catch(() => render({ context: null, status: null, activeChat: null }));
  } else {
    render({ context: null, status: null, activeChat: null });
  }

  setInterval(() => {
    AGW.context.render(latestState.context, latestState.status, latestState.activeChat);
    AGW.quotas.render(latestState.status);
    AGW.zcode.render(latestState.status);
  }, 30_000);
})(window.AGW = window.AGW || {});
