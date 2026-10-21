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
    public int LastTimerMinutes { get; set; } = 30;
    public string? LastPassionId { get; set; }
    public bool HelpSeen { get; set; }

    private static readonly string Folder =
        Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Chiarella");
    private static readonly string FilePath = Path.Combine(Folder, "chiarella.json");
    private static readonly JsonSerializerOptions Options = new() { WriteIndented = true };

    public double Volume(string id) => Volumes.GetValueOrDefault(id, 0.7);

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
            Directory.CreateDirectory(Folder);
            File.WriteAllText(FilePath, JsonSerializer.Serialize(this, Options));
        }
        catch (IOException)
        {
            // Disco pieno o file bloccato: si riprova al prossimo salvataggio.
        }
    }
}
