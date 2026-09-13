import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createScalingFixtureServer } from './gecko_safe_area_scaling_server.mjs';

test('host runner serves the unchanged Android fixture and captures bounded reports', async () => {
  const html = '<!doctype html><title>Scaling fixture</title>';
  const server = createScalingFixtureServer(html);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const origin = `http://127.0.0.1:${server.address().port}`;
  try {
    const fixture = await fetch(`${origin}/safe-area-scaling?scale=256&run=baseline`);
    assert.equal(await fixture.text(), html);
    assert.equal(fixture.headers.get('cache-control'), 'no-store');
    for (let index = 0; index < 101; index++) {
      const response = await fetch(`${origin}/safe-area-report`, {
        method: 'POST', body: JSON.stringify({ index }),
      });
      assert.equal(response.status, 204);
    }
    const reports = await (await fetch(`${origin}/reports`)).json();
    assert.equal(reports.length, 100);
    assert.deepEqual(reports[0], { index: 1 });
    assert.deepEqual(reports.at(-1), { index: 100 });
    const queryResponse = await fetch(`${origin}/safe-area-scaling/report?data=` +
      encodeURIComponent(JSON.stringify({ scale: 256, passed: true })));
    assert.equal(queryResponse.status, 204);
    assert.deepEqual((await (await fetch(`${origin}/reports`)).json()).at(-1), {
      scale: 256, passed: true,
    });
    assert.equal((await fetch(`${origin}/safe-area-report`, {
      method: 'POST', body: 'invalid JSON',
    })).status, 400);
    assert.equal((await fetch(`${origin}/safe-area-report`, {
      method: 'POST', body: ' '.repeat(64 * 1024 + 1),
    })).status, 413);
    assert.equal((await fetch(`${origin}/missing`)).status, 404);
  } finally {
    await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }
});
