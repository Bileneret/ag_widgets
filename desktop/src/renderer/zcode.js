'use strict';

/**
 * Card 03: ZCode coupon, balance, and OAuth sign-in state.
 * Exposed as `window.AGW.zcode`.
 */
(function (AGW) {
  const { clamp, formatEndDate, formatUnits } = AGW.format;

  let loginState = 'idle';
  let loginMessage = '';

  function setLogin(state, message = '') {
    loginState = state;
    loginMessage = message;
  }

  function getLogin() {
    return { state: loginState, message: loginMessage };
  }

  function render(status) {
    const title = document.getElementById('zcode-title');
    const material = document.getElementById('zcode-material');
    const subtitle = document.getElementById('zcode-sub');
    const chip = document.getElementById('zcode-chip');
    const bar = document.getElementById('zcode-bar');
    const barFill = document.getElementById('zcode-bar-fill');
    const actions = document.getElementById('zcode-actions');
    const loginButton = document.getElementById('zcode-login');
    const zcode = status && status.zcode;

    const needsLogin = !zcode || zcode.method !== 'api' || zcode.auth_ok === false;
    const showLogin = loginState !== 'idle' || needsLogin;
    actions.classList.toggle('hidden', !showLogin);
    loginButton.disabled = loginState === 'pending';
    loginButton.textContent =
      loginState === 'pending'
        ? 'ОЖИДАНИЕ ВХОДА…'
        : loginState === 'error'
          ? 'ПОВТОРИТЬ ВХОД'
          : 'ВОЙТИ В Z.AI';

    bar.classList.add('hidden');
    barFill.style.width = '0%';
    subtitle.title = '';
    title.textContent = zcode && zcode.method === 'api' && zcode.plan_name ? `ZCode · ${zcode.plan_name}` : 'ZCode';
    material.textContent = zcode ? (zcode.method === 'api' ? 'API' : zcode.method === 'disabled' ? 'OFF' : '—') : '—';

    if (loginState === 'pending') {
      subtitle.textContent = 'Открыл браузер · завершите вход в Z.ai…';
      chip.className = 'zcode-chip chip-warn';
      chip.textContent = 'ОЖИДАНИЕ';
      material.textContent = 'OAUTH';
      return;
    }

    if (loginState === 'error') {
      subtitle.textContent = `Не удалось войти: ${loginMessage}`;
      chip.className = 'zcode-chip chip-error';
      chip.textContent = 'ОШИБКА';
      material.textContent = 'OAUTH';
      return;
    }

    if (loginState === 'success') {
      subtitle.textContent = loginMessage || 'Вход выполнен · обновляю баланс…';
      chip.className = 'zcode-chip chip-available';
      chip.textContent = 'ВХОД OK';
      material.textContent = 'API';
      return;
    }

    if (!zcode) {
      subtitle.textContent = 'Ожидание проверки';
      chip.className = 'zcode-chip chip-idle';
      chip.textContent = 'нет данных';
      return;
    }

    if (zcode.method === 'disabled') {
      subtitle.textContent = 'Не подключён · войдите в Z.ai';
      chip.className = 'zcode-chip chip-idle';
      chip.textContent = 'не подключён';
      return;
    }

    if (zcode.network_error) {
      subtitle.textContent = 'zcode.z.ai недоступен · повторим позже';
      chip.className = 'zcode-chip chip-error';
      chip.textContent = 'нет связи';
      return;
    }

    if (zcode.method === 'api' && zcode.auth_ok === false) {
      subtitle.textContent = 'Токен истёк · войдите снова';
      chip.className = 'zcode-chip chip-warn';
      chip.textContent = 'нужен вход';
      return;
    }

    const hasBalance = typeof zcode.remaining_units === 'number' && zcode.remaining_units > 0;
    const hasTotal = typeof zcode.total_units === 'number' && zcode.total_units > 0;
    const balanceText = hasBalance
      ? `Остаток: ${formatUnits(zcode.remaining_units)}${hasTotal ? ` / ${formatUnits(zcode.total_units)}` : ''}`
      : '';
    const endsDate = formatEndDate(zcode.ends_at);
    const endsText = endsDate ? ` · до ${endsDate}` : '';

    if (hasBalance && hasTotal) {
      bar.classList.remove('hidden');
      barFill.style.width = `${clamp((zcode.remaining_units / zcode.total_units) * 100, 1, 100)}%`;
    }

    if (zcode.method === 'api' && zcode.coupon_visible) {
      subtitle.textContent = balanceText ? `${balanceText}${endsText}` : 'Ваучер 300–500M · заберите в ZCode';
      subtitle.title = zcode.matched || '';
      chip.className = 'zcode-chip chip-available';
      chip.textContent = 'Ваучер доступен';
      return;
    }

    if (balanceText) {
      subtitle.textContent = `${balanceText}${endsText}`;
      chip.className = 'zcode-chip chip-none';
      chip.textContent = 'нет ваучера';
      return;
    }

    const checked = zcode.checked_at ? new Date(zcode.checked_at) : null;
    const checkedText =
      checked && !Number.isNaN(checked.getTime())
        ? ` · ${checked.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' })}`
        : '';
    subtitle.textContent = zcode.method === 'api' ? `Прямой API zcode.z.ai${checkedText}` : 'ZCode';
    chip.className = 'zcode-chip chip-none';
    chip.textContent = 'нет ваучера';
  }

  AGW.zcode = { render, setLogin, getLogin };
})(window.AGW = window.AGW || {});
