'use strict';

/**
 * Pure formatting helpers shared by all renderer views.
 * Exposed as `window.AGW.format`.
 */
(function (AGW) {
  const DEFAULT_YELLOW_PCT = 40;
  const DEFAULT_RED_PCT = 65;

  function formatTokens(value) {
    if (typeof value !== 'number' || Number.isNaN(value)) return '—';
    if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(value >= 10_000_000 ? 0 : 2)}M`;
    if (value >= 1_000) return `${Math.round(value / 1_000)}K`;
    return String(value);
  }

  function formatWindow(value) {
    if (typeof value !== 'number' || Number.isNaN(value) || value <= 0) return '—';
    if (value >= 1_000_000) return `${Math.round((value / 1_000_000) * 10) / 10}M`;
    if (value >= 1024) return `${Math.round(value / 1024)}K`;
    return String(value);
  }

  function formatUnits(value) {
    if (typeof value !== 'number' || Number.isNaN(value)) return '';
    if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(0)}M`;
    if (value >= 1_000) return `${Math.round(value / 1_000)}K`;
    return String(value);
  }

  /** "Gemini 4 Neon (High)" -> "Gemini 4 Neon". */
  function prettyModel(raw) {
    if (!raw) return 'Неизвестная модель';
    const efforts = new Set(['low', 'medium', 'high', 'xhigh', 'max', 'minimal']);
    const parts = String(raw).split('-').filter(Boolean);
    const last = (parts[parts.length - 1] || '').toLowerCase();
    const hasEffort = parts.length > 1 && efforts.has(last);
    const base = (hasEffort ? parts.slice(0, -1) : parts)
      .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
      .join(' ');
    return base.replace(/\b(\d) (\d)\b/g, '$1.$2');
  }

  function formatCountdown(iso) {
    if (!iso) return '';
    const target = new Date(iso);
    if (Number.isNaN(target.getTime())) return '';
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

  AGW.format = {
    formatTokens,
    formatWindow,
    formatUnits,
    prettyModel,
    formatCountdown,
    formatEndDate,
    clamp,
    shortAge,
  };
  AGW.defaults = { DEFAULT_YELLOW_PCT, DEFAULT_RED_PCT };
})(window.AGW = window.AGW || {});
