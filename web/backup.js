"use strict";

// Formato condiviso con Android e Windows. La validazione non modifica i dati esistenti.
const ChiarellaBackup = (() => {
  const MAX_BYTES = 2 * 1024 * 1024;
  const fail = () => { throw new Error("Il file non è un backup Chiarella valido."); };
  const object = (v) => { if (!v || typeof v !== "object" || Array.isArray(v)) fail(); return v; };
  const text = (v, max, empty = false) => {
    if (typeof v !== "string" || v.length > max || (!empty && !v.trim())) fail();
    return v;
  };
  const id = (v) => { if (typeof v !== "string" || v.trim() !== v || !/^[A-Za-z0-9_.-]{1,128}$/.test(v)) fail(); return v; };
  const volume = (v) => { if (typeof v !== "number" || !Number.isFinite(v) || v < 0 || v > 1) fail(); return v; };
  const mix = (v) => {
    const entries = Object.entries(object(v));
    if (entries.length > 100) fail();
    return Object.fromEntries(entries.map(([key, value]) => {
      if (key.trim() !== key || !/^[a-z][a-z0-9_]{0,63}$/.test(key)) fail();
      return [key, volume(value)];
    }));
  };
  const list = (v, read) => {
    if (!Array.isArray(v) || v.length > 1000) fail();
    const result = v.map((item) => read(object(item)));
    if (new Set(result.map((item) => item.id)).size !== result.length) fail();
    return result;
  };
  const categories = new Set(["FILM", "SERIE_TV", "VIDEOGIOCHI", "PROGRAMMARE", "LETTURA", "MUSICA", "SPORT", "NATURA", "ARTE", "FOTOGRAFIA", "CUCINA", "BENESSERE", "ALTRO"]);

  function parse(raw) {
    if (new TextEncoder().encode(raw).length > MAX_BYTES) fail();
    let root;
    try { root = object(JSON.parse(raw.replace(/^\uFEFF/, ""))); } catch { return fail(); }
    if (root.format !== "chiarella-backup") fail();
    if (root.version !== 1) throw new Error("Versione del backup non supportata. Aggiorna Chiarella e riprova.");
    const d = object(root.data);
    if (typeof d.onboardingDone !== "boolean" || !Number.isInteger(d.lastTimerMinutes) || d.lastTimerMinutes < 5 || d.lastTimerMinutes > 240) fail();
    return {
      presets: list(d.presets, (p) => ({ id: id(p.id), name: text(p.name, 200), mix: mix(p.mix) })),
      passions: list(d.passions, (p) => {
        if (!categories.has(p.category)) fail();
        return { id: id(p.id), category: p.category, detail: text(p.detail, 1000, true) };
      }),
      volumes: mix(d.volumes), masterVolume: volume(d.masterVolume), lastMix: mix(d.lastMix),
      lastTimerMinutes: d.lastTimerMinutes, onboardingDone: d.onboardingDone,
      lastPassionId: d.lastPassionId === null ? null : id(d.lastPassionId),
    };
  }

  function stringify(data) {
    const raw = JSON.stringify({ format: "chiarella-backup", version: 1, exportedAt: new Date().toISOString(), data }, null, 2);
    parse(raw);
    return raw;
  }

  return { MAX_BYTES, parse, stringify };
})();
if (typeof module !== "undefined") module.exports = ChiarellaBackup;
