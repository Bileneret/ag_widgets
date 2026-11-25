'use strict';

/**
 * Mock data for design QA without Electron:
 * `index.html?preview=red|yellow|green|empty|offline|coupon|disabled|summary`.
 * Exposed as `window.AGW.preview`.
 */
(function (AGW) {
  function isoOffset(hours) {
    return new Date(Date.now() + hours * 3600 * 1000).toISOString();
  }

  function previewState(name) {
    const now = new Date().toISOString();
    const base = {
      updated_at: now,
      antigravity: {
        plan_tier: 'Pro',
        buckets: [
          { id: 'gemini-weekly', kind: 'weekly', remaining_fraction: 0.814, reset_time: isoOffset(126) },
          { id: 'gemini-5h', kind: '5h', remaining_fraction: 0.668, reset_time: isoOffset(2.3) },
          { id: '3p-weekly', kind: 'weekly', remaining_fraction: 0.5, reset_time: isoOffset(165) },
          { id: '3p-5h', kind: '5h', remaining_fraction: 0.0, reset_time: isoOffset(5.2) },
        ],
      },
      zcode: {
        running: true,
        coupon_visible: false,
        matched: '',
        plan_id: '',
        method: 'api',
        auth_ok: true,
        network_error: false,
        remaining_units: 320_000_000,
        total_units: 500_000_000,
        ends_at: isoOffset(24 * 6),
        plan_name: 'GLM 5.3',
        checked_at: now,
      },
      context_summary: {
        model: 'gemini-4-neon-high',
        tokens: 377_387,
        window: 524_288,
        pct: 71.98,
        level: 'red',
        yellow_pct: 40,
        red_pct: 65,
        warn_tokens: 209_715,
        risk_tokens: 340_787,
      },
    };

    const contextState = {
      last: 'preview-main',
      conversations: {
        'preview-main': {
          model: 'gemini-4-neon-high',
          tokens: 377_387,
          window: 524_288,
          pct: 71.98,
          level: 'red',
          yellow_pct: 40,
          red_pct: 65,
          warn_tokens: 209_715,
          risk_tokens: 340_787,
          workspace: 'orbit-app',
          snippet: 'Собери новый экран настроек',
          updated_at: now,
        },
        'preview-two': {
          model: 'claude-opus-5-5-high',
          tokens: 128_400,
          window: 1_000_000,
          pct: 12.84,
          level: 'green',
          workspace: 'demo-site',
          updated_at: isoOffset(-1),
        },
        'preview-three': {
          model: 'gemini-4-hydrogen-xhigh',
          tokens: 510_770,
          window: 1_048_576,
          pct: 48.71,
          level: 'yellow',
          workspace: 'workspace-alpha',
          updated_at: isoOffset(-3),
        },
      },
    };

    if (name === 'empty') {
      return { context: null, status: null, now: Date.now() };
    }

    if (name === 'summary') {
      // context.json is missing, but status.json still has the last PC summary.
      return { context: null, status: base, now: Date.now() };
    }

    if (name === 'green') {
      return {
        context: {
          last: 'preview-main',
          conversations: {
            'preview-main': {
              model: 'gemini-4-hydrogen-high',
              tokens: 187_420,
              window: 1_048_576,
              pct: 17.87,
              level: 'green',
              yellow_pct: 40,
              red_pct: 65,
              warn_tokens: 419_430,
              risk_tokens: 681_574,
              workspace: 'orbit-app',
              snippet: 'Добавь тесты для нового экрана',
              updated_at: now,
            },
          },
        },
        status: {
          ...base,
          zcode: { ...base.zcode, remaining_units: 486_000_000, coupon_visible: false },
          context_summary: {
            model: 'gemini-4-hydrogen-high',
            tokens: 187_420,
            window: 1_048_576,
            pct: 17.87,
            level: 'green',
            yellow_pct: 40,
            red_pct: 65,
            warn_tokens: 419_430,
            risk_tokens: 681_574,
          },
        },
        now: Date.now(),
      };
    }

    if (name === 'yellow') {
      const conversation = {
        ...contextState.conversations['preview-main'],
        model: 'gemini-4-carbon-medium',
        tokens: 471_000,
        window: 1_048_576,
        pct: 44.92,
        level: 'yellow',
        warn_tokens: 419_430,
        risk_tokens: 681_574,
      };
      return {
        context: { last: 'preview-main', conversations: { 'preview-main': conversation } },
        status: {
          ...base,
          zcode: { ...base.zcode, remaining_units: 250_000_000, total_units: 500_000_000 },
          context_summary: {
            model: 'gemini-4-carbon-medium',
            tokens: 471_000,
            window: 1_048_576,
            pct: 44.92,
            level: 'yellow',
            yellow_pct: 40,
            red_pct: 65,
            warn_tokens: 419_430,
            risk_tokens: 681_574,
          },
        },
        now: Date.now(),
      };
    }

    if (name === 'disabled') {
      return {
        context: contextState,
        status: {
          ...base,
          zcode: {
            running: false,
            coupon_visible: false,
            matched: '',
            plan_id: '',
            method: 'disabled',
            auth_ok: null,
            network_error: false,
            remaining_units: null,
            total_units: null,
            ends_at: null,
            plan_name: '',
          },
        },
        now: Date.now(),
      };
    }

    if (name === 'offline') {
      return {
        context: contextState,
        status: {
          ...base,
          zcode: {
            ...base.zcode,
            running: false,
            method: 'api',
            auth_ok: null,
            network_error: true,
            remaining_units: null,
            total_units: null,
            ends_at: null,
          },
        },
        now: Date.now(),
      };
    }

    if (name === 'coupon' || name === 'voucher') {
      return {
        context: {
          last: 'preview-main',
          conversations: { 'preview-main': contextState.conversations['preview-main'] },
        },
        status: {
          ...base,
          zcode: {
            ...base.zcode,
            coupon_visible: true,
            matched: 'Забрать 500M токенов',
            remaining_units: 250_000_000,
            total_units: 500_000_000,
          },
        },
        now: Date.now(),
      };
    }

    return { context: contextState, status: base, now: Date.now() };
  }

  function makeBridge(name) {
    const state = previewState(name);
    if (state.context && !state.activeChat) {
      state.activeChat = { id: 'preview-main', title: 'Example Session · Orbit App' };
    }
    return {
      getState: () => Promise.resolve(state),
      hideWindow: () => {},
      minimizeWindow: () => {},
      quitApp: () => {},
      showMenu: () => {},
      moveWindow: () => {},
      refresh: async () => ({ ok: true }),
      zcodeLogin: async () => ({ ok: false, error: 'preview mode' }),
      onStateUpdate: () => () => {},
    };
  }

  AGW.preview = { state: previewState, bridge: makeBridge };
})(window.AGW = window.AGW || {});
