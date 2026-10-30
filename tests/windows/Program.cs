using Chiarella.Data;
using System.Text.Json.Nodes;

var root = Path.GetFullPath(args.Length > 0 ? args[0] : ".");
var fixture = File.ReadAllText(Path.Combine(root, "tests/fixtures/backup-v1.json"));
var output = Path.Combine(root, "dist/backup-tests");
Directory.CreateDirectory(output);
void Check(bool condition) { if (!condition) throw new Exception("Verifica backup fallita"); }
void Verify(string raw)
{
    var data = Backup.Parse(raw);
    Check(data.Presets.Count == 2 && data.Passions.Count == 2);
    Check(data.Presets[0].Name == "Quiete 🌊 e pioggia");
    Check(data.Passions[0].Detail == "L'amica geniale — un po’ di quiete");
    Check(Math.Abs(data.MasterVolume - 0.65) < 1e-6 && data.Volumes["future_sound"] == 0.5);
    Check(data.LastPassionId == "passion-2" && data.LastTimerMinutes == 45 && data.OnboardingDone);
    Check(Math.Abs(data.LastMix["mare"] - 0.35) < 1e-6);
}
Verify(fixture);
Verify("\uFEFF" + fixture);
Verify(Backup.Stringify(Backup.Parse(fixture)));
foreach (var name in new[] { "web.json", "android.json", "browser.json" })
{
    var file = Path.Combine(output, name);
    if (File.Exists(file)) { Verify(File.ReadAllText(file)); Console.WriteLine($"Compatibilità verificata: {name} -> Windows"); }
}
Action<JsonObject>[] invalid = [
    r => r["version"] = 2, r => r["version"] = "1", r => r["format"] = "other-app",
    r => r["data"]!.AsObject().Remove("volumes"), r => r["data"]!["masterVolume"] = -0.1,
    r => r["data"]!["masterVolume"] = 1.01, r => r["data"]!["volumes"]!["mare"] = "0.5",
    r => r["data"]!["lastTimerMinutes"] = 4, r => r["data"]!["lastTimerMinutes"] = 30.5,
    r => r["data"]!["presets"]!.AsArray().Add(r["data"]!["presets"]![0]!.DeepClone()),
    r => r["data"]!["passions"] = null, r => r["data"]!["presets"]![0]!["id"] = "\" onclick=alert(1)",
    r => r["data"]!["passions"]![0]!["category"] = "UNKNOWN", r => r["data"]!["onboardingDone"] = "true",
];
foreach (var change in invalid)
{
    var json = JsonNode.Parse(fixture)!.AsObject(); change(json);
    try { Backup.Parse(json.ToJsonString()); throw new Exception("Backup non valido accettato"); } catch (FormatException) { }
}
foreach (var raw in new[] { "null", "[]", "{broken", new string(' ', Backup.MaxBytes + 1) })
{
    try { Backup.Parse(raw); throw new Exception("Backup corrotto accettato"); } catch (FormatException) { }
}
var empty = JsonNode.Parse(fixture)!;
empty["data"]!["presets"] = new JsonArray(); empty["data"]!["passions"] = new JsonArray();
empty["data"]!["lastPassionId"] = null; empty["data"]!["onboardingDone"] = false;
Check(!Backup.Parse(empty.ToJsonString()).OnboardingDone);
File.WriteAllText(Path.Combine(output, "windows.json"), Backup.Stringify(Backup.Parse(fixture)));
Console.WriteLine("PASS: codec Windows, round trip, file invalidi, archivio vuoto e compatibilità");

var preferences = new Store { ShowVolumeNotice = false, ReducedAnimations = true };
var storedPreferences = System.Text.Json.JsonSerializer.Serialize(preferences);
var reopened = System.Text.Json.JsonSerializer.Deserialize<Store>(storedPreferences)!;
Check(!reopened.ShowVolumeNotice && reopened.ReducedAnimations);
var previousVersion = System.Text.Json.JsonSerializer.Deserialize<Store>("{}")!;
Check(previousVersion.ShowVolumeNotice && !previousVersion.ReducedAnimations);
Console.WriteLine("PASS: preferenze Windows persistenti e valori predefiniti delle versioni precedenti");

// La presentazione del titolo è l'unico legame del modello con WinUI; non serve al codec.
namespace Chiarella.Data
{
    internal static class Catalog
    {
        public static (string Label, string Id) Category(string id) => (id, id);
    }
}
