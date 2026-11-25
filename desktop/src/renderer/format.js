'use strict';

/**
 * Pure formatting helpers shared by all renderer views.
 * Exposed as `window.AGW.format`; importable through CommonJS in Node tests.
 */
(function (root) {
  const AGW = (root.AGW = root.AGW || {});
  const DEFAULT_YELLOW_PCT = 40;
  const DEFAULT_RED_PCT = 65;

  function trimWhole(text) {
    return text.replace(/\.0$/, '');
  }

  /** 999_600 -> "1M", 1_200_000 -> "1.2M", never "1000K". */
  function formatTokens(value) {
    if (typeof value !== 'number' || !Number.isFinite(value)) return '—';
    if (value >= 999_500) return `${trimWhole((value / 1_000_000).toFixed(1))}M`;
    if (value >= 1_000) return `${Math.round(value / 1_000)}K`;
    return String(Math.round(value));
  }

  /** 524_288 -> "512K", 1_048_576 -> "1M", 2_097_152 -> "2M". */
  function formatWindow(value) {
    if (typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return '—';
    if (value >= 1_000_000) {
      if (value === 1_048_576 || value === 1_000_000) return '1M';
      const millions = Math.round((value / 1_048_576) * 10) / 10;
      return `${trimWhole(millions.toString())}M`;
    }
    if (value >= 1024) return `${Math.round(value / 1024)}K`;
    return String(Math.round(value));
  }

  /** Same scale as tokens for ZCode units, but unknown values become "". */
  function formatUnits(value) {
    if (typeof value !== 'number' || !Number.isFinite(value)) return '';
    if (value >= 999_500) return `${trimWhole((value / 1_000_000).toFixed(1))}M`;
    if (value >= 1_000) return `${Math.round(value / 1_000)}K`;
    return String(Math.round(value));
  }

  /** "Gemini 4 Neon (High)" -> "Gemini 4 Neon"; "5 5" -> "5.5". */
  function prettyModel(raw) {
    if (!raw) return 'Неизвестная модель';
    const efforts = new Set(['low', 'medium', 'high', 'xhigh', 'max', 'minimal']);
    const parts = String(raw).split('-').filter(Boolean);
    const last = (parts[parts.length - 1] || '').toLowerCase();
    const hasEffort = parts.length > 1 && efforts.has(last);
    const base = (hasEffort ? parts.slice(0, -1) : parts)
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ');
    return base.replace(/\b(\d+)\s+(\d+)\b/g, '$1.$2');
  }

  function formatCountdown(iso) {
    if (!iso) return '';
    const target = new Date(iso);
    if (Number.isNaN(target.getTime()) || target.getTime() <= 0) return '';
    const diffSec = Math.max(0, Math.floor((target.getTime() - Date.now()) / 1000));
    const hhmm = target.toLocaleTimeString('ru-RU', { hour: '2-digit', minute: '2-digit' });
    if (diffSec <= 0) return `сброс сейчас · ${hhmm}`;
    const days = Math.floor(diffSec / 86400);
    const hours = Math.floor((diffSec % 86400) / 3600);
    const minutes = Math.floor((diffSec % 3600) / 60);
    if (days > 0) return `${days}д ${hours}ч · ${hhmm}`;
    if (hours > 0) return `${hours}ч ${minutes}м · ${hhmm}`;
    return `${minutes}м · ${hhmm}`;
  }

  function formatEndDate(iso) {
    if (!iso) return '';
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return '';
    return date.toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit' });
  }

  function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
  }

  function shortAge(iso) {
    if (!iso) return '';
    const date = new Date(iso);
    if (Number.isNaN(date.getTime())) return '';
    const minutes = Math.max(0, Math.round((Date.now() - date.getTime()) / 60000));
    if (minutes < 1) return 'только что';
    if (minutes < 60) return `${minutes}м назад`;
    const hours = Math.round(minutes / 60);
    if (hours < 24) return `${hours}ч назад`;
    return `${Math.round(hours / 24)}д назад`;
  }

  /**
   * Context health zone per the shared contract:
   * pct < yellow -> green, pct < red -> yellow, otherwise red.
   * A missing token count or window means "unknown" (never a made-up green).
   */
  function computeZone(tokens, windowSize, yellowPct = DEFAULT_YELLOW_PCT, redPct = DEFAULT_RED_PCT) {
    if (!Number.isFinite(tokens) || !Number.isFinite(windowSize) || windowSize <= 0) {
      return { pct: null, level: 'unknown', warnTokens: null, riskTokens: null };
    }
    const yellow = Number.isFinite(yellowPct) ? yellowPct : DEFAULT_YELLOW_PCT;
    const red = Number.isFinite(redPct) ? redPct : DEFAULT_RED_PCT;
    const pct = Math.round((tokens / windowSize) * 10000) / 100;
    return {
      pct,
      level: pct < yellow ? 'green' : pct < red ? 'yellow' : 'red',
      warnTokens: Math.round((windowSize * yellow) / 100),
      riskTokens: Math.round((windowSize * red) / 100),
    };
  }

  AGW.format = {
    formatTokens,
    formatWindow,
    formatUnits,
    prettyModel,
    formatCountdown,
    formatEndDate,
    clamp,
    shortAge,
    computeZone,
    DEFAULT_YELLOW_PCT,
    DEFAULT_RED_PCT,
  };
  AGW.defaults = { DEFAULT_YELLOW_PCT, DEFAULT_RED_PCT };

  if (typeof module !== 'undefined' && module.exports) {
    module.exports = {
      formatTokens,
      formatWindow,
      formatUnits,
      prettyModel,
      formatCountdown,
      formatEndDate,
      clamp,
      shortAge,
      computeZone,
      DEFAULT_YELLOW_PCT,
      DEFAULT_RED_PCT,
    };
  }
})(typeof window !== 'undefined' ? window : globalThis);
