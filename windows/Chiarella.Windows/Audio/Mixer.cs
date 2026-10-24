using Chiarella.Data;
using Microsoft.UI.Dispatching;
using NAudio.CoreAudioApi;
using NAudio.Wave;
using NAudio.Wave.SampleProviders;
using NVorbis;

namespace Chiarella.Audio;

/// <summary>Volume che si muove verso un obiettivo campione per campione: dissolvenze senza scatti.</summary>
internal sealed class Ramp(float value)
{
    private float _target = value;
    private float _step;

    public float Value { get; private set; } = value;
    public bool Silent => Value <= 0f && _target <= 0f;

    public void To(float target, double seconds, int sampleRate)
    {
        _target = target;
        _step = (float)(Math.Abs(target - Value) / Math.Max(1, seconds * sampleRate));
        if (_step == 0) Value = target;
    }

    public float Next()
    {
        if (Value < _target) Value = Math.Min(_target, Value + _step);
        else if (Value > _target) Value = Math.Max(_target, Value - _step);
        return Value;
    }
}

/// <summary>
/// Un suono in loop infinito: il file Vorbis viene riletto dall'inizio appena finisce,
/// a livello di campione, quindi senza pause (i file sono già preparati per non avere giunture).
/// </summary>
internal sealed class LoopVoice : ISampleProvider, IDisposable
{
    private readonly VorbisReader _reader;
    private readonly Ramp _gain = new(0f);
    private readonly object _lock = new();
    private bool _stopping;
    private bool _disposed;

    public string Id { get; }
    public WaveFormat WaveFormat { get; }

    public LoopVoice(string id, string path)
    {
        Id = id;
        _reader = new VorbisReader(path);
        WaveFormat = WaveFormat.CreateIeeeFloatWaveFormat(_reader.SampleRate, _reader.Channels);
        // Ogni suono parte da un punto a caso: due mix uguali non suonano mai identici.
        _reader.SamplePosition = (long)(Random.Shared.NextDouble() * Math.Max(0, _reader.TotalSamples - 1));
    }

    public void FadeTo(float volume, double seconds)
    {
        lock (_lock) _gain.To(volume, seconds, WaveFormat.SampleRate);
    }

    public void FadeOutAndStop(double seconds)
    {
        lock (_lock)
        {
            _stopping = true;
            _gain.To(0f, seconds, WaveFormat.SampleRate);
        }
    }

    public int Read(float[] buffer, int offset, int count)
    {
        lock (_lock)
        {
            // Spento e già sfumato: restituendo 0 il mixer lo rimuove.
            if (_disposed || (_stopping && _gain.Silent)) return 0;

            var filled = 0;
            var rewinds = 0;
            while (filled < count)
            {
                var n = _reader.ReadSamples(buffer, offset + filled, count - filled);
                if (n > 0) { filled += n; continue; }
                if (++rewinds > 2) break; // file vuoto o illeggibile
                _reader.SamplePosition = 0;
            }
            if (filled < count) Array.Clear(buffer, offset + filled, count - filled);

            var channels = WaveFormat.Channels;
            for (var i = 0; i < count; i += channels)
            {
                var g = _gain.Next();
                for (var c = 0; c < channels; c++) buffer[offset + i + c] *= g;
            }
            return count;
        }
    }

    public void Dispose()
    {
        lock (_lock)
        {
            _disposed = true;
            _reader.Dispose();
        }
    }
}

/// <summary>Guadagno generale: serve per pausa/ripresa morbide e per la dissolvenza del timer.</summary>
internal sealed class MasterGain(ISampleProvider source) : ISampleProvider
{
    private readonly Ramp _gain = new(0f);
    private readonly object _lock = new();

    public WaveFormat WaveFormat => source.WaveFormat;

    public void FadeTo(float volume, double seconds)
    {
        lock (_lock) _gain.To(volume, seconds, WaveFormat.SampleRate);
    }

