'use strict';

const crypto = require('crypto');
const { shell } = require('electron');
const { SECRETS_PATH } = require('./paths');
const { readJson, writeJsonAtomic } = require('./storage');
const { runCollector } = require('./collector');

const ZCODE_INIT_URL = 'https://zcode.z.ai/api/v1/oauth/cli/init';
const ZCODE_POLL_BASE = 'https://zcode.z.ai/api/v1/oauth/cli/poll';
const LOGIN_TIMEOUT_MS = 300_000;

let loginInFlight = false;

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function fetchJson(url, options = {}, timeoutMs = 20_000) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, { ...options, signal: controller.signal });
    const text = await response.text();
    let json = null;
    try {
      json = JSON.parse(text);
    } catch {
      // Non-JSON error responses are handled by the HTTP status below.
    }
    return { status: response.status, json };
  } finally {
    clearTimeout(timer);
  }
}

/** Only open Z.ai HTTPS URLs, never an arbitrary scheme from a server response. */
function isTrustedAuthorizeUrl(value) {
  try {
    const url = new URL(String(value));
    return url.protocol === 'https:' && (url.hostname === 'z.ai' || url.hostname.endsWith('.z.ai'));
  } catch {
    return false;
  }
}

/**
 * Run the ZCode OAuth flow: open the browser, poll until the server returns a
 * token, save it locally, and refresh the collector.
 */
async function zcodeLogin() {
  if (loginInFlight) return { ok: false, error: 'Login already in progress' };
  loginInFlight = true;

  try {
    const pollToken = crypto.randomBytes(32).toString('hex');
    const authHeaders = {
      Authorization: `Bearer ${pollToken}`,
      Accept: 'application/json',
    };

    const init = await fetchJson(ZCODE_INIT_URL, {
      method: 'POST',
      headers: { ...authHeaders, 'Content-Type': 'application/json' },
      body: JSON.stringify({ provider: 'zai' }),
    });
    const initData = init.json && init.json.data;
    if (init.status !== 200 || !init.json || init.json.code !== 0 || !initData) {
      throw new Error(`OAuth init failed (HTTP ${init.status})`);
    }

    const flowId = String(initData.flow_id || '').trim();
    const authorizeUrl = String(initData.authorize_url || '').trim();
    if (!flowId || !authorizeUrl) throw new Error('OAuth init returned an empty flow');
    if (!isTrustedAuthorizeUrl(authorizeUrl)) throw new Error('Refusing an untrusted sign-in URL');

    const intervalSec = Math.max(2, Math.min(10, Number(initData.poll_interval_sec) || 3));
    try {
      await shell.openExternal(authorizeUrl);
    } catch {
      throw new Error('Could not open the browser for sign-in');
    }

    const pollUrl = `${ZCODE_POLL_BASE}/${encodeURIComponent(flowId)}`;
    const deadline = Date.now() + LOGIN_TIMEOUT_MS;

    while (Date.now() < deadline) {
      await sleep(intervalSec * 1000);
      let poll;
      try {
        poll = await fetchJson(pollUrl, { headers: authHeaders });
      } catch {
        continue;
      }
      if (poll.status >= 400 && poll.status < 500 && poll.status !== 408 && poll.status !== 429) {
        throw new Error(`Sign-in session expired (HTTP ${poll.status})`);
      }

      const data = poll.json && poll.json.data;
      if (!data) continue;
      const status = String(data.status || '');
      if (status === 'pending') continue;
      if (status === 'failed') throw new Error('Sign-in was rejected');
      if (status === 'ready') {
        const token = String(data.token || '').trim();
        if (!token) throw new Error('Server did not return a token');

        const secrets = readJson(SECRETS_PATH) || {};
        secrets.zcode_token = token;
        if (!secrets.device_mid) secrets.device_mid = crypto.randomUUID();
        writeJsonAtomic(SECRETS_PATH, secrets);

        const refreshed = await runCollector();
        return { ok: true, refreshed: Boolean(refreshed && refreshed.ok) };
      }
    }

    throw new Error('Sign-in timed out');
  } catch (error) {
    return { ok: false, error: String((error && error.message) || error) };
  } finally {
    loginInFlight = false;
  }
}

module.exports = { zcodeLogin };
