<#
  Pubblica una nuova versione di Chiarella su GitHub Releases.

  1. alza la versione (versionCode +1, versionName: ultimo numero +1, es. 1.4 -> 1.5)
     nell'app Android e nell'app Windows
  2. compila l'APK firmato (assembleRelease), l'app Windows (zip autonomo) e la versione web (zip)
  3. commit "Versione X", tag vX, push
  4. crea la release vX su GitHub con APK, zip Windows e zip web allegati

  L'app controlla l'ultima release e propone l'aggiornamento agli utenti.

  Uso (di solito tramite release.bat nella cartella del progetto):
    release.bat                         versione automatica, note chieste a schermo
    release.bat -Version 2.0            versione scelta a mano
    release.bat -Notes "Novità..."      note di rilascio senza domande
#>
param(
    [string]$Version = "",
    [string]$Notes = ""
)

# "Continue": in PowerShell 5.1 i messaggi su stderr di git/gh fermerebbero lo script;
# gli errori dei comandi esterni si controllano con $LASTEXITCODE (vedi Run).
$ErrorActionPreference = "Continue"
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$utf8 = New-Object System.Text.UTF8Encoding $false

function Step($text) { Write-Host ""; Write-Host "==> $text" -ForegroundColor Cyan }
function Fail($text) { Write-Host ""; Write-Host "ERRORE: $text" -ForegroundColor Red; exit 1 }
function Run($exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "$exe $($arguments -join ' ') (codice $LASTEXITCODE)" }
}

# ------------------------------------------------------------------ controlli
Step "Controlli preliminari"
& gh auth status *> $null
if ($LASTEXITCODE -ne 0) { Fail "GitHub CLI non è collegata. Esegui: gh auth login" }
$branch = (git rev-parse --abbrev-ref HEAD).Trim()
if ($branch -ne "main") { Fail "Sei sul branch '$branch': le release si pubblicano da 'main'." }

$pending = git status --porcelain
if ($pending) {
    Write-Host "Ci sono modifiche non ancora salvate in git:" -ForegroundColor Yellow
    $pending | ForEach-Object { Write-Host "   $_" }
    $answer = Read-Host "Le includo nella release? (s/n)"
    if ($answer -notmatch '^[sSyY]') { Fail "Release annullata: salva o scarta le modifiche e riprova." }
}

# ------------------------------------------------------------------ versione
$gradleFile = Join-Path $root "app\build.gradle.kts"
$original = [IO.File]::ReadAllText($gradleFile, $utf8)
$code = [int]([regex]::Match($original, 'versionCode\s*=\s*(\d+)').Groups[1].Value)
$name = [regex]::Match($original, 'versionName\s*=\s*"([^"]+)"').Groups[1].Value
if (-not $Version) {
    $parts = [System.Collections.Generic.List[string]]($name.Split('.'))
    if ($parts.Count -lt 2) { $parts.Add("0") }
    $parts[$parts.Count - 1] = [string]([int]$parts[$parts.Count - 1] + 1)
    $Version = $parts -join '.'
}
$newCode = $code + 1
$tag = "v$Version"
git fetch --tags --quiet
if (git tag --list $tag) { Fail "La versione $Version esiste già (tag $tag)." }

Write-Host "Versione attuale: $name (codice $code)"
Write-Host "Nuova versione:   $Version (codice $newCode)" -ForegroundColor Green

# ------------------------------------------------------------------ note di rilascio
if (-not $Notes) {
    $previous = git describe --tags --abbrev=0 2>&1 | Where-Object { $_ -is [string] }
    if ($LASTEXITCODE -ne 0) { $previous = $null }
    $range = if ($previous) { "$previous..HEAD" } else { "HEAD" }
    $commits = git log $range --pretty=format:"- %s" | Where-Object { $_ -notmatch '^- Versione ' }
    Write-Host ""
    if ($commits) {
        Write-Host "Modifiche dall'ultima release:"
        $commits | ForEach-Object { Write-Host "   $_" }
    }
    $typed = Read-Host "Note di rilascio (Invio per usare l'elenco qui sopra)"
    $Notes = if ($typed) { $typed } elseif ($commits) { $commits -join "`n" } else { "Miglioramenti e correzioni." }
}

$updated = $original -replace 'versionCode\s*=\s*\d+', "versionCode = $newCode" `
                     -replace 'versionName\s*=\s*"[^"]+"', "versionName = `"$Version`""
[IO.File]::WriteAllText($gradleFile, $updated, $utf8)

