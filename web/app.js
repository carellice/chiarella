"use strict";

// ------------------------------------------------------------------ archivio

const store = {
  get(key, fallback) {
    try {
      const raw = localStorage.getItem("chiarella." + key);
      return raw == null ? fallback : JSON.parse(raw);
    } catch { return fallback; }
  },
  set(key, value) {
    try { localStorage.setItem("chiarella." + key, JSON.stringify(value)); } catch { /* archivio non disponibile */ }
  },
};

const state = {
  active: new Set(),
  volumes: store.get("volumes", {}),
  // Volume generale (0..1): si applica sopra a quello di ogni suono, senza cambiarlo.
  masterVolume: store.get("masterVolume", 1),
  playing: false,
  presets: store.get("presets", []),
  presetId: null,
  timerEndsAt: 0,
  timerDuration: 0,
  passions: store.get("passions", []),
  showVolumeNotice: store.get("showVolumeNotice", true) !== false,
  reducedAnimations: store.get("reducedAnimations", false) === true,
};
let visualFrame = 0;
const volumeOf = (id) => state.volumes[id] ?? 0.7;
const soundById = Object.assign(Object.create(null), Object.fromEntries(SOUNDS.map((s) => [s.id, s])));
const categoryById = Object.assign(Object.create(null), Object.fromEntries(CATEGORIES.map((c) => [c.id, c])));
const $ = (sel) => document.querySelector(sel);
const uid = () => (crypto.randomUUID ? crypto.randomUUID() : String(Date.now() + Math.random()));

// Il browser non espone il volume del dispositivo: questo è un promemoria, non una misurazione.
$("#dismiss-device-volume").addEventListener("click", () => { $("#device-volume-notice").hidden = true; });
window.addEventListener("pageshow", () => { $("#device-volume-notice").hidden = !state.showVolumeNotice; });
$("#disable-device-volume").addEventListener("click", () => setPreference("showVolumeNotice", false));
$("#btn-preferences").addEventListener("click", () => $("#dlg-preferences").showModal());
$("#pref-volume-notice").addEventListener("change", (e) => setPreference("showVolumeNotice", e.target.checked));
$("#pref-reduced-animations").addEventListener("change", (e) => setPreference("reducedAnimations", e.target.checked));

function setPreference(key, value) {
  state[key] = value;
  store.set(key, value);
  applyPreferences();
}

function applyPreferences() {
  $("#device-volume-notice").hidden = !state.showVolumeNotice;
  $("#pref-volume-notice").checked = state.showVolumeNotice;
  $("#pref-reduced-animations").checked = state.reducedAnimations;
  document.body.classList.toggle("reduced-motion", state.reducedAnimations);
  if (state.reducedAnimations) {
    cancelAnimationFrame(visualFrame);
    visualFrame = 0;
  } else refreshVisuals();
}

function refreshVisuals() {
  if (!state.reducedAnimations && !visualFrame && !document.hidden) visualFrame = requestAnimationFrame(frame);
}

// ------------------------------------------------------------------ audio

const SOUND_CACHE = "chiarella-audio-v2";
let soundsReady = false;

/**
 * Motore audio con Web Audio API: ogni suono è un AudioBuffer in loop (senza giunture,
 * a livello di campione) con il suo guadagno; tutti passano da un guadagno generale.
 */
const audio = {
  ctx: null,
  master: null,
  voices: new Map(),
  buffers: new Map(),
  files: new Map(),

  ensure() {
    if (this.ctx) return;
    this.ctx = new (window.AudioContext || window.webkitAudioContext)();
    this.master = this.ctx.createGain();
    // Il guadagno "master" serve a pausa e timer; quello dell'utente è separato, così non si pestano i piedi.
    this.user = this.ctx.createGain();
    this.user.gain.value = perceptual(state.masterVolume);
    this.master.connect(this.user);
    this.user.connect(this.ctx.destination);
  },

  setUserVolume(v) {
    if (this.ctx) this.ramp(this.user.gain, perceptual(v), 0.08);
  },

  /** I suoni stanno in `sounds/`; avviando il sito dalla cartella del progetto si usano quelli dell'app. */
  download(id) {
    if (!this.files.has(id)) {
      const p = (async () => {
        const cache = globalThis.caches ? await caches.open(SOUND_CACHE).catch(() => null) : null;
        // Il nuovo URL evita anche la vecchia cache del worker ancora attivo durante l'aggiornamento.
        const revision = id === "falo" ? "?v=2" : "";
        for (const url of [`sounds/${id}.ogg${revision}`, `../app/src/main/res/raw/${id}.ogg${revision}`]) {
          const cached = cache && await cache.match(url).catch(() => null);
          if (cached) return cached.arrayBuffer();
          const controller = new AbortController();
          const timeout = setTimeout(() => controller.abort(), 60_000);
          try {
            const res = await fetch(url, { signal: controller.signal });
            if (!res.ok) continue;
            const copy = res.clone();
            const data = await res.arrayBuffer();
            // La cache può essere piena o non disponibile: i file restano comunque in memoria.
            if (cache) await cache.put(url, copy).catch(() => {});
            return data;
          } catch { /* prova anche il percorso usato nello sviluppo locale */ }
          finally { clearTimeout(timeout); }
        }
        throw new Error("Suono non trovato: " + id);
      })();
      p.catch(() => this.files.delete(id));
      this.files.set(id, p);
    }
    return this.files.get(id);
  },

  load(id) {
    if (!this.buffers.has(id)) {
      // Decodifica solo i suoni scelti: precaricarli tutti occuperebbe oltre 500 MB.
      const p = this.download(id).then((data) => this.ctx.decodeAudioData(data.slice(0)));
      p.catch(() => this.buffers.delete(id));
      this.buffers.set(id, p);
    }
    return this.buffers.get(id);
  },

  async start(id) {
    this.ensure();
    if (this.voices.has(id)) return;
    const gain = this.ctx.createGain();
    gain.gain.value = 0;
    gain.connect(this.master);
    const voice = { gain, source: null };
    this.voices.set(id, voice);
    try {
      const buffer = await this.load(id);
      if (this.voices.get(id) !== voice) return; // spento mentre caricava
      const source = this.ctx.createBufferSource();
      source.buffer = buffer;
      source.loop = true;
      source.connect(gain);
      // Ogni suono parte da un punto a caso: due mix uguali non suonano mai identici.
      source.start(0, Math.random() * buffer.duration);
      voice.source = source;
      this.ramp(gain.gain, volumeOf(id), 1.2);
      render();
    } catch (e) {
      console.error(e);
      this.voices.delete(id);
      state.active.delete(id);
      toast("Impossibile caricare il suono");
      render();
    }
  },

  stop(id, fade = 1) {
    const voice = this.voices.get(id);
    if (!voice) return;
    this.voices.delete(id);
    this.ramp(voice.gain.gain, 0, fade);
    setTimeout(() => {
      try { voice.source && voice.source.stop(); } catch { /* già fermo */ }
      voice.gain.disconnect();
    }, fade * 1000 + 100);
  },

  setVolume(id, v) {
    const voice = this.voices.get(id);
    if (voice) this.ramp(voice.gain.gain, v, 0.08);
  },

  ramp(param, to, seconds) {
    const now = this.ctx.currentTime;
    param.cancelScheduledValues(now);
    param.setValueAtTime(param.value, now);
    param.linearRampToValueAtTime(to, now + seconds);
  },

  async resume() {
    this.ensure();
    await this.ctx.resume();
    this.ramp(this.master.gain, 1, 0.6);
  },

  async pause() {
    if (!this.ctx) return;
    this.ramp(this.master.gain, 0, 0.4);
    await new Promise((r) => setTimeout(r, 420));
    if (!state.playing) await this.ctx.suspend();
  },
};

