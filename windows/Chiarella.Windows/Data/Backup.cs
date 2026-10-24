using System.Text;
using System.Text.Json;

namespace Chiarella.Data;

public sealed record BackupData(List<Preset> Presets, List<Passion> Passions,
    Dictionary<string, double> Volumes, double MasterVolume, Dictionary<string, double> LastMix,
    int LastTimerMinutes, bool OnboardingDone, string? LastPassionId);

/// <summary>Formato JSON v1 condiviso con Android e web, validato prima di modificare l'archivio.</summary>
public static class Backup
{
    public const int MaxBytes = 2 * 1024 * 1024;
    private static readonly JsonSerializerOptions Options = new() { PropertyNamingPolicy = JsonNamingPolicy.CamelCase, WriteIndented = true };
    private static readonly System.Text.RegularExpressions.Regex IdPattern = new(@"\A[A-Za-z0-9_.-]{1,128}\z");
    private static readonly System.Text.RegularExpressions.Regex SoundPattern = new(@"\A[a-z][a-z0-9_]{0,63}\z");
    private static readonly HashSet<string> Categories = ["FILM", "SERIE_TV", "VIDEOGIOCHI", "PROGRAMMARE", "LETTURA", "MUSICA", "SPORT", "NATURA", "ARTE", "FOTOGRAFIA", "CUCINA", "BENESSERE", "ALTRO"];
    private static FormatException Invalid() => new("Il file non è un backup Chiarella valido.");
    private static JsonElement Object(JsonElement value) => value.ValueKind == JsonValueKind.Object ? value : throw Invalid();
    private static string Text(JsonElement value, int max, bool empty = false)
    {
        if (value.ValueKind != JsonValueKind.String) throw Invalid();
        var s = value.GetString()!;
        if (s.Length > max || (!empty && string.IsNullOrWhiteSpace(s))) throw Invalid();
        return s;
    }
    private static string Id(JsonElement value)
    {
        var s = Text(value, 128);
        return IdPattern.IsMatch(s) ? s : throw Invalid();
    }
    private static double Volume(JsonElement value)
    {
        if (value.ValueKind != JsonValueKind.Number || !value.TryGetDouble(out var n) || !double.IsFinite(n) || n < 0 || n > 1) throw Invalid();
        return n;
    }
    private static Dictionary<string, double> Mix(JsonElement value)
    {
        var result = new Dictionary<string, double>();
        foreach (var p in Object(value).EnumerateObject())
        {
            if (!SoundPattern.IsMatch(p.Name) || result.Count >= 100) throw Invalid();
            result[p.Name] = Volume(p.Value);
        }
        return result;
    }
    private static List<T> List<T>(JsonElement value, Func<JsonElement, T> read, Func<T, string> id)
    {
        if (value.ValueKind != JsonValueKind.Array || value.GetArrayLength() > 1000) throw Invalid();
        var result = value.EnumerateArray().Select(v => read(Object(v))).ToList();
        if (result.Select(id).Distinct().Count() != result.Count) throw Invalid();
        return result;
    }

    public static BackupData Parse(string raw)
    {
        if (Encoding.UTF8.GetByteCount(raw) > MaxBytes) throw Invalid();
        try
        {
            using var json = JsonDocument.Parse(raw.TrimStart('\uFEFF'));
            var root = Object(json.RootElement);
            if (Text(root.GetProperty("format"), 100) != "chiarella-backup") throw Invalid();
            if (root.GetProperty("version").ValueKind != JsonValueKind.Number || root.GetProperty("version").GetDouble() != 1)
                throw new FormatException("Versione del backup non supportata. Aggiorna Chiarella e riprova.");
            var d = Object(root.GetProperty("data"));
            var timer = d.GetProperty("lastTimerMinutes");
            if (timer.ValueKind != JsonValueKind.Number || !timer.TryGetDouble(out var minutes) || minutes % 1 != 0 || minutes < 5 || minutes > 240) throw Invalid();
            var presets = List(d.GetProperty("presets"), p => new Preset { Id = Id(p.GetProperty("id")), Name = Text(p.GetProperty("name"), 200), Mix = Mix(p.GetProperty("mix")) }, p => p.Id);
            var passions = List(d.GetProperty("passions"), p =>
            {
                var category = Text(p.GetProperty("category"), 64);
                if (!Categories.Contains(category)) throw Invalid();
                return new Passion { Id = Id(p.GetProperty("id")), Category = category, Detail = Text(p.GetProperty("detail"), 1000, true) };
            }, p => p.Id);
            var done = d.GetProperty("onboardingDone");
            if (done.ValueKind is not (JsonValueKind.True or JsonValueKind.False)) throw Invalid();
            var last = d.GetProperty("lastPassionId");
            return new(presets, passions, Mix(d.GetProperty("volumes")), Volume(d.GetProperty("masterVolume")), Mix(d.GetProperty("lastMix")),
                (int)minutes, done.GetBoolean(), last.ValueKind == JsonValueKind.Null ? null : Id(last));
        }
        catch (Exception e) when (e is JsonException or KeyNotFoundException or InvalidOperationException or OverflowException) { throw Invalid(); }
    }

    public static string Stringify(BackupData data)
    {
        var raw = JsonSerializer.Serialize(new { format = "chiarella-backup", version = 1, exportedAt = DateTimeOffset.UtcNow.ToString("O"), data }, Options);
        Parse(raw);
        return raw;
    }
}
