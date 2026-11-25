'use strict';

/**
 * Golden-fixture tests: the shared JSON contract in
 * tests/fixtures/format_cases.json must match the JavaScript implementation
 * exactly (full equality, no tolerance except where the fixture says so).
 *
 * The fixture file is read-only here; these tests never rewrite it.
 * Run with: node --test desktop/test/golden-fixtures.test.js
 */

const { describe, it } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const fmt = require('../src/renderer/format.js');
const security = require('../src/main/zcode-auth.js');

const FIXTURE_PATH = path.join(__dirname, '..', '..', 'tests', 'fixtures', 'format_cases.json');
const fixtures = JSON.parse(fs.readFileSync(FIXTURE_PATH, 'utf8'));

function fixtureCases(name) {
  const list = fixtures[name];
  assert.ok(Array.isArray(list), `${name} must be an array in ${FIXTURE_PATH}`);
  return list;
}

function show(value) {
  return JSON.stringify(value);
}

describe('golden fixtures: formatWindow', () => {
  it('matches every window_cases entry', () => {
    const cases = fixtureCases('window_cases');
    assert.ok(cases.length > 0, `window_cases must not be empty in ${FIXTURE_PATH}`);
    for (const testCase of cases) {
      const actual = fmt.formatWindow(testCase.input);
      assert.equal(
        actual,
        testCase.expected,
        `formatWindow(${show(testCase.input)}) returned ${show(actual)}, expected ${show(testCase.expected)}`,
      );
    }
  });
});

describe('golden fixtures: formatTokens', () => {
  it('matches every token_cases entry', () => {
    const cases = fixtureCases('token_cases');
    assert.ok(cases.length > 0, `token_cases must not be empty in ${FIXTURE_PATH}`);
    for (const testCase of cases) {
      const actual = fmt.formatTokens(testCase.input);
      assert.equal(
        actual,
        testCase.expected,
        `formatTokens(${show(testCase.input)}) returned ${show(actual)}, expected ${show(testCase.expected)}`,
      );
    }
  });
});

describe('golden fixtures: computeZone', () => {
  it('matches every context_level_cases entry', () => {
    const cases = fixtureCases('context_level_cases');
    assert.ok(cases.length > 0, `context_level_cases must not be empty in ${FIXTURE_PATH}`);
    for (const testCase of cases) {
      const zone = fmt.computeZone(
        testCase.tokens,
        testCase.window,
        testCase.yellow_pct,
        testCase.red_pct,
      );
      const where =
        `computeZone(tokens=${show(testCase.tokens)}, window=${show(testCase.window)}, ` +
        `yellow_pct=${show(testCase.yellow_pct)}, red_pct=${show(testCase.red_pct)})`;

      if (testCase.window === null) {
        assert.equal(zone.pct, null, `${where}: pct ${show(zone.pct)} !== expected null`);
        assert.equal(
          zone.level,
          'unknown',
          `${where}: level ${show(zone.level)} !== expected "unknown"`,
        );
      } else {
        assert.equal(
          zone.pct,
          testCase.expected_pct,
          `${where}: pct ${show(zone.pct)} !== expected ${show(testCase.expected_pct)}`,
        );
        assert.equal(
          zone.level,
          testCase.expected_level,
          `${where}: level ${show(zone.level)} !== expected ${show(testCase.expected_level)}`,
        );
      }
    }
  });
});

describe('golden fixtures: isTrustedAuthorizeUrl', () => {
  it('matches every oauth_url_cases entry', () => {
    const cases = fixtureCases('oauth_url_cases');
    assert.ok(cases.length > 0, `oauth_url_cases must not be empty in ${FIXTURE_PATH}`);
    for (const testCase of cases) {
      const actual = security.isTrustedAuthorizeUrl(testCase.url);
      assert.equal(
        typeof actual,
        'boolean',
        `isTrustedAuthorizeUrl(${show(testCase.url)}) must return a boolean, got ${typeof actual}`,
      );
      assert.equal(
        actual,
        testCase.trusted,
        `isTrustedAuthorizeUrl(${show(testCase.url)}) returned ${show(actual)}, expected ${show(testCase.trusted)}`,
      );
    }
  });
});