// ------------------------------------------------------------------ azioni

function toggleSound(id) {
  if (!soundsReady) return;
  if (state.active.has(id)) {
    state.active.delete(id);
    audio.stop(id);
  } else {
    state.active.add(id);
    audio.start(id);
    if (!state.playing) play();
  }
  state.presetId = null;
  rememberMix();
  if (state.active.size === 0) stopAll();
  render();
}

function play() {
  if (!soundsReady) return;
  if (state.active.size === 0) return;
  state.playing = true;
  audio.resume();
  render();
}

function pause() {
  state.playing = false;
  audio.pause();
  render();
}

function togglePlay() { state.playing ? pause() : play(); }

function stopAll() {
  [...audio.voices.keys()].forEach((id) => audio.stop(id, 0.8));
  state.active.clear();
  state.playing = false;
  state.presetId = null;
  cancelTimer();
  render();
}

function setVolume(id, v) {
  state.volumes[id] = v;
  store.set("volumes", state.volumes);
  rememberMix();
  audio.setVolume(id, v);
  state.presetId = null;
  renderPlayer();
  renderPresets();
}

function applyPreset(preset) {
  if (!soundsReady) return;
  if (state.presetId === preset.id) { togglePlay(); return; }
  const ids = Object.keys(preset.mix).filter((id) => soundById[id]);
  [...state.active].filter((id) => !ids.includes(id)).forEach((id) => { state.active.delete(id); audio.stop(id); });
  ids.forEach((id) => {
    state.volumes[id] = preset.mix[id];
    if (state.active.has(id)) audio.setVolume(id, preset.mix[id]);
    else { state.active.add(id); audio.start(id); }
  });
  store.set("volumes", state.volumes);
  rememberMix();
  state.presetId = preset.id;
  play();
  render();
}

// ------------------------------------------------------------------ timer

let timerHandle = 0;
let fadingOut = false;

function startTimer(minutes) {
  store.set("lastTimer", minutes);
  state.timerDuration = minutes * 60_000;
  state.timerEndsAt = Date.now() + state.timerDuration;
  fadingOut = false;
  clearInterval(timerHandle);
  timerHandle = setInterval(tickTimer, 1000);
  if (!state.playing) play();
  render();
}

function extendTimer(minutes) {
  if (!state.timerEndsAt) return startTimer(minutes);
  state.timerEndsAt += minutes * 60_000;
  state.timerDuration += minutes * 60_000;
  if (fadingOut) { fadingOut = false; if (state.playing) audio.ramp(audio.master.gain, 1, 1); }
  render();
}

function cancelTimer() {
  clearInterval(timerHandle);
  if (fadingOut && state.playing) audio.ramp(audio.master.gain, 1, 1);
  fadingOut = false;
  state.timerEndsAt = 0;
  state.timerDuration = 0;
  renderPlayer();
  renderTimerDialog();
}

function tickTimer() {
  const remaining = state.timerEndsAt - Date.now();
  // Dissolvenza finale di 12 secondi, come nell'app.
  if (remaining <= 12_000 && !fadingOut && state.playing) {
    fadingOut = true;
    audio.ramp(audio.master.gain, 0, Math.max(remaining, 0) / 1000);
  }
  if (remaining <= 0) {
    cancelTimer();
    pause();
    return;
  }
  renderPlayer();
  renderTimerDialog();
}

// ------------------------------------------------------------------ interfaccia

function formatCountdown(ms) {
  const total = Math.ceil(Math.max(ms, 0) / 1000);
  const h = Math.floor(total / 3600), m = Math.floor((total % 3600) / 60), s = total % 60;
  const pad = (n) => String(n).padStart(2, "0");
  return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}

function formatMinutes(min) {
  if (min < 60) return `${min} min`;
  return min % 60 === 0 ? `${min / 60} h` : `${Math.floor(min / 60)} h ${min % 60} min`;
}

function gradient(ids) {
  const colors = ids.flatMap((id) => [soundById[id].primary, soundById[id].secondary]);
  return `linear-gradient(135deg, ${colors.length ? colors.join(", ") : "var(--lavender), var(--sky)"})`;
}

function stackHtml(ids, max = 3) {
  const shown = ids.slice(0, max);
  const extra = ids.length - shown.length;
  return shown.map((id) => `<span style="--grad:linear-gradient(135deg,${soundById[id].primary},${soundById[id].secondary})">${soundById[id].icon}</span>`).join("")
    + (extra > 0 ? `<span style="--grad:var(--surface-high)">+${extra}</span>` : "");
}

let toastTimer = 0;
function toast(text) {
  const el = $("#toast");
  el.textContent = text;
  el.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.hidden = true; }, 2600);
}

function buildGrid() {
  const grid = $("#grid");
  SOUNDS.forEach((s, i) => {
    const card = document.createElement("div");
    card.className = "card";
    card.dataset.id = s.id;
    card.tabIndex = 0;
    card.setAttribute("role", "button");
    card.style.cssText = `--p:${s.primary};--s:${s.secondary};--i:${i}`;
    card.innerHTML = `<canvas></canvas><span class="ico">${s.icon}</span>
      <div><strong>${s.name}</strong><small>${s.tagline}</small>
      <input class="vol" type="range" min="0" max="1" step="0.01" aria-label="Volume ${s.name}" /></div>`;
    const range = card.querySelector(".vol");
    range.value = volumeOf(s.id);
    range.addEventListener("input", () => setVolume(s.id, Number(range.value)));
    range.addEventListener("click", (e) => e.stopPropagation());
    range.addEventListener("keydown", (e) => e.stopPropagation());
    card.addEventListener("click", () => {
      if (detail.suppressClick) { detail.suppressClick = false; return; }
      toggleSound(s.id);
    });
    // Dettaglio: clic destro (o il tasto Menu), oppure pressione lunga sugli schermi touch.
    card.addEventListener("contextmenu", (e) => { e.preventDefault(); openSoundDetail(s.id, card); });
    card.addEventListener("pointerdown", (e) => {
      if (e.pointerType !== "touch" || e.target.closest(".vol")) return;
      clearTimeout(detail.pressTimer);
      detail.pressTimer = setTimeout(() => { detail.suppressClick = true; openSoundDetail(s.id, card); }, 550);
    });
    ["pointerup", "pointercancel", "pointerleave", "pointermove"].forEach((t) =>
      card.addEventListener(t, (e) => { if (t !== "pointermove" || Math.abs(e.movementY) > 4) clearTimeout(detail.pressTimer); }));
    card.addEventListener("keydown", (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggleSound(s.id); } });
    grid.appendChild(card);
  });
}

function render() {
  document.querySelectorAll(".card").forEach((card) => {
    const on = state.active.has(card.dataset.id);
    const loading = on && !audio.voices.get(card.dataset.id)?.source;
    card.classList.toggle("on", on);
    card.setAttribute("aria-pressed", on);
    card.setAttribute("aria-busy", loading);
    card.querySelector("small").textContent = loading ? "Preparazione audio…" : soundById[card.dataset.id].tagline;
    card.querySelector(".vol").value = volumeOf(card.dataset.id);
  });
  const n = state.active.size;
  $("#status-text").textContent = n === 0 ? "Accendi uno o più suoni e mescolali come preferisci."
    : state.playing ? (n === 1 ? "1 suono in riproduzione" : `${n} suoni in riproduzione`) : "In pausa";
  $("#status-eq").hidden = n === 0;
  $("#status-eq").classList.toggle("on", state.playing);
  const hint = $("#sounds-hint");
  hint.textContent = n ? "Ferma tutti" : "Tocca per accendere";
  hint.classList.toggle("on", n > 0);
  renderPlayer();
  renderPresets();
  renderTimerDialog();
  renderSoundDetail();
  renderMaster();
  updateMediaSession();
}