    public int Read(float[] buffer, int offset, int count)
    {
        var n = source.Read(buffer, offset, count);
        lock (_lock)
        {
            var channels = WaveFormat.Channels;
            for (var i = 0; i < n; i += channels)
            {
                var g = _gain.Next();
                for (var c = 0; c < channels; c++) buffer[offset + i + c] *= g;
            }
        }
        return n;
    }
}

/// <summary>
/// Motore audio: un unico flusso WASAPI in cui vengono mescolati i suoni accesi,
/// ciascuno con il suo volume, più timer di spegnimento con dissolvenza finale.
/// </summary>
public sealed class Mixer : IDisposable
{
    private const int SampleRate = 44100;
    private static readonly TimeSpan FinalFade = TimeSpan.FromSeconds(12);

    private readonly Store _store;
    private readonly string _soundsFolder = Path.Combine(AppContext.BaseDirectory, "Sounds");
    private readonly MixingSampleProvider _mix = new(WaveFormat.CreateIeeeFloatWaveFormat(SampleRate, 2)) { ReadFully = true };
    private readonly MasterGain _master;
    // Volume generale scelto dall'utente: separato dal guadagno usato per pausa e timer.
    private readonly MasterGain _user;
    private readonly Dictionary<string, LoopVoice> _voices = [];
    private readonly DispatcherQueueTimer _ticker;
    private WasapiOut? _output;
    private bool _fadingOut;

    public event Action? Changed;

    public IReadOnlyCollection<string> Active => _voices.Keys;
    public bool HasMix => _voices.Count > 0;
    public bool Playing { get; private set; }
    public string? PresetId { get; private set; }
    public DateTime? TimerEndsAt { get; private set; }
    public TimeSpan TimerDuration { get; private set; }

    public Mixer(Store store)
    {
        _store = store;
        _master = new MasterGain(_mix);
        _user = new MasterGain(_master);
        _user.FadeTo(Perceptual(store.MasterVolume), 0);
        _ticker = DispatcherQueue.GetForCurrentThread().CreateTimer();
        _ticker.Interval = TimeSpan.FromSeconds(1);
        _ticker.Tick += (_, _) => TickTimer();
    }

    public bool IsActive(string id) => _voices.ContainsKey(id);

    public void Toggle(string id)
    {
        PresetId = null;
        if (_voices.ContainsKey(id)) Remove(id);
        else
        {
            Add(id, _store.Volume(id));
            if (!Playing) Play();
        }
        if (_voices.Count == 0) StopAll();
        else
        {
            _store.LastMix = _voices.Keys.ToDictionary(key => key, key => _store.Volume(key));
            Changed?.Invoke();
        }
    }

    public void SetVolume(string id, double volume)
    {
        _store.Volumes[id] = volume;
        if (HasMix) _store.LastMix = _voices.Keys.ToDictionary(key => key, key => _store.Volume(key));
        if (_voices.TryGetValue(id, out var voice)) voice.FadeTo((float)volume, 0.08);
        PresetId = null;
        Changed?.Invoke();
    }

    public void ApplyPreset(Preset preset)
    {
        // Toccare il profilo che sta già suonando lo mette in pausa (e viceversa).
        if (PresetId == preset.Id)
        {
            TogglePlay();
            return;
        }
        foreach (var id in _voices.Keys.Where(id => !preset.Mix.ContainsKey(id)).ToList()) Remove(id);
        foreach (var (id, volume) in preset.Mix)
        {
            if (Catalog.Sound(id) is null) continue;
            _store.Volumes[id] = volume;
            if (_voices.TryGetValue(id, out var voice)) voice.FadeTo((float)volume, 0.6);
            else Add(id, volume);
        }
        PresetId = preset.Id;
        Play();
    }

    public double MasterVolume => _store.MasterVolume;

    /// <summary>Alza o abbassa tutta l'app insieme; i volumi dei singoli suoni (e dei profili) restano invariati.</summary>
    public void SetMasterVolume(double volume)
    {
        _store.MasterVolume = Math.Clamp(volume, 0, 1);
        _user.FadeTo(Perceptual(_store.MasterVolume), 0.08);
        Changed?.Invoke();
    }

