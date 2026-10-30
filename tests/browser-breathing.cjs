// Integrazione audio: eseguire con lo stesso ambiente Playwright di browser-backup.cjs.
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const http = require('node:http');
const fs = require('node:fs/promises');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const server = http.createServer(async (req, res) => {
  const pathname = new URL(req.url, 'http://localhost').pathname;
  const file = pathname.startsWith('/sounds/')
    ? path.join(root, 'app/src/main/res/raw', path.basename(pathname))
    : path.join(root, 'web', path.basename(pathname) || 'index.html');
  try {
    const content = await fs.readFile(file);
    const mime = { '.js': 'text/javascript', '.html': 'text/html', '.css': 'text/css', '.ogg': 'audio/ogg' };
    res.writeHead(200, { 'Content-Type': mime[path.extname(file)] || 'application/octet-stream' });
    res.end(content);
  } catch { res.writeHead(404); res.end(); }
});

(async () => {
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const browser = await chromium.launch({ headless: true, executablePath: process.env.CHIARELLA_BROWSER || undefined });
  try {
    const page = await browser.newPage({ viewport: { width: 390, height: 844 } });
    const errors = [];
    page.on('pageerror', error => errors.push(error.message));
    await page.goto(`http://127.0.0.1:${server.address().port}/`);
    await page.waitForFunction(() => soundsReady);
    assert.equal(await page.locator('#device-volume-notice').isVisible(), true);
    const beforeNotice = await page.evaluate(() => JSON.stringify({ master: state.masterVolume, volumes: state.volumes, active: [...state.active] }));
    assert.equal(await page.locator('#device-volume-notice button').count(), 1, 'il web offre soltanto la chiusura del promemoria');
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
    await fs.mkdir(path.join(root, 'dist'), { recursive: true });
    await page.screenshot({ path: path.join(root, 'dist/system-volume-web.png') });
    await page.locator('#dismiss-device-volume').click();
    assert.equal(await page.locator('#device-volume-notice').isVisible(), false);
    assert.equal(await page.evaluate(() => JSON.stringify({ master: state.masterVolume, volumes: state.volumes, active: [...state.active] })), beforeNotice);
    await page.reload();
    await page.waitForFunction(() => soundsReady);
    assert.equal(await page.locator('#device-volume-notice').isVisible(), true, 'il promemoria ritorna alla nuova apertura');
    await page.locator('.card[data-id="candela"]').click();
    await page.waitForFunction(() => audio.voices.get('candela')?.source);
    const candle = await page.evaluate(() => {
      const source = audio.voices.get('candela').source;
      return { looping: source.loop, duration: source.buffer.duration };
    });
    assert.equal(candle.looping, true);
    assert.ok(Math.abs(candle.duration - 13.647846) < 0.05, 'durata originale della candela conservata');
    await page.locator('.card[data-id="candela"]').click();
    await page.evaluate(() => {
      state.passions = [{ id: 'test', category: 'LETTURA', detail: 'Test' }];
      window.cueEvents = [];
      const play = breathCues.play.bind(breathCues);
      breathCues.play = inhaling => { cueEvents.push(inhaling); play(inhaling); };
    });
    await page.locator('#btn-relax').click();
    await page.waitForFunction(() => cueEvents.length === 1 && breathCues.source);
    assert.deepEqual(await page.evaluate(() => cueEvents), [true]);
    await page.waitForFunction(() => document.querySelector('#phase').textContent.includes('Trattieni'));
    assert.equal(await page.evaluate(() => breathCues.source), null);
    assert.deepEqual(await page.evaluate(() => cueEvents), [true]);
    await page.waitForFunction(() => cueEvents.length === 2 && breathCues.source);
    assert.deepEqual(await page.evaluate(() => cueEvents), [true, false]);
    await page.locator('#relax-close').click();
    assert.equal(await page.evaluate(() => breathCues.source), null);
    await page.locator('#btn-relax').click();
    await page.waitForFunction(() => cueEvents.length === 3 && breathCues.source);
    await page.locator('#btn-skip').click();
    assert.equal(await page.evaluate(() => breathCues.source), null);
    assert.deepEqual(errors, []);
    console.log('PASS: promemoria volume web senza modifiche automatiche, riapertura e layout mobile; loop candela e segnali del respiro');
  } finally { await browser.close(); server.close(); }
})().catch(error => { console.error(error); server.close(); process.exitCode = 1; });