function renderPlayer() {
  const ids = [...state.active];
  // Durante "Un momento per te" la barra resta nascosta.
  $("#player").hidden = ids.length === 0 || !$("#relax").hidden;
  if (!ids.length) return;
  const preset = state.presets.find((p) => p.id === state.presetId);
  $("#player-stack").className = "stack";
  $("#player-stack").innerHTML = stackHtml(ids);
  $("#player-title").textContent = preset ? preset.name : ids.length === 1 ? soundById[ids[0]].name : ids.map((id) => soundById[id].name).join(" · ");
  const timer = state.timerEndsAt ? ` · si spegne tra ${formatCountdown(state.timerEndsAt - Date.now())}` : "";
  $("#player-sub").textContent = (state.playing ? "In riproduzione" : "In pausa") + timer;
  $("#btn-play").classList.toggle("paused", !state.playing);
  const badge = $("#timer-badge");
  badge.hidden = !state.timerEndsAt;
  if (state.timerEndsAt) badge.textContent = formatCountdown(state.timerEndsAt - Date.now());
}

function renderPresets() {
  const box = $("#presets");
  const parts = [`<button class="preset new" id="btn-save"><span>+</span>Salva mix</button>`];
  if (!state.presets.length) {
    parts.push(`<div class="preset empty"><strong>Ancora nessun profilo</strong><small>Accendi i suoni che ti piacciono, regola i volumi e tocca «Salva mix».</small></div>`);
  }
  state.presets.forEach((p) => {
    const ids = Object.keys(p.mix).filter((id) => soundById[id]);
    const sel = p.id === state.presetId;
    parts.push(`<button class="preset${sel ? " selected" : ""}" data-preset="${p.id}" style="--grad:${gradient(ids)}">
      <div class="stack">${stackHtml(ids)}</div>
      <div><strong>${escapeHtml(p.name)}</strong><br><small>${sel ? (state.playing ? "In riproduzione" : "In pausa") : ids.length + (ids.length === 1 ? " suono" : " suoni")}</small></div></button>`);
  });
  box.innerHTML = parts.join("");
  $("#presets-hint").textContent = state.presets.length ? "Tieni premuto per eliminare" : "I tuoi mix preferiti";
}

