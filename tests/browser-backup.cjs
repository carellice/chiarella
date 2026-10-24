// Test facoltativo d'integrazione; vedere docs/backup-format.md per Playwright.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const Backup = require('../web/backup.js');
const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://localhost').pathname;
  const file = url.startsWith('/sounds/')
    ? path.join(root, 'app/src/main/res/raw', path.basename(url))
    : path.join(root, 'web', path.basename(url) || 'index.html');
  try {
    const data = await fs.readFile(file);
    const mime = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.ogg': 'audio/ogg', '.png': 'image/png', '.webmanifest': 'application/manifest+json' }[path.extname(file)];
    res.writeHead(200, { 'Content-Type': mime || 'application/octet-stream' }); res.end(data);
  } catch { res.writeHead(404); res.end(); }
});

(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHIARELLA_BROWSER || undefined });
  try {
    const context = await browser.newContext({ viewport: { width: 390, height: 844 }, reducedMotion: 'reduce' });
    const page = await context.newPage(), errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.waitForFunction(() => soundsReady);
    await page.evaluate(() => {
      state.presets = [{ id: 'before', name: 'Da conservare', mix: { falo: 0.7 } }];
      store.set('presets', state.presets); render();
    });
    await page.locator('#btn-backup').click();
    const load = file => page.locator('#backup-file').setInputFiles(path.join(root, file));
    await load('tests/fixtures/backup-v1.json');
    await page.locator('#backup-restore').waitFor({ state: 'visible' });
    assert.equal(await page.evaluate(() => state.presets[0].id), 'before');
    await page.locator('#backup-cancel').click();
    assert.equal(await page.evaluate(() => state.presets[0].id), 'before');
    assert.equal(await page.locator('#backup-confirm').isVisible(), false);

    // Simula un errore reale di salvataggio a metà delle chiavi, poi controlla il rollback.
    await load('tests/fixtures/backup-v1.json');
    await page.locator('#backup-restore').waitFor({ state: 'visible' });
    const previous = await page.evaluate(() => JSON.stringify({ ...localStorage }));
    await page.evaluate(() => {
      const original = Storage.prototype.setItem;
      let failOnce = true;
      window.restoreStorage = () => { Storage.prototype.setItem = original; };
      Storage.prototype.setItem = function (key, value) {
        if (key === 'chiarella.passions' && failOnce) { failOnce = false; throw new DOMException('Quota full', 'QuotaExceededError'); }
        return original.call(this, key, value);
      };
    });
    await page.locator('#backup-restore').click();
    await page.waitForFunction(() => document.querySelector('#backup-message').textContent.includes('Impossibile salvare'));
    assert.equal(await page.evaluate(() => JSON.stringify({ ...localStorage })), previous);
    assert.equal(await page.evaluate(() => state.presets[0].id), 'before');
    await page.evaluate(() => restoreStorage());
    await page.locator('#backup-cancel').click();

    // File di versioni future e contenuti non JSON non producono una conferma d'importazione.
    for (const content of ['{broken', JSON.stringify({ format: 'chiarella-backup', version: 2, data: {} })]) {
      await page.locator('#backup-file').setInputFiles({ name: 'bad.json', mimeType: 'application/json', buffer: Buffer.from(content) });
      await page.waitForFunction(() => /non è un backup|non supportata/.test(document.querySelector('#backup-message').textContent));
      assert.equal(await page.locator('#backup-confirm').isVisible(), false);
      assert.equal(await page.evaluate(() => state.presets[0].id), 'before');
    }

    await page.locator('#dlg-backup [data-close]').click();
    await page.locator('.card[data-id="falo"]').click();
    await page.waitForFunction(() => audio.voices.get('falo')?.source);
    await page.locator('#btn-timer').click();
    await page.locator('#timer-chips [data-min="15"]').click();
    assert.equal(await page.evaluate(() => state.playing && state.timerEndsAt > 0), true);
    await page.locator('#btn-backup').click();
    for (const file of ['tests/fixtures/backup-v1.json', 'dist/backup-tests/windows.json', 'dist/backup-tests/android.json']) {
      await fs.access(path.join(root, file)); // I test nativi devono essere eseguiti prima.
      await load(file);
      await page.locator('#backup-restore').waitFor({ state: 'visible' });
      await page.screenshot({ path: path.join(root, 'dist/backup-tests/import-mobile.png') });
      await page.locator('#backup-restore').click();
      await page.waitForFunction(() => document.querySelector('#backup-message').textContent.startsWith('Backup importato'));
      assert.equal(await page.evaluate(() => state.presets[0].name), 'Quiete 🌊 e pioggia');
      assert.equal(await page.evaluate(() => store.get('lastTimer')), 45);
      assert.equal(await page.evaluate(() => state.active.size), 0);
      assert.equal(await page.evaluate(() => state.timerEndsAt), 0);
      assert.equal(await page.evaluate(() => state.playing), false);
    }

    const downloaded = page.waitForEvent('download');
    await page.locator('#backup-export').click();
    const download = await downloaded;
    assert.match(download.suggestedFilename(), /^Chiarella-backup-\d{4}-\d{2}-\d{2}\.json$/);
    const exported = path.join(root, 'dist/backup-tests/browser.json');
    await download.saveAs(exported);
    const data = Backup.parse(await fs.readFile(exported, 'utf8'));
    assert.equal(data.passions[0].detail, "L'amica geniale — un po’ di quiete");
    assert.equal(data.lastPassionId, 'passion-2');
    assert.ok(Math.abs(data.masterVolume - 0.65) < 1e-6);

    await page.reload(); await page.waitForFunction(() => soundsReady);
    assert.equal(await page.evaluate(() => state.presets[0].id), 'mix-1');
    assert.equal(await page.evaluate(() => store.get('lastMix').mare > 0.34), true);
    await page.evaluate(() => navigator.serviceWorker.ready);
    await page.waitForFunction(() => !!navigator.serviceWorker.controller);
    await context.setOffline(true);
    await page.reload(); await page.waitForFunction(() => soundsReady);
    await page.locator('#btn-backup').click();
    assert.equal(await page.locator('#backup-export').isEnabled(), true);
    assert.deepEqual(errors, []);
    console.log('PASS: backup browser, conferma, annullamento, rollback, file invalidi, scambio Android/Windows, export, persistenza, offline');
    await context.close();
  } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
