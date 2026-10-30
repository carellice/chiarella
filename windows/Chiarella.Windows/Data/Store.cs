using System.Text.Json;

namespace Chiarella.Data;

/// <summary>Profilo: un insieme di suoni, ciascuno con il proprio volume (0..1).</summary>
public sealed class Preset
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Name { get; set; } = "";
    public Dictionary<string, double> Mix { get; set; } = [];
}

/// <summary>Una passione dell'utente: una categoria e, facoltativo, un dettaglio ("Zelda", "Breaking Bad"…).</summary>
public sealed class Passion
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Category { get; set; } = "ALTRO";
    public string Detail { get; set; } = "";

    [System.Text.Json.Serialization.JsonIgnore]
    public string Title => string.IsNullOrWhiteSpace(Detail) ? Catalog.Category(Category).Label : Detail;
}

/// <summary>Persistenza leggera: un file JSON in %LOCALAPPDATA%\Chiarella.</summary>
public sealed class Store
{
    public List<Preset> Presets { get; set; } = [];
    public List<Passion> Passions { get; set; } = [];
    public Dictionary<string, double> Volumes { get; set; } = [];
    public Dictionary<string, double> LastMix { get; set; } = [];
    public int LastTimerMinutes { get; set; } = 30;
    /// <summary>Volume generale dell'app (0..1): moltiplica quello dei singoli suoni senza cambiarlo.</summary>
    public double MasterVolume { get; set; } = 1;
    public string? LastPassionId { get; set; }
    public bool HelpSeen { get; set; }
    public bool ShowVolumeNotice { get; set; } = true;
    public bool ReducedAnimations { get; set; }

    private static readonly string Folder =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Chiarella");
    private static readonly string FilePath = Path.Combine(Folder, "chiarella.json");
    private static readonly JsonSerializerOptions Options = new() { WriteIndented = true };

    public double Volume(string id) => Volumes.GetValueOrDefault(id, 0.7);

    public BackupData BackupData() => new(Presets, Passions, Volumes, MasterVolume, LastMix,
        LastTimerMinutes, HelpSeen, LastPassionId);

    public void ImportBackup(BackupData data)
    {
        var replacement = new Store
        {
            ShowVolumeNotice = ShowVolumeNotice, ReducedAnimations = ReducedAnimations,
            Presets = data.Presets, Passions = data.Passions, Volumes = data.Volumes,
            MasterVolume = data.MasterVolume, LastMix = data.LastMix,
            LastTimerMinutes = data.LastTimerMinutes, HelpSeen = data.OnboardingDone, LastPassionId = data.LastPassionId,
        };
        // Salva prima di cambiare lo stato vivo: un disco pieno non cancella i dati correnti.
        replacement.SaveChecked();
        Presets = replacement.Presets;
        Passions = replacement.Passions;
        Volumes = replacement.Volumes;
        MasterVolume = replacement.MasterVolume;
        LastMix = replacement.LastMix;
        LastTimerMinutes = replacement.LastTimerMinutes;
        HelpSeen = replacement.HelpSeen;
        LastPassionId = replacement.LastPassionId;
    }

    public static Store Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<Store>(File.ReadAllText(FilePath), Options) ?? new Store();
        }
        catch (Exception)
        {
            // File rovinato: si riparte da zero piuttosto che non aprire l'app.
        }
        return new Store();
    }

    public void Save()
    {
        try
        {
            SaveChecked();
        }
        catch (IOException)
        {
            // Disco pieno o file bloccato: si riprova al prossimo salvataggio.
        }
    }

    private void SaveChecked()
    {
        Directory.CreateDirectory(Folder);
        var temporary = FilePath + ".tmp";
        try
        {
            File.WriteAllText(temporary, JsonSerializer.Serialize(this, Options));
            File.Move(temporary, FilePath, overwrite: true);
        }
        finally { if (File.Exists(temporary)) File.Delete(temporary); }
    }
}