    /// <summary>Curva percettiva: a metà cursore il volume "sembra" a metà.</summary>
    private static float Perceptual(double v) => (float)(v * v);

    public void Play()
    {
        if (_voices.Count == 0) return;
        _store.LastMix = _voices.Keys.ToDictionary(id => id, id => _store.Volume(id));
        EnsureOutput();
        Playing = true;
        _master.FadeTo(_fadingOut ? 0f : 1f, 0.6);
        if (_output!.PlaybackState != PlaybackState.Playing) _output.Play();
        Changed?.Invoke();
    }

    public async void Pause()
    {
        Playing = false;
        _master.FadeTo(0f, 0.4);
        Changed?.Invoke();
        await Task.Delay(450);
        if (!Playing) _output?.Pause();
    }

    public void TogglePlay()
    {
        if (Playing) Pause(); else Play();
    }

    public void StopAll()
    {
        foreach (var id in _voices.Keys.ToList()) Remove(id, 0.8);
        PresetId = null;
        CancelTimer();
        Playing = false;
        Changed?.Invoke();
    }

    public void ReloadFromStore()
    {
        StopAll();
        _user.FadeTo(Perceptual(_store.MasterVolume), 0.08);
        Changed?.Invoke();
    }

    public void StartTimer(int minutes)
    {
        _store.LastTimerMinutes = minutes;
        _store.Save();
        TimerDuration = TimeSpan.FromMinutes(minutes);
        TimerEndsAt = DateTime.Now + TimerDuration;
        _fadingOut = false;
        _ticker.Start();
        if (!Playing) Play();
        Changed?.Invoke();
    }

    public void ExtendTimer(int minutes)
    {
        if (TimerEndsAt is null)
        {
            StartTimer(minutes);
            return;
        }
        TimerEndsAt += TimeSpan.FromMinutes(minutes);
        TimerDuration += TimeSpan.FromMinutes(minutes);
        if (_fadingOut)
        {
            _fadingOut = false;
            if (Playing) _master.FadeTo(1f, 1);
        }
        Changed?.Invoke();
    }

    public void CancelTimer()
    {
        _ticker.Stop();
        if (_fadingOut && Playing) _master.FadeTo(1f, 1);
        _fadingOut = false;
        TimerEndsAt = null;
        TimerDuration = TimeSpan.Zero;
        Changed?.Invoke();
    }

    private void TickTimer()
    {
        if (TimerEndsAt is not { } end) return;
        var remaining = end - DateTime.Now;
        if (remaining <= FinalFade && !_fadingOut && Playing)
        {
            _fadingOut = true;
            _master.FadeTo(0f, Math.Max(0, remaining.TotalSeconds));
        }
        if (remaining <= TimeSpan.Zero)
        {
            CancelTimer();
            Pause();
            return;
        }
        Changed?.Invoke();
    }

    private void Add(string id, double volume)
    {
        var path = Path.Combine(_soundsFolder, id + ".ogg");
        if (!File.Exists(path)) return;
        var voice = new LoopVoice(id, path);
        voice.FadeTo((float)volume, 1.2);
        _voices[id] = voice;
        _mix.AddMixerInput(voice);
    }

    private void Remove(string id, double fade = 1.0)
    {
        if (!_voices.Remove(id, out var voice)) return;
        voice.FadeOutAndStop(fade);
        // Il mixer lo scarta da solo a fine dissolvenza; il file si chiude poco dopo.
        _ = Task.Delay(TimeSpan.FromSeconds(fade + 1)).ContinueWith(_ =>
        {
            _mix.RemoveMixerInput(voice);
            voice.Dispose();
        });
    }

    private void EnsureOutput()
    {
        if (_output is not null) return;
        _output = new WasapiOut(AudioClientShareMode.Shared, true, 150);
        _output.Init(_user);
    }

    public void Dispose()
    {
        _ticker.Stop();
        _output?.Stop();
        _output?.Dispose();
        foreach (var voice in _voices.Values) voice.Dispose();
    }
}
