using NAudio.CoreAudioApi;

namespace Chiarella.Audio;

internal sealed record SystemVolumeState(float Level, bool Muted)
{
    public bool NeedsIncrease => Muted || Level < 0.9999f;
    public int Percent => Muted ? 0 : NeedsIncrease ? Math.Min(99, (int)Math.Round(Level * 100)) : 100;
}

internal static class SystemVolume
{
    public static SystemVolumeState? Inspect()
    {
        try
        {
            using var enumerator = new MMDeviceEnumerator();
            // Stessa uscita predefinita usata da WasapiOut nel mixer.
            using var device = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Console);
            return new(device.AudioEndpointVolume.MasterVolumeLevelScalar, device.AudioEndpointVolume.Mute);
        }
        catch (Exception ex) { System.Diagnostics.Debug.WriteLine(ex); return null; }
    }

    // Chiamato soltanto dal pulsante dell'avviso, mai durante il controllo iniziale.
    public static SystemVolumeState? RaiseToMaximum()
    {
        try
        {
            using var enumerator = new MMDeviceEnumerator();
            using var device = enumerator.GetDefaultAudioEndpoint(DataFlow.Render, Role.Console);
            device.AudioEndpointVolume.MasterVolumeLevelScalar = 1f;
            device.AudioEndpointVolume.Mute = false;
        }
        catch (Exception ex) { System.Diagnostics.Debug.WriteLine(ex); }
        return Inspect();
    }
}
