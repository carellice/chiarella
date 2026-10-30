using NAudio.CoreAudioApi;
using NAudio.Wave;
using NAudio.Wave.SampleProviders;
using NVorbis;

namespace Chiarella.Audio;

/// <summary>Segnali singoli del respiro, decodificati prima del conto alla rovescia.</summary>
internal sealed class BreathAudio : IDisposable
{
    private readonly MixingSampleProvider _mix = new(WaveFormat.CreateIeeeFloatWaveFormat(44100, 2)) { ReadFully = true };
    private readonly Dictionary<bool, float[]> _samples = [];
    private readonly WasapiOut _output = new(AudioClientShareMode.Shared, true, 100);

    public BreathAudio()
    {
        foreach (var inhaling in new[] { true, false })
        {
            using var reader = new VorbisReader(Path.Combine(AppContext.BaseDirectory, "Sounds",
                inhaling ? "respiro_inspira.ogg" : "respiro_espira.ogg"));
            var samples = new List<float>();
            var buffer = new float[4096];
            int read;
            while ((read = reader.ReadSamples(buffer, 0, buffer.Length)) > 0) samples.AddRange(buffer.Take(read));
            _samples[inhaling] = samples.ToArray();
        }
        _output.Init(_mix);
    }

    public void Play(bool inhaling, double volume)
    {
        Stop();
        _mix.AddMixerInput(new Cue(_samples[inhaling], (float)(volume * volume)));
        _output.Play();
    }

    public void Stop() { _output.Stop(); _mix.RemoveAllMixerInputs(); }
    public void Dispose() { Stop(); _output.Dispose(); }

    private sealed class Cue(float[] samples, float gain) : ISampleProvider
    {
        private int _position;
        public WaveFormat WaveFormat { get; } = WaveFormat.CreateIeeeFloatWaveFormat(44100, 2);
        public int Read(float[] buffer, int offset, int count)
        {
            var read = Math.Min(count, samples.Length - _position);
            for (var i = 0; i < read; i++) buffer[offset + i] = samples[_position++] * gain;
            return read;
        }
    }
}
