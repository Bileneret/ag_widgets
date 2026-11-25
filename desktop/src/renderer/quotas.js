'use strict';

/**
 * Card 02: Antigravity limits (Gemini and Claude/GPT buckets).
 * Exposed as `window.AGW.quotas`.
 */
(function (AGW) {
  const { clamp, formatCountdown } = AGW.format;

  function bucketLabel(bucket) {
    const id = String(bucket.id || '').toLowerCase();
    const group = id.startsWith('gemini-') ? 'Gemini' : id.startsWith('3p-') ? 'Claude/GPT' : '';
    const kind = bucket.kind === '5h' ? '5ч' : bucket.kind === 'weekly' ? 'неделя' : bucket.id || 'квота';
    return group ? `${group} · ${kind}` : kind;
  }

  function render(status) {
    const tier = document.getElementById('plan-tier');
    const list = document.getElementById('quota-list');
    const antigravity = status && status.antigravity;

    tier.textContent = antigravity && antigravity.plan_tier ? antigravity.plan_tier : 'Pro';

    if (!antigravity || !Array.isArray(antigravity.buckets) || antigravity.buckets.length === 0) {
      list.className = 'quota-list empty';
      list.innerHTML = '<div class="empty-msg">Квоты появятся после первого /usage</div>';
      return;
    }

    const order = { '5h': 0, weekly: 1, other: 2 };
    const sorted = [...antigravity.buckets].sort((a, b) => {
      const aGemini = String(a.id || '').startsWith('gemini-') ? 0 : 1;
      const bGemini = String(b.id || '').startsWith('gemini-') ? 0 : 1;
      if (aGemini !== bGemini) return aGemini - bGemini;
      return (order[a.kind] ?? 2) - (order[b.kind] ?? 2);
    });

    list.className = 'quota-list';
    list.innerHTML = '';

    sorted.slice(0, 4).forEach((bucket, index) => {
      const fraction = typeof bucket.remaining_fraction === 'number' ? clamp(bucket.remaining_fraction, 0, 1) : 0;
      const percent = Math.round(fraction * 100);
      const id = String(bucket.id || '');
      const isThirdParty = id.startsWith('3p-');
      const isGemini = id.startsWith('gemini-');

      const row = document.createElement('div');
      row.className = 'quota-row';

      const top = document.createElement('div');
      top.className = 'quota-top';

      const label = document.createElement('span');
      label.className = 'quota-label';
      const dot = document.createElement('span');
      dot.className = `quota-group-dot ${isThirdParty ? 'third' : 'gem'}`;
      const name = document.createElement('span');
      name.textContent = bucketLabel(bucket);
      label.append(dot, name);

      const reset = document.createElement('span');
      reset.className = 'quota-reset';
      reset.textContent = formatCountdown(bucket.reset_time) || '—';
      const percentEl = document.createElement('b');
      percentEl.textContent = ` ${percent}%`;
      reset.appendChild(percentEl);

      const indexEl = document.createElement('span');
      indexEl.className = 'quota-index';
      indexEl.textContent = String(index + 1).padStart(2, '0');
      top.append(indexEl, label, reset);

      const track = document.createElement('div');
      track.className = 'bar-track';
      const fill = document.createElement('div');
      let fillClass = 'bar-fill';
      if (fraction <= 0.001) fillClass += ' empty';
      else if (fraction < 0.2) fillClass += ' low';
      else if (fraction < 0.45) fillClass += ' mid';
      else if (isThirdParty) fillClass += ' third';
      fill.className = fillClass;
      fill.style.width = `${percent}%`;
      track.appendChild(fill);

      row.append(top, track);
      if (!isGemini && !isThirdParty) name.style.opacity = '0.8';
      list.appendChild(row);
    });
  }

  AGW.quotas = { render };
})(window.AGW = window.AGW || {});
