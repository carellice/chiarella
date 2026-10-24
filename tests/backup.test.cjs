const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Backup = require('../web/backup.js');
const fixture = fs.readFileSync(path.join(__dirname, 'fixtures/backup-v1.json'), 'utf8');
const out = path.join(__dirname, '../dist/backup-tests');
const mutate = change => { const root = JSON.parse(fixture); change(root); return JSON.stringify(root); };

test('schema condiviso: tutti i dati e i caratteri Unicode sopravvivono al round trip', () => {
  const data = Backup.parse(fixture);
  assert.deepEqual(Backup.parse(Backup.stringify(data)), data);
  assert.equal(data.volumes.future_sound, 0.5);
  assert.equal(data.passions[0].detail, "L'amica geniale — un po’ di quiete");
  assert.deepEqual(Backup.parse('\uFEFF' + fixture), data);
  fs.mkdirSync(out, {recursive: true});
  fs.writeFileSync(path.join(out, 'web.json'), Backup.stringify(data));
});

test('archivio vuoto e ultima passione nulla sono backup validi', () => {
  const raw = mutate(r => { r.data.presets = []; r.data.passions = []; r.data.lastPassionId = null; r.data.lastMix = {}; r.data.onboardingDone = false; });
  assert.equal(Backup.parse(raw).presets.length, 0);
  assert.equal(Backup.parse(raw).onboardingDone, false);
});

test('file incompleti, corrotti, troppo grandi o di versioni future sono respinti', () => {
  for (const raw of ['null', '[]', '{broken', fixture.repeat(10000),
    mutate(r => r.version = 2), mutate(r => r.version = '1'), mutate(r => r.format = 'other-app'),
    mutate(r => delete r.data.volumes), mutate(r => r.data.masterVolume = -0.1), mutate(r => r.data.masterVolume = 1.01),
    mutate(r => r.data.volumes.mare = '0.5'), mutate(r => r.data.lastTimerMinutes = 4), mutate(r => r.data.lastTimerMinutes = 30.5),
    mutate(r => r.data.presets.push(r.data.presets[0])), mutate(r => r.data.passions = null),
    mutate(r => r.data.presets[0].id = '\" onclick=alert(1)'), mutate(r => r.data.passions[0].category = 'UNKNOWN'),
    mutate(r => r.data.onboardingDone = 'true')]) assert.throws(() => Backup.parse(raw));
});

test('i backup prodotti dai codec nativi mantengono gli stessi dati', () => {
  const expected = Backup.parse(fixture);
  for (const name of ['windows.json', 'android.json']) {
    const file = path.join(out, name);
    if (!fs.existsSync(file)) continue;
    const data = Backup.parse(fs.readFileSync(file, 'utf8'));
    assert.deepEqual(data.presets.map(p => [p.id, p.name]), expected.presets.map(p => [p.id, p.name]));
    assert.deepEqual(data.passions, expected.passions);
    assert.equal(data.lastPassionId, expected.lastPassionId);
    assert.equal(data.lastTimerMinutes, 45);
    assert.equal(data.onboardingDone, true);
    assert.ok(Math.abs(data.masterVolume - 0.65) < 1e-6);
    for (const [id, volume] of Object.entries(expected.volumes)) assert.ok(Math.abs(data.volumes[id] - volume) < 1e-6);
    console.log(`Compatibilità verificata: ${name} -> web`);
  }
});