$winProject = Join-Path $root "windows\Chiarella.Windows\Chiarella.Windows.csproj"
$winOriginal = [IO.File]::ReadAllText($winProject, $utf8)
[IO.File]::WriteAllText($winProject, ($winOriginal -replace '<Version>[^<]+</Version>', "<Version>$Version</Version>"), $utf8)

function Restore-Versions {
    [IO.File]::WriteAllText($gradleFile, $original, $utf8)
    [IO.File]::WriteAllText($winProject, $winOriginal, $utf8)
}

# ------------------------------------------------------------------ build
try {
    Step "Compilazione dell'APK $Version"
    & .\gradlew.bat --stop *> $null
    # A volte Windows tiene bloccato l'output di R8: meglio partire da pulito.
    Remove-Item "app\build\intermediates\dex\release" -Recurse -Force -ErrorAction SilentlyContinue
    Run ".\gradlew.bat" @("assembleRelease", "--console=plain", "-q")
} catch {
    Restore-Versions
    Fail "Compilazione non riuscita, versione ripristinata a $name. $_"
}

$distDir = Join-Path $root "dist"
New-Item -ItemType Directory -Force $distDir | Out-Null
$apk = Join-Path $distDir "Chiarella-$Version.apk"
Copy-Item "app\build\outputs\apk\release\app-release.apk" $apk -Force -ErrorAction Stop
Copy-Item $apk (Join-Path $distDir "Chiarella.apk") -Force
Write-Host ("APK: {0} ({1:N1} MB)" -f $apk, ((Get-Item $apk).Length / 1MB))

$sounds = Join-Path $root "app\src\main\res\raw"
try {
    Step "Compilazione dell'app Windows $Version"
    $winOut = Join-Path $distDir "windows-$Version"
    Remove-Item $winOut -Recurse -Force -ErrorAction SilentlyContinue
    Run "dotnet" @("publish", (Join-Path $root "windows\Chiarella.Windows"), "-c", "Release", "-r", "win-x64",
        "-p:Platform=x64", "-o", $winOut, "--nologo", "-v", "q")
    $winZip = Join-Path $distDir "Chiarella-Windows-$Version.zip"
    Remove-Item $winZip -ErrorAction SilentlyContinue
    Compress-Archive -Path (Join-Path $winOut "*") -DestinationPath $winZip
    Write-Host ("Windows: {0} ({1:N1} MB)" -f $winZip, ((Get-Item $winZip).Length / 1MB))

    Step "Pacchetto della versione web $Version"
    $webOut = Join-Path $distDir "web-$Version"
    Remove-Item $webOut -Recurse -Force -ErrorAction SilentlyContinue
    Copy-Item (Join-Path $root "web") $webOut -Recurse
    New-Item -ItemType Directory -Force (Join-Path $webOut "sounds") | Out-Null
    Copy-Item (Join-Path $sounds "*.ogg") (Join-Path $webOut "sounds")
    $webZip = Join-Path $distDir "Chiarella-Web-$Version.zip"
    Remove-Item $webZip -ErrorAction SilentlyContinue
    Compress-Archive -Path (Join-Path $webOut "*") -DestinationPath $webZip
    Write-Host ("Web: {0} ({1:N1} MB)" -f $webZip, ((Get-Item $webZip).Length / 1MB))
} catch {
    Restore-Versions
    Fail "Pacchetti Windows/web non riusciti, versione ripristinata a $name. $_"
}

# ------------------------------------------------------------------ git + release
try {
    Step "Commit, tag e push"
    Run "git" @("add", "-A")
    Run "git" @("commit", "-q", "-m", "Versione $Version")
    Run "git" @("tag", "-a", $tag, "-m", "Chiarella $Version")
    Run "git" @("push", "-q", "origin", "main", "--follow-tags")

    Step "Pubblicazione della release su GitHub"
    $notesFile = Join-Path $env:TEMP "chiarella-notes-$Version.md"
    [IO.File]::WriteAllText($notesFile, $Notes, $utf8)
    Run "gh" @("release", "create", $tag, $apk, $winZip, $webZip, "--title", "Chiarella $Version", "--notes-file", $notesFile)
    Remove-Item $notesFile -ErrorAction SilentlyContinue
} catch {
    Fail "$_"
}

Write-Host ""
Write-Host "Fatto! Chiarella $Version è pubblicata:" -ForegroundColor Green
Write-Host "   https://github.com/carellice/chiarella/releases/tag/$tag"
Write-Host "Le app installate la proporranno come aggiornamento al prossimo controllo."
