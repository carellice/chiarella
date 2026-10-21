using Microsoft.UI.Xaml;

namespace Chiarella;

public partial class App : Application
{
    private Window? _window;

    public App()
    {
        InitializeComponent();
        // Gli errori imprevisti finiscono in %LOCALAPPDATA%\Chiarella\errori.log.
        UnhandledException += (_, e) => Log(e.Exception);
        AppDomain.CurrentDomain.UnhandledException += (_, e) => Log(e.ExceptionObject as Exception);
        TaskScheduler.UnobservedTaskException += (_, e) => Log(e.Exception);
    }

    private static void Log(Exception? error)
    {
        try
        {
            var folder = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Chiarella");
            Directory.CreateDirectory(folder);
            File.AppendAllText(Path.Combine(folder, "errori.log"), $"{DateTime.Now:s} {error}\n\n");
        }
        catch (IOException)
        {
        }
    }

    protected override void OnLaunched(LaunchActivatedEventArgs args)
    {
        _window = new MainWindow();
        _window.Activate();
    }
}