function escapeHtml(text) {
  return text.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// Profili: clic per applicare, pressione lunga o clic destro per eliminare.
(function bindPresets() {
  const box = $("#presets");
  let pressTimer = 0, longPressed = false;
  const target = (e) => e.target.closest("[data-preset]");
  const askDelete = (el) => {
    const preset = state.presets.find((p) => p.id === el.dataset.preset);
    if (preset && confirm(`Eliminare "${preset.name}"?\nIl profilo verrà rimosso. I suoni non verranno toccati.`)) {
      state.presets = state.presets.filter((p) => p.id !== preset.id);
      if (state.presetId === preset.id) state.presetId = null;
      store.set("presets", state.presets);
      renderPresets();
    }
  };
  box.addEventListener("pointerdown", (e) => {
    const el = target(e);
    longPressed = false;
    if (el) pressTimer = setTimeout(() => { longPressed = true; askDelete(el); }, 600);
  });
  ["pointerup", "pointerleave", "pointercancel"].forEach((t) => box.addEventListener(t, () => clearTimeout(pressTimer)));
  box.addEventListener("contextmenu", (e) => { const el = target(e); if (el) { e.preventDefault(); askDelete(el); } });
  box.addEventListener("click", (e) => {
    if (e.target.closest("#btn-save")) return openSave();
    const el = target(e);
    if (!el || longPressed) return;
    const preset = state.presets.find((p) => p.id === el.dataset.preset);
    if (preset) applyPreset(preset);
  });
})();

function openSave() {
  if (!state.active.size) return toast("Accendi almeno un suono per salvare un mix");
  $("#save-name").value = `Il mio mix ${state.presets.length + 1}`;
  $("#save-preview").innerHTML = [...state.active].map((id) => {
    const s = soundById[id], v = volumeOf(id);
    return `<div class="mixrow" style="--grad:linear-gradient(90deg,${s.primary},${s.secondary})"><div class="stack">${stackHtml([id])}</div>
      <span>${s.name}</span><div class="bar"><i style="width:${v * 100}%"></i></div><span>${Math.round(v * 100)}%</span></div>`;
  }).join("");
  $("#dlg-save").showModal();
}

$("#save-form").addEventListener("submit", (e) => {
  e.preventDefault();
  const name = $("#save-name").value.trim();
  if (!name) return;
  const preset = { id: uid(), name, mix: Object.fromEntries([...state.active].map((id) => [id, volumeOf(id)])) };
  state.presets.push(preset);
  store.set("presets", state.presets);
  state.presetId = preset.id;
  $("#dlg-save").close();
  toast(`Profilo "${name}" salvato`);
  render();
});

// Timer
const QUICK = [15, 30, 45, 60, 90, 120];
$("#timer-chips").innerHTML = QUICK.map((m) => `<button data-min="${m}">${formatMinutes(m)}</button>`).join("");
$("#timer-chips").addEventListener("click", (e) => {
  const b = e.target.closest("[data-min]");
  if (b) { startTimer(Number(b.dataset.min)); $("#dlg-timer").close(); }
});
const range = $("#custom-range");
range.value = store.get("lastTimer", 30);
const syncCustom = () => { $("#custom-label").textContent = formatMinutes(Number(range.value)); };
range.addEventListener("input", syncCustom);
syncCustom();
$("#btn-custom").addEventListener("click", () => { startTimer(Number(range.value)); $("#dlg-timer").close(); });
$("#timer-on").addEventListener("click", (e) => { const b = e.target.closest("[data-extend]"); if (b) extendTimer(Number(b.dataset.extend)); });
$("#btn-cancel-timer").addEventListener("click", cancelTimer);

function renderTimerDialog() {
  const on = !!state.timerEndsAt;
  $("#timer-off").hidden = on;
  $("#timer-on").hidden = !on;
  if (!on) return;
  const remaining = state.timerEndsAt - Date.now();
  $("#ring-time").textContent = formatCountdown(remaining);
  $("#ring-end").textContent = "si spegne alle " + new Date(state.timerEndsAt).toLocaleTimeString("it-IT", { hour: "2-digit", minute: "2-digit" });
  $("#ring-progress").style.strokeDashoffset = String(283 * (1 - Math.max(remaining, 0) / state.timerDuration));
}

document.querySelectorAll("[data-close]").forEach((b) => b.addEventListener("click", () => b.closest("dialog").close()));
$("#btn-timer").addEventListener("click", () => { renderTimerDialog(); $("#dlg-timer").showModal(); });
$("#btn-play").addEventListener("click", togglePlay);
$("#btn-stop").addEventListener("click", stopAll);
$("#sounds-hint").addEventListener("click", () => { if (state.active.size) stopAll(); });
$("#btn-help").addEventListener("click", () => $("#dlg-help").showModal());
document.addEventListener("keydown", (e) => {
  if (e.code === "Space" && !e.target.closest("input, textarea, dialog, button, [role=button]") && $("#relax").hidden) {
    e.preventDefault();
    togglePlay();
  }
});

function updateMediaSession() {
  if (!("mediaSession" in navigator)) return;
  const ids = [...state.active];
  if (!ids.length) { navigator.mediaSession.metadata = null; return; }
  const preset = state.presets.find((p) => p.id === state.presetId);
  navigator.mediaSession.metadata = new MediaMetadata({
    title: preset ? preset.name : ids.map((id) => soundById[id].name).join(" · "),
    artist: "Chiarella",
    artwork: [{ src: "icon-512.png", sizes: "512x512", type: "image/png" }],
  });
  navigator.mediaSession.playbackState = state.playing ? "playing" : "paused";
}
if ("mediaSession" in navigator) {
  navigator.mediaSession.setActionHandler("play", play);
  navigator.mediaSession.setActionHandler("pause", pause);
  navigator.mediaSession.setActionHandler("stop", stopAll);
}

const hour = new Date().getHours();
$("#greeting").textContent = hour >= 5 && hour < 12 ? "Buongiorno" : hour < 18 && hour >= 12 ? "Buon pomeriggio" : hour >= 18 && hour < 23 ? "Buonasera" : "Buonanotte";

// ------------------------------------------------------------------ sfondo e animazioni

const IDLE = ["#3B2F8F", "#1F4E8C", "#5B2C83"];
const aurora = {
  canvas: $("#aurora"),
  colors: IDLE.map(hexToRgb),
  target: IDLE.map(hexToRgb),
  intensity: 0.55,
  stars: Array.from({ length: 70 }, () => ({ x: Math.random(), y: Math.random() * 0.8, size: 0.5 + Math.random() * 1.3, phase: Math.random() * 6.28 })),
  override: null,
};

function hexToRgb(hex) {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}
const rgba = (c, a) => `rgba(${c[0] | 0},${c[1] | 0},${c[2] | 0},${a})`;

function auroraTarget() {
  const list = aurora.override ?? [...state.active].map((id) => soundById[id].primary);
  const base = list.length ? list : IDLE;
  return [0, 1, 2].map((i) => hexToRgb(base[i % base.length]));
}

function drawAurora(t) {
  const c = aurora.canvas, dpr = Math.min(devicePixelRatio || 1, 2);
  const w = innerWidth, h = innerHeight;
  if (c.width !== Math.round(w * dpr) || c.height !== Math.round(h * dpr)) { c.width = w * dpr; c.height = h * dpr; }
  const g = c.getContext("2d");
  g.setTransform(dpr, 0, 0, dpr, 0, 0);
  const target = auroraTarget();
  aurora.colors = aurora.colors.map((col, i) => col.map((v, k) => v + (target[i][k] - v) * 0.02));
  const lively = state.playing || aurora.override;
  aurora.intensity += ((lively ? 1 : 0.55) - aurora.intensity) * 0.02;

  const bg = g.createLinearGradient(0, 0, 0, h);
  bg.addColorStop(0, "#080B19"); bg.addColorStop(0.5, "#0D1030"); bg.addColorStop(1, "#05070F");
  g.fillStyle = bg;
  g.fillRect(0, 0, w, h);

  const s = t / 36000 * Math.PI * 2;
  const pulse = lively ? 0.975 + 0.125 * Math.sin(t / 5000 * Math.PI) : 1;
  const big = Math.max(w, h * 0.7);
  glow(g, aurora.colors[0], w * (0.2 + 0.15 * Math.cos(s)), h * (0.18 + 0.06 * Math.sin(s * 2)), big * 0.85 * pulse, 0.42 * aurora.intensity);
  glow(g, aurora.colors[1], w * (0.85 + 0.1 * Math.sin(s + 1)), h * (0.45 + 0.08 * Math.cos(s)), big * 0.75, 0.32 * aurora.intensity);
  glow(g, aurora.colors[2], w * (0.35 + 0.2 * Math.sin(s + 2.5)), h * (0.9 + 0.05 * Math.cos(s * 2)), big * 0.9 * pulse, 0.30 * aurora.intensity);

  const tw = t / 6000 * Math.PI * 2;
  aurora.stars.forEach((st) => {
    const a = 0.25 + 0.55 * ((Math.sin(tw * (0.6 + st.phase % 1) + st.phase) + 1) / 2);
    g.fillStyle = `rgba(255,255,255,${a * 0.8})`;
    g.beginPath(); g.arc(st.x * w, st.y * h, st.size, 0, 6.283); g.fill();
  });
}

function glow(g, color, x, y, r, a) {
  const grad = g.createRadialGradient(x, y, 0, x, y, r);
  grad.addColorStop(0, rgba(color, a));
  grad.addColorStop(0.45, rgba(color, a * 0.45));
  grad.addColorStop(1, rgba(color, 0));
  g.fillStyle = grad;
  g.fillRect(x - r, y - r, r * 2, r * 2);
}

// Scene animate dentro le card dei suoni accesi.
const particles = Array.from({ length: 30 }, () => ({ x: Math.random(), y: Math.random(), size: Math.random(), speed: 0.5 + Math.random(), phase: Math.random() * 6.28 }));

function drawAmbience(canvas, sound, t, intensity = null) {
  const r = canvas.getBoundingClientRect();
  const dpr = Math.min(devicePixelRatio || 1, 2);
  if (canvas.width !== Math.round(r.width * dpr)) { canvas.width = r.width * dpr; canvas.height = r.height * dpr; }
  const g = canvas.getContext("2d");
  g.setTransform(dpr, 0, 0, dpr, 0, 0);
  const w = r.width, h = r.height, sec = t / 1000;
  const a = intensity ?? (state.playing ? 1 : 0.35);
  const p = hexToRgb(sound.primary), q = hexToRgb(sound.secondary);
  g.clearRect(0, 0, w, h);
  g.lineCap = "round";
  const lines = (n, fn) => particles.slice(0, n).forEach(fn);

  switch (sound.ambience) {
    case "rain":
    case "storm":
      lines(26, (pt) => {
        const y = ((pt.y + sec * 0.9 * pt.speed) % 1.2 - 0.1) * h, x = pt.x * w;
        g.strokeStyle = rgba(q, 0.45 * a); g.lineWidth = 1 + pt.size;
        g.beginPath(); g.moveTo(x, y); g.lineTo(x - 3, y + 10 + pt.size * 8); g.stroke();
      });
      if (sound.ambience === "storm") {
        const flash = Math.max(0, Math.sin(sec * 0.9) * Math.sin(sec * 7.3)) ** 6;
        g.fillStyle = rgba(q, flash * 0.5 * a); g.fillRect(0, 0, w, h);
      }
      break;
    case "heat":
      for (let i = 0; i < 4; i++) {
        g.strokeStyle = rgba(i % 2 ? q : p, 0.25 * a); g.lineWidth = 2;
        g.beginPath();
        for (let x = 0; x <= w; x += 6) {
          const y = h * (0.55 + i * 0.1) + Math.sin(x / w * 9 + sec * 2 + i) * 4 - ((sec * 12 + i * 20) % 30);
          x ? g.lineTo(x, y) : g.moveTo(x, y);
        }
        g.stroke();
      }
      glow(g, p, w / 2, h * 1.1, w * 0.7, (0.3 + 0.05 * Math.sin(sec * 3)) * a);
      break;
    case "waves":
      for (let i = 0; i < 3; i++) {
        g.fillStyle = rgba(i % 2 ? p : q, (0.16 + i * 0.06) * a);
        g.beginPath(); g.moveTo(0, h);
        for (let x = 0; x <= w; x += 6) g.lineTo(x, h * (0.62 + i * 0.1) + Math.sin(x / w * 6 + sec * (0.8 + i * 0.3) + i * 2) * 8);
        g.lineTo(w, h); g.fill();
      }
      break;
    case "leaves":
      lines(16, (pt, i) => {
        const y = ((pt.y + sec * 0.06 * pt.speed) % 1.15 - 0.08) * h, x = (pt.x + Math.sin(sec * 0.8 * pt.speed + pt.phase) * 0.06) * w;
        const rr = 2 + pt.size * 2.5;
        g.fillStyle = rgba(i % 2 ? q : p, 0.55 * a);
        g.beginPath(); g.ellipse(x, y, rr, rr * 0.6, sec + pt.phase, 0, 6.283); g.fill();
      });
      break;
    case "fall":
      lines(24, (pt) => {
        const y = ((pt.y + sec * 1.3 * pt.speed) % 1.3 - 0.3) * h, x = w * (0.2 + pt.x * 0.6), len = h * (0.18 + pt.size * 0.2);
        const grad = g.createLinearGradient(0, y, 0, y + len);
        grad.addColorStop(0, rgba(q, 0)); grad.addColorStop(1, rgba(q, 0.5 * a));
        g.strokeStyle = grad; g.lineWidth = 1 + pt.size * 1.5;
        g.beginPath(); g.moveTo(x, y); g.lineTo(x, y + len); g.stroke();
      });
      glow(g, q, w / 2, h, w * 0.6, 0.22 * a);
      break;
    case "ripple":
      for (let i = 0; i < 5; i++) {
        g.strokeStyle = rgba(q, (0.14 + 0.05 * i) * a); g.lineWidth = 1.4;
        g.beginPath();
        for (let x = 0; x <= w; x += 6) {
          const y = h * (0.45 + i * 0.11) + Math.sin(x / w * 12.5 - sec * (1.4 + i * 0.2) + i) * 3;
          x ? g.lineTo(x, y) : g.moveTo(x, y);
        }
        g.stroke();
      }
      break;
    case "embers":
      glow(g, p, w / 2, h * 1.1, w * 0.75, (0.3 + 0.08 * Math.sin(sec * 7) + 0.06 * Math.sin(sec * 13)) * a);
      lines(20, (pt) => {
        const pr = (pt.y + sec * 0.22 * pt.speed) % 1;
        const x = w * (0.3 + pt.x * 0.4) + Math.sin(sec * 2 * pt.speed + pt.phase) * 10 * pr;
        g.fillStyle = rgba(pr > 0.5 ? p : q, (1 - pr) * 0.85 * a);
        g.beginPath(); g.arc(x, h * (1.05 - pr), 2.4 * (1 - pr) + 0.6, 0, 6.283); g.fill();
      });
      break;
    case "wind":
    case "airflow":
      lines(14, (pt, i) => {
        const x = ((pt.x + sec * 0.25 * pt.speed) % 1.4 - 0.4) * w, y = h * (0.2 + pt.y * 0.7), len = w * (0.2 + pt.size * 0.25);
        g.strokeStyle = rgba(i % 2 ? q : p, 0.4 * a); g.lineWidth = 1.5;
        g.beginPath(); g.moveTo(x, y);
        g.quadraticCurveTo(x + len / 2, y + Math.sin(sec * 2 + pt.phase) * (sound.ambience === "airflow" ? 12 : 5), x + len, y);
        g.stroke();
      });
      break;
    case "fireflies":
      lines(14, (pt) => {
        const x = (pt.x + Math.sin(sec * 0.4 * pt.speed + pt.phase) * 0.08) * w, y = (pt.y + Math.cos(sec * 0.3 * pt.speed + pt.phase) * 0.06) * h;
        const lit = (Math.sin(sec * 1.8 * pt.speed + pt.phase) + 1) / 2;
        glow(g, q, x, y, 9, lit * 0.6 * a);
        g.fillStyle = rgba(q, lit * a); g.beginPath(); g.arc(x, y, 1.6, 0, 6.283); g.fill();
      });
      break;
  }
}

function frame(t) {
  visualFrame = 0;
  if (state.reducedAnimations || document.hidden) return;
  drawAurora(t);
  document.querySelectorAll(".card.on canvas").forEach((cv) => drawAmbience(cv, soundById[cv.parentElement.dataset.id], t));
  if (!$("#step-breathe").hidden) drawFlower(t);
  if (detail.id) drawAmbience($("#sd-canvas"), soundById[detail.id], t, detailIntensity());
  refreshVisuals();
}

// ------------------------------------------------------------------ un momento per te

const relax = { selected: null, breath: 0, breathRun: 0, picked: null };

const breathCues = {
  source: null,
  run: 0,
  async prepare() {
    audio.ensure();
    await audio.ctx.resume();
    await Promise.all([audio.load("respiro_inspira"), audio.load("respiro_espira")]);
  },
  play(inhaling) {
    this.stop();
    const run = this.run;
    const buffer = audio.buffers.get(inhaling ? "respiro_inspira" : "respiro_espira");
    if (!buffer) return;
    // I buffer sono già pronti prima del conto alla rovescia.
    buffer.then((decoded) => {
      if (run !== this.run || document.hidden || $("#step-breathe").hidden) return;
      const source = audio.ctx.createBufferSource();
      source.buffer = decoded;
      source.connect(audio.user);
      this.source = source;
      source.onended = () => { source.disconnect(); if (this.source === source) this.source = null; };
      source.start();
    }).catch(() => {});
  },
  stop() { this.run++; if (this.source) { this.source.stop(); this.source = null; } },
};

function savePassions() { store.set("passions", state.passions); }

function openRelax() {
  $("#home").hidden = true;
  $("#relax").hidden = false;
  renderPlayer();
  showStep(state.passions.length ? "breathe" : "passions");
}

function closeRelax() {
  breathCues.stop();
  relax.breathRun++;
  aurora.override = null;
  $("#relax").hidden = true;
  $("#home").hidden = false;
  render();
}

function showStep(step) {
  breathCues.stop();
  ["passions", "breathe", "reveal"].forEach((s) => { $("#step-" + s).hidden = s !== step; });
  $("#relax-edit").style.visibility = step === "passions" ? "hidden" : "visible";
  relax.breathRun++;
  if (step === "passions") { aurora.override = null; renderPassions(); }
  if (step === "breathe") { aurora.override = ["#5B8DEF", "#8B7CF6", "#14B8A6"]; runBreathing(); }
}

function renderPassions() {
  $("#chips").innerHTML = CATEGORIES.map((c) =>
    `<button class="chip${relax.selected === c.id ? " sel" : ""}" data-cat="${c.id}" style="--p:${c.primary};--s:${c.secondary}">${c.icon} ${c.label}</button>`).join("");
  const form = $("#detail-form");
  const cat = categoryById[relax.selected];
  form.hidden = !cat;
  if (cat) {
    $("#detail-title").textContent = cat.label;
    $("#detail-input").placeholder = "Es. " + cat.example;
  }
  const n = state.passions.length;
  $("#passions-title").textContent = n ? `Le tue passioni (${n})` : "Nessuna passione, per ora";
  $("#passions").innerHTML = state.passions.map((p) => {
    const c = categoryById[p.category] ?? categoryById.ALTRO;
    return `<li><span class="badge" style="--p:${c.primary};--s:${c.secondary}">${c.icon}</span>
      <div><strong>${escapeHtml(p.detail || c.label)}</strong>${p.detail ? `<small>${c.label}</small>` : ""}</div>
      <button class="round" data-remove="${p.id}" aria-label="Rimuovi">✕</button></li>`;
  }).join("");
  const start = $("#btn-start-breath");
  start.disabled = !n;
  start.textContent = n ? "Respira e scopri cosa fare" : "Aggiungi almeno una passione";
}

$("#chips").addEventListener("click", (e) => {
  const b = e.target.closest("[data-cat]");
  if (!b) return;
  relax.selected = relax.selected === b.dataset.cat ? null : b.dataset.cat;
  renderPassions();
  if (relax.selected) $("#detail-input").focus();
});
$("#detail-form").addEventListener("submit", (e) => {
  e.preventDefault();
  if (!relax.selected) return;
  state.passions.push({ id: uid(), category: relax.selected, detail: $("#detail-input").value.trim() });
  savePassions();
  relax.selected = null;
  $("#detail-input").value = "";
  renderPassions();
});
$("#passions").addEventListener("click", (e) => {
  const b = e.target.closest("[data-remove]");
  if (!b) return;
  state.passions = state.passions.filter((p) => p.id !== b.dataset.remove);
  savePassions();
  renderPassions();
});

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function runBreathing() {
  const run = relax.breathRun;
  const alive = () => run === relax.breathRun;
  await breathCues.prepare().catch(() => {});
  if (!alive()) return;
  const phaseEl = $("#phase"), countEl = $("#count"), dots = [...$("#dots").children];
  const setPhase = (label, hint) => {
    phaseEl.innerHTML = `<strong>${label}</strong><small>${hint}</small>`;
    phaseEl.style.animation = "none"; void phaseEl.offsetWidth; phaseEl.style.animation = "";
  };
  dots.forEach((d) => d.classList.remove("done"));
  relax.breath = 0;
  setPhase("Mettiti comodo", "e chiudi gli occhi, se vuoi");
  for (let s = 3; s >= 1; s--) { countEl.textContent = s; await sleep(1000); if (!alive()) return; }
  for (let c = 0; c < BREATH_CYCLES; c++) {
    dots[c].classList.add("done");
    for (const ph of BREATH) {
      breathCues.stop();
      if (ph.to != null) breathCues.play(ph.to === 1);
      setPhase(ph.label, ph.hint);
      if (navigator.vibrate) navigator.vibrate(15);
      const from = relax.breath, start = performance.now(), dur = ph.seconds * 1000;
      const anim = ph.to == null ? null : (now) => {
        const k = Math.min((now - start) / dur, 1);
        const eased = k < 0.5 ? 2 * k * k : 1 - (-2 * k + 2) ** 2 / 2;
        relax.breath = from + (ph.to - from) * eased;
      };
      relax.animate = anim;
      for (let s = ph.seconds; s >= 1; s--) { countEl.textContent = s; await sleep(1000); if (!alive()) return; }
    }
  }
  relax.animate = null;
  breathCues.stop();
  await sleep(400);
  if (alive()) pick();
}

function drawFlower(t) {
  if (relax.animate) relax.animate(performance.now());
  const c = $("#flower"), r = c.getBoundingClientRect(), dpr = Math.min(devicePixelRatio || 1, 2);
  if (c.width !== Math.round(r.width * dpr) || c.height !== Math.round(r.height * dpr)) { c.width = r.width * dpr; c.height = r.height * dpr; }
  const g = c.getContext("2d");
  g.setTransform(dpr, 0, 0, dpr, 0, 0);
  g.clearRect(0, 0, r.width, r.height);
  const b = relax.breath, cx = r.width / 2, cy = r.height / 2, maxR = Math.min(r.width, r.height, 340) / 2;
  const spin = t / 40000 * Math.PI * 2, shimmer = (Math.sin(t / 3000 * Math.PI) + 1) / 2;
  glow(g, hexToRgb("#8B7CF6"), cx, cy, maxR * (0.7 + 0.3 * b), 0.35 + 0.2 * b);
  ["#5B8DEF", "#8B7CF6", "#14B8A6", "#5EC8F2", "#C4BCFF", "#99F6E4"].forEach((col, i) => {
    const a = spin * 0.6 + i * Math.PI / 3 + b * 0.5, dist = maxR * (0.05 + 0.33 * b), pr = maxR * (0.3 + 0.16 * b);
    const x = cx + Math.cos(a) * dist, y = cy + Math.sin(a) * dist, rgb = hexToRgb(col);
    const grad = g.createRadialGradient(x, y, 0, x, y, pr);
    grad.addColorStop(0, rgba(rgb, 0.55)); grad.addColorStop(1, rgba(rgb, 0.12));
    g.fillStyle = grad; g.beginPath(); g.arc(x, y, pr, 0, 6.283); g.fill();
  });
  g.strokeStyle = `rgba(255,255,255,${0.25 + 0.15 * shimmer})`; g.lineWidth = 1;
  g.beginPath(); g.arc(cx, cy, maxR * (0.8 + 0.1 * b), 0, 6.283); g.stroke();
  particles.slice(0, 18).forEach((pt) => {
    const a = pt.phase + spin * (0.8 + pt.size * 0.2), rr = maxR * (0.75 + pt.size * 0.35) * (0.7 + 0.3 * b);
    g.fillStyle = `rgba(255,255,255,${0.35 + 0.4 * shimmer * b})`;
    g.beginPath(); g.arc(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr, 1 + pt.speed, 0, 6.283); g.fill();
  });
}

async function pick() {
  const last = store.get("lastPassion", null);
  const pool = state.passions.filter((p) => p.id !== last);
  const list = pool.length ? pool : state.passions;
  const chosen = list[Math.floor(Math.random() * list.length)];
  if (!chosen) return showStep("passions");
  store.set("lastPassion", chosen.id);
  relax.picked = chosen;
  showStep("reveal");
  const run = relax.breathRun;
  const badge = $("#reveal-badge"), title = $("#reveal-title"), idea = $("#reveal-idea"), actions = $("#reveal-actions");
  const show = (p) => {
    const c = categoryById[p.category] ?? categoryById.ALTRO;
    badge.style.cssText = `--p:${c.primary};--s:${c.secondary}`;
    $("#halo").style.cssText = `--p:${c.primary}`;
    badge.textContent = c.icon;
    title.textContent = p.detail || c.label;
    return c;
  };
  idea.hidden = true;
  actions.style.visibility = "hidden";
  $("#btn-another").hidden = state.passions.length < 2;
  // Breve "rullo": le passioni scorrono rallentando, poi si ferma su quella scelta.
  if (state.passions.length > 1) {
    let wait = 60, i = state.passions.indexOf(chosen) + 1;
    for (let k = 0; k < 12; k++) {
      show(state.passions[i++ % state.passions.length]);
      badge.classList.remove("spin"); void badge.offsetWidth; badge.classList.add("spin");
      await sleep(wait);
      if (run !== relax.breathRun) return;
      wait *= 1.22;
    }
  }
  const c = show(chosen);
  aurora.override = [c.primary, c.secondary, c.primary];
  badge.classList.remove("spin", "land"); void badge.offsetWidth; badge.classList.add("land");
  if (navigator.vibrate) navigator.vibrate(30);
  const template = c.ideas[Math.floor(Math.random() * c.ideas.length)];
  idea.textContent = template.replace("%s", chosen.detail || c.fallback);
  idea.hidden = false;
  actions.style.visibility = "visible";
}

$("#btn-relax").addEventListener("click", openRelax);
$("#relax-close").addEventListener("click", closeRelax);
$("#relax-edit").addEventListener("click", () => showStep("passions"));
$("#btn-start-breath").addEventListener("click", () => showStep("breathe"));
$("#btn-skip").addEventListener("click", () => { breathCues.stop(); relax.breathRun++; pick(); });
$("#btn-done").addEventListener("click", closeRelax);
$("#btn-another").addEventListener("click", pick);
$("#btn-again").addEventListener("click", () => showStep("breathe"));
document.addEventListener("keydown", (e) => { if (e.key === "Escape" && !$("#relax").hidden && !document.querySelector("dialog[open]")) closeRelax(); });
document.addEventListener("visibilitychange", () => {
  if ($("#relax").hidden || $("#step-breathe").hidden) return;
  if (document.hidden) { breathCues.stop(); relax.breathRun++; }
  else showStep("breathe");
});

// ------------------------------------------------------------------ volume generale

/** Curva percettiva: a metà cursore il volume "sembra" a metà. */
function perceptual(v) { return v * v; }

const VOL_ICONS = { off: "M16.5 12A4.5 4.5 0 0 0 14 8v2.2l2.5 2.5V12Zm2.5 0c0 .9-.2 1.8-.5 2.6l1.5 1.5A8.9 8.9 0 0 0 21 12c0-4.3-3-7.9-7-8.8v2.1c2.9.9 5 3.5 5 6.7ZM4.3 3 3 4.3 7.7 9H3v6h4l5 5v-6.7l4.3 4.3c-.7.5-1.4.9-2.3 1.2v2.1a9 9 0 0 0 3.7-1.8l2 2 1.3-1.3-9-9L4.3 3ZM12 4 9.9 6.1 12 8.2V4Z", down: "M18.5 12A4.5 4.5 0 0 0 16 8v8a4.5 4.5 0 0 0 2.5-4ZM5 9v6h4l5 5V4L9 9H5Z", up: "M3 9v6h4l5 5V4L7 9H3Zm13.5 3A4.5 4.5 0 0 0 14 8v8a4.5 4.5 0 0 0 2.5-4ZM14 3.2v2.1c2.9.9 5 3.5 5 6.7s-2.1 5.8-5 6.7v2.1c4-.9 7-4.5 7-8.8s-3-7.9-7-8.8Z" };

function setMasterVolume(v) {
  state.masterVolume = Math.min(1, Math.max(0, v));
  store.set("masterVolume", state.masterVolume);
  audio.setUserVolume(state.masterVolume);
  renderMaster();
}

function renderMaster() {
  const v = state.masterVolume;
  const btn = $("#btn-volume");
  $("#knob-level").style.strokeDashoffset = String(75.4 * (1 - v));
  $("#vol-icon").setAttribute("d", v < 0.01 ? VOL_ICONS.off : v < 0.5 ? VOL_ICONS.down : VOL_ICONS.up);
  btn.classList.toggle("muted", v < 0.01);
  btn.setAttribute("aria-label", `Volume generale ${Math.round(v * 100)}%`);
  if (!$("#dlg-volume").open) return;
  const range = $("#master-range");
  if (document.activeElement !== range) range.value = v;
  range.style.setProperty("--fill", `${v * 100}%`);
  $("#master-pct").textContent = `${Math.round(v * 100)}%`;
  document.querySelectorAll("#master-chips [data-level]").forEach((b) =>
    b.classList.toggle("sel", Math.round(Number(b.dataset.level) * 100) === Math.round(v * 100)));
  const ids = [...state.active];
  $("#master-mix").innerHTML = ids.length ? `<span class="sd-label">Come esce ogni suono</span>` + ids.map((id) => {
    const s = soundById[id], own = volumeOf(id), eff = own * v;
    return `<div class="mixrow" style="--grad:linear-gradient(90deg,${s.primary},${s.secondary})"><div class="stack">${stackHtml([id])}</div>
      <span>${s.name}</span><div class="bar"><b style="width:${own * 100}%"></b><i style="width:${eff * 100}%"></i></div><span>${Math.round(eff * 100)}%</span></div>`;
  }).join("") : "";
}

$("#btn-volume").addEventListener("click", () => { $("#dlg-volume").showModal(); renderMaster(); });
$("#master-range").addEventListener("input", (e) => setMasterVolume(Number(e.target.value)));
$("#master-chips").addEventListener("click", (e) => {
  const b = e.target.closest("[data-level]");
  if (b) { setMasterVolume(Number(b.dataset.level)); $("#master-range").value = state.masterVolume; }
});

// ------------------------------------------------------------------ dettaglio del suono

const detail = { id: null, pressTimer: 0, suppressClick: false };

function detailIntensity() {
  const live = state.active.has(detail.id) && state.playing;
  return live ? 0.55 + 0.45 * volumeOf(detail.id) : 0.45;
}

function openSoundDetail(id, card) {
  const dlg = $("#dlg-sound");
  if (dlg.open) return;
  const s = soundById[id], info = SOUND_INFO[id];
  detail.id = id;
  // La finestra parte dalla posizione della card, così sembra uscire da lì.
  const r = card.getBoundingClientRect();
  dlg.style.cssText = `--p:${s.primary};--s:${s.secondary};--from-x:${Math.round(r.left + r.width / 2 - innerWidth / 2)}px;--from-y:${Math.round(r.top + r.height / 2 - innerHeight / 2)}px`;
  $("#sd-icon").textContent = s.icon;
  $("#sd-name").textContent = s.name;
  $("#sd-desc").textContent = info.desc;
  $("#sd-moods").innerHTML = info.moods.map((m) => `<span>${MOODS[m]}</span>`).join("");
  renderSoundDetail();
  if (navigator.vibrate) navigator.vibrate(12);
  dlg.showModal();
}

function renderSoundDetail() {
  if (!detail.id) return;
  const s = soundById[detail.id], info = SOUND_INFO[detail.id];
  const on = state.active.has(detail.id);
  // Stato ben visibile: acceso (con equalizzatore) o spento.
  const live = on && state.playing;
  const label = live ? "Acceso · in riproduzione" : on ? "Acceso · in pausa" : "Spento";
  if ($("#sd-state").textContent !== label) $("#sd-state").textContent = label;
  $("#sd-pill").classList.toggle("on", on);
  $("#sd-eq").hidden = !on;
  $("#sd-eq").classList.toggle("on", live);
  $("#sd-pairs").innerHTML = info.pairs.map((pid) => {
    const p = soundById[pid], pon = state.active.has(pid);
    return `<button class="${pon ? "on" : ""}" data-pair="${pid}" style="--p:${p.primary};--s:${p.secondary}">
      <span class="badge">${p.icon}</span><strong>${p.name}</strong><small>${pon ? "Acceso" : "Aggiungi"}</small></button>`;
  }).join("");
  // Il volume resta sempre regolabile: a suono spento sarà quello con cui ripartirà.
  $("#sd-volume-hint").hidden = on;
  $("#sd-range").value = volumeOf(detail.id);
  $("#sd-volume-label").textContent = Math.round(volumeOf(detail.id) * 100) + "%";
  const toggle = $("#sd-toggle");
  toggle.textContent = (on ? "Spegni " : "Accendi ") + s.name.toLowerCase();
  toggle.classList.toggle("on", on);
}

function closeSoundDetail() {
  const dlg = $("#dlg-sound");
  if (!dlg.open || dlg.classList.contains("closing")) return;
  dlg.classList.add("closing");
  setTimeout(() => { dlg.classList.remove("closing"); dlg.close(); detail.id = null; }, state.reducedAnimations ? 0 : 250);
}

(function bindSoundDetail() {
  const dlg = $("#dlg-sound");
  // Chiusura animata: con la X, con Esc o toccando fuori dalla finestra.
  dlg.addEventListener("click", (e) => {
    if (e.target.closest("[data-close]") || e.target === dlg) { e.stopPropagation(); closeSoundDetail(); }
  }, true);
  dlg.addEventListener("cancel", (e) => { e.preventDefault(); closeSoundDetail(); });
  $("#sd-pairs").addEventListener("click", (e) => {
    const b = e.target.closest("[data-pair]");
    if (b) toggleSound(b.dataset.pair);
  });
  $("#sd-toggle").addEventListener("click", () => toggleSound(detail.id));
  $("#sd-range").addEventListener("input", (e) => {
    setVolume(detail.id, Number(e.target.value));
    $("#sd-volume-label").textContent = Math.round(Number(e.target.value) * 100) + "%";
    const cardRange = document.querySelector(`.card[data-id="${detail.id}"] .vol`);
    if (cardRange) cardRange.value = e.target.value;
  });
})();

// ------------------------------------------------------------------ avvio

// ------------------------------------------------------------------ backup

function rememberMix() {
  if (state.active.size) store.set("lastMix", Object.fromEntries([...state.active].map((id) => [id, volumeOf(id)])));
}

function backupData() {
  return {
    presets: state.presets, passions: state.passions, volumes: state.volumes,
    masterVolume: state.masterVolume,
    lastMix: state.active.size ? Object.fromEntries([...state.active].map((id) => [id, volumeOf(id)])) : store.get("lastMix", {}),
    lastTimerMinutes: store.get("lastTimer", 30), onboardingDone: store.get("onboardingDone", true),
    lastPassionId: store.get("lastPassion", null),
  };
}

function restoreBackup(data) {
  const values = {
    presets: data.presets, passions: data.passions, volumes: data.volumes, masterVolume: data.masterVolume,
    lastMix: data.lastMix, lastTimer: data.lastTimerMinutes, onboardingDone: data.onboardingDone, lastPassion: data.lastPassionId,
  };
  const previous = new Map(Object.keys(values).map((key) => [key, localStorage.getItem("chiarella." + key)]));
  try {
    // Non usare store.set qui: un errore di scrittura deve annullare l'importazione.
    Object.entries(values).forEach(([key, value]) => localStorage.setItem("chiarella." + key, JSON.stringify(value)));
  } catch {
    previous.forEach((value, key) => {
      if (value === null) localStorage.removeItem("chiarella." + key);
      else localStorage.setItem("chiarella." + key, value);
    });
    throw new Error("Impossibile salvare il backup. Controlla lo spazio disponibile nel browser.");
  }
  stopAll();
  state.presets = data.presets;
  state.passions = data.passions;
  state.volumes = data.volumes;
  state.masterVolume = data.masterVolume;
  audio.setUserVolume(data.masterVolume);
  range.value = data.lastTimerMinutes;
  range.dispatchEvent(new Event("input"));
  render();
}

let pendingBackup = null;
function resetBackupImport() {
  pendingBackup = null;
  $("#backup-confirm").hidden = true;
  $("#backup-file").value = "";
}
$("#btn-backup").addEventListener("click", () => {
  resetBackupImport();
  $("#backup-message").textContent = "";
  $("#dlg-backup").showModal();
});
$("#dlg-backup").addEventListener("close", resetBackupImport);
$("#backup-export").addEventListener("click", () => {
  try {
    const blob = new Blob([ChiarellaBackup.stringify(backupData())], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `Chiarella-backup-${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
    $("#backup-message").textContent = "Backup esportato. Conservalo per ripristinare i tuoi dati su qualsiasi dispositivo.";
  } catch (e) { $("#backup-message").textContent = e.message; }
});
$("#backup-import").addEventListener("click", () => { resetBackupImport(); $("#backup-file").click(); });
$("#backup-file").addEventListener("change", async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  try {
    if (file.size > ChiarellaBackup.MAX_BYTES) throw new Error("Il file è troppo grande (massimo 2 MB).");
    const data = ChiarellaBackup.parse(await file.text());
    if (!$("#dlg-backup").open) return;
    pendingBackup = data;
    $("#backup-message").textContent = `Backup pronto: ${data.presets.length} profili e ${data.passions.length} passioni, con volumi e preferenze.`;
    $("#backup-confirm").hidden = false;
    $("#backup-restore").focus();
  } catch (error) {
    resetBackupImport();
    $("#backup-message").textContent = error.message;
  }
});
$("#backup-cancel").addEventListener("click", () => { resetBackupImport(); $("#backup-message").textContent = "Importazione annullata. I dati presenti sono stati mantenuti."; });
$("#backup-restore").addEventListener("click", () => {
  if (!pendingBackup) return;
  try {
    restoreBackup(pendingBackup);
    resetBackupImport();
    $("#backup-message").textContent = "Backup importato. Profili, passioni e preferenze sono pronti.";
    $("#backup-import").focus();
  } catch (e) { $("#backup-message").textContent = e.message; }
});

async function prepareSounds() {
  const loader = $("#sound-loader"), retry = $("#loader-retry"), progress = $("#loader-progress");
  const restoreFocus = document.activeElement === retry;
  retry.hidden = true;
  loader.setAttribute("aria-busy", "true");
  $("#loader-title").textContent = "Prepariamo la tua quiete";
  $("#loader-description").textContent = "Carichiamo i suoni sul tuo dispositivo. La prima volta può volerci un momento.";
  progress.max = SOUNDS.length;
  progress.value = 0;
  let completed = 0;
  const pending = [...SOUNDS];
  $("#loader-status").textContent = `0 di ${SOUNDS.length} suoni pronti`;
  // Due download alla volta, anche per non sovraccaricare le connessioni mobili.
  const worker = async () => {
    while (pending.length) {
      const sound = pending.shift();
      await audio.download(sound.id);
      progress.value = ++completed;
      $("#loader-status").textContent = `${completed} di ${SOUNDS.length} suoni pronti`;
    }
  };
  const results = await Promise.allSettled([worker(), worker()]);
  loader.setAttribute("aria-busy", "false");
  if (results.some((result) => result.status === "rejected")) {
    $("#loader-title").textContent = "Il caricamento si è interrotto";
    $("#loader-description").textContent = "Controlla la connessione e riprova. I suoni già caricati non verranno scaricati di nuovo.";
    retry.hidden = false;
    retry.focus();
    return;
  }
  soundsReady = true;
  // Conserva anche i due segnali per un successivo esercizio offline.
  Promise.allSettled([audio.download("respiro_inspira"), audio.download("respiro_espira")]);
  $("#home").inert = false;
  $("#home").setAttribute("aria-busy", "false");
  document.body.classList.remove("loading-sounds");
  loader.hidden = true;
  if (restoreFocus) $("#btn-help").focus();
}

$("#loader-retry").addEventListener("click", prepareSounds);
buildGrid();
render();
applyPreferences();
document.addEventListener("visibilitychange", () => {
  if (document.hidden) { cancelAnimationFrame(visualFrame); visualFrame = 0; }
  else refreshVisuals();
});
prepareSounds();
if ("serviceWorker" in navigator && location.protocol.startsWith("http")) {
  navigator.serviceWorker.register("sw.js").catch(() => { /* offline non disponibile */ });
}
