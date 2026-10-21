using Chiarella.Audio;
using Chiarella.Data;
using Chiarella.Ui;
using Microsoft.UI;
using Microsoft.UI.Input;
using Microsoft.UI.Text;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Windows.Graphics;
using Windows.System;
using Windows.UI;

namespace Chiarella;

public sealed partial class MainWindow : Window
{
    private static readonly int[] QuickTimers = [15, 30, 45, 60, 90, 120];

    private readonly Store _store = Store.Load();
    private readonly Mixer _mixer;
    private readonly Aurora _aurora;
    private readonly List<SoundCard> _cards = [];
    private readonly DateTime _started = DateTime.Now;

    public MainWindow()
    {
        InitializeComponent();
        _mixer = new Mixer(_store);
        _aurora = new Aurora(AuroraLayer);

        ExtendsContentIntoTitleBar = true;
        SetTitleBar(TitleBar);
        AppWindow.SetIcon(Path.Combine(AppContext.BaseDirectory, "Assets", "chiarella.ico"));
        AppWindow.TitleBar.PreferredHeightOption = TitleBarHeightOption.Tall;
        AppWindow.TitleBar.ButtonBackgroundColor = Colors.Transparent;
        AppWindow.TitleBar.ButtonInactiveBackgroundColor = Colors.Transparent;
        AppWindow.TitleBar.ButtonForegroundColor = Color.FromArgb(255, 0xA0, 0xA8, 0xC6);
        var scale = (Content as FrameworkElement)?.XamlRoot?.RasterizationScale ?? 1.0;
        AppWindow.Resize(new SizeInt32((int)(1180 * scale), (int)(860 * scale)));
        if (AppWindow.Presenter is OverlappedPresenter presenter)
        {
            presenter.PreferredMinimumWidth = 480;
            presenter.PreferredMinimumHeight = 600;
        }

        Greeting.Text = DateTime.Now.Hour switch
        {
            >= 5 and < 12 => "BUONGIORNO",
            >= 12 and < 18 => "BUON POMERIGGIO",
            >= 18 and < 23 => "BUONASERA",
            _ => "BUONANOTTE",
        };

        foreach (var sound in Catalog.Sounds)
        {
            var card = new SoundCard(sound);
            card.Toggled += () => _mixer.Toggle(sound.Id);
            card.VolumeChanged += v =>
            {
                _mixer.SetVolume(sound.Id, v);
                SaveSoon();
            };
            _cards.Add(card);
            SoundsPanel.Children.Add(card);
        }

        Relax.Closed += () =>
        {
            HomeView.Visibility = Visibility.Visible;
            Render();
        };
        _mixer.Changed += Render;
        Render();

        // Ingresso a cascata.
        UIElement[] intro = [Header, RelaxCard, PresetsTitle, PresetsScroller, SoundsTitle];
        for (var i = 0; i < intro.Length; i++) Anim.Rise(intro[i], i * 110);
        for (var i = 0; i < _cards.Count; i++) Anim.Rise(_cards[i], 450 + i * 45);

        CompositionTarget.Rendering += OnFrame;
        Closed += (_, _) =>
        {
            CompositionTarget.Rendering -= OnFrame;
            _store.Save();
            _mixer.Dispose();
        };
    }

    private void OnFrame(object? sender, object e)
    {
        var t = (DateTime.Now - _started).TotalSeconds;
        var colors = Relax.Visibility == Visibility.Visible
            ? Relax.AuroraColors
            : _mixer.Active.Select(id => Catalog.Sound(id)!.Primary).ToList();
        _aurora.Update(t, colors, _mixer.Playing || Relax.AuroraColors.Count > 0);
        if (HomeView.Visibility == Visibility.Visible)
        {
            foreach (var card in _cards) card.Tick(t, _mixer.Playing);
            StatusEq.Tick(t);
        }
        Relax.Tick(t);
        var s = 0.94 + 0.08 * (Math.Sin(t / 4 * Math.PI) + 1) / 2;
        RelaxBadgeScale.ScaleX = RelaxBadgeScale.ScaleY = s;
    }

    // ------------------------------------------------------------------ stato → interfaccia

    private bool _playerShown;

    private void Render()
    {
        foreach (var card in _cards) card.SetState(_mixer.IsActive(card.Sound.Id), _store.Volume(card.Sound.Id));
        var n = _mixer.Active.Count;
        StatusText.Text = n == 0 ? "Accendi uno o più suoni e mescolali come preferisci."
            : _mixer.Playing ? (n == 1 ? "1 suono in riproduzione" : $"{n} suoni in riproduzione") : "In pausa";
        StatusEq.Visibility = n == 0 ? Visibility.Collapsed : Visibility.Visible;
        StatusEq.Playing = _mixer.Playing;
        SoundsHint.Content = n > 0 ? "Ferma tutti" : "Tocca per accendere";
        SoundsHint.IsEnabled = n > 0;
        SoundsHint.Foreground = (Brush)Application.Current.Resources[n > 0 ? "PeachBrush" : "FaintBrush"];
        RenderPlayer();
        RenderPresets();
    }

    private void RenderPlayer()
    {
        var ids = _mixer.Active.ToList();
        var show = ids.Count > 0 && Relax.Visibility != Visibility.Visible;
        if (show != _playerShown)
        {
            _playerShown = show;
            PlayerBar.Visibility = show ? Visibility.Visible : Visibility.Collapsed;
            if (show) Anim.Rise(PlayerBar, 0, 80);
        }
        if (!show) return;

        PlayerStack.Children.Clear();
        foreach (var (id, i) in ids.Take(3).Select((id, i) => (id, i)))
        {
            var s = Catalog.Sound(id)!;
            var badge = Paint.Badge(s.Icon, s.Primary, s.Secondary, 34);
            badge.BorderThickness = new Thickness(2);
            badge.BorderBrush = new SolidColorBrush(Catalog.Hex("#161B33"));
            badge.Margin = new Thickness(i == 0 ? 0 : -12, 0, 0, 0);
            PlayerStack.Children.Add(badge);
        }
        var preset = _store.Presets.FirstOrDefault(p => p.Id == _mixer.PresetId);
        PlayerTitle.Text = preset?.Name ?? string.Join(" · ", ids.Select(id => Catalog.Sound(id)!.Name));
        var timer = _mixer.TimerEndsAt is { } end ? $" · si spegne tra {Countdown(end - DateTime.Now)}" : "";
        PlayerSub.Text = (_mixer.Playing ? "In riproduzione" : "In pausa") + timer;
        PlayIcon.Glyph = _mixer.Playing ? "" : "";
        TimerBadge.Visibility = _mixer.TimerEndsAt is null ? Visibility.Collapsed : Visibility.Visible;
        if (_mixer.TimerEndsAt is { } e2) TimerBadgeText.Text = Countdown(e2 - DateTime.Now);
        UpdateTimerDialog();
    }

    private string _presetsSignature = "";

    private void RenderPresets()
    {
        var signature = string.Join("|", _store.Presets.Select(p => p.Id + p.Name)) + _mixer.PresetId + _mixer.Playing;
        if (signature == _presetsSignature) return;
        _presetsSignature = signature;

        PresetsPanel.Children.Clear();
        PresetsHint.Text = _store.Presets.Count == 0 ? "I tuoi mix preferiti" : "Clic destro per eliminare";

        var add = new Button
        {
            Width = 110, Height = 150, CornerRadius = new CornerRadius(24), BorderThickness = new Thickness(1),
            BorderBrush = (Brush)Application.Current.Resources["GlassBorderBrush"], Background = new SolidColorBrush(Color.FromArgb(0x0D, 255, 255, 255)),
            Content = new StackPanel
            {
                Spacing = 10,
                Children =
                {
                    new Border { Width = 40, Height = 40, CornerRadius = new CornerRadius(20), Background = new SolidColorBrush(Color.FromArgb(0x14, 255, 255, 255)),
                        Child = new FontIcon { Glyph = "", FontSize = 16 } },
                    new TextBlock { Text = "Salva mix", Foreground = (Brush)Application.Current.Resources["MutedBrush"] },
                },
            },
        };
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(add, "Salva mix");
        add.Click += async (_, _) => await SavePresetAsync();
        PresetsPanel.Children.Add(add);

        if (_store.Presets.Count == 0)
        {
            PresetsPanel.Children.Add(new Border
            {
                Width = 290, Height = 150, CornerRadius = new CornerRadius(24), Padding = new Thickness(20),
                Background = new SolidColorBrush(Color.FromArgb(0x0D, 255, 255, 255)),
                Child = new StackPanel
                {
                    VerticalAlignment = VerticalAlignment.Center, Spacing = 6,
                    Children =
                    {
                        new TextBlock { Text = "Ancora nessun profilo", FontWeight = FontWeights.SemiBold, Foreground = (Brush)Application.Current.Resources["TextBrush"] },
                        new TextBlock { Text = "Accendi i suoni che ti piacciono, regola i volumi e premi «Salva mix».", TextWrapping = TextWrapping.Wrap, FontSize = 13,
                            Foreground = (Brush)Application.Current.Resources["MutedBrush"] },
                    },
                },
            });
        }

        foreach (var preset in _store.Presets)
        {
            var ids = preset.Mix.Keys.Where(id => Catalog.Sound(id) is not null).ToList();
            var sounds = ids.Select(id => Catalog.Sound(id)!).ToList();
            var selected = preset.Id == _mixer.PresetId;
            var first = sounds.FirstOrDefault()?.Primary ?? Catalog.Hex("#8B7CF6");
            var second = sounds.LastOrDefault()?.Secondary ?? Catalog.Hex("#5EC8F2");
            var stack = new StackPanel { Orientation = Orientation.Horizontal };
            foreach (var (s, i) in sounds.Take(3).Select((s, i) => (s, i)))
            {
                var b = Paint.Badge(s.Icon, s.Primary, s.Secondary, 30);
                b.Margin = new Thickness(i == 0 ? 0 : -10, 0, 0, 0);
                b.BorderThickness = new Thickness(2);
                b.BorderBrush = new SolidColorBrush(Catalog.Hex("#161B33"));
                stack.Children.Add(b);
            }
            var state = selected ? (_mixer.Playing ? "In riproduzione" : "In pausa") : ids.Count == 1 ? "1 suono" : $"{ids.Count} suoni";
            var content = new Grid { Height = 120 };
            content.Children.Add(stack);
            var label = new StackPanel { VerticalAlignment = VerticalAlignment.Bottom };
            label.Children.Add(new TextBlock { Text = preset.Name, FontSize = 16, FontWeight = FontWeights.SemiBold, Foreground = (Brush)Application.Current.Resources["TextBrush"], TextTrimming = TextTrimming.CharacterEllipsis });
            label.Children.Add(new TextBlock { Text = state, FontSize = 12, Foreground = (Brush)Application.Current.Resources["MutedBrush"] });
            content.Children.Add(label);

            var button = new Button
            {
                Width = 158, Height = 150, Padding = new Thickness(14), CornerRadius = new CornerRadius(24),
                HorizontalContentAlignment = HorizontalAlignment.Stretch, VerticalContentAlignment = VerticalAlignment.Stretch,
                BorderThickness = new Thickness(1),
                BorderBrush = new SolidColorBrush(selected ? Color.FromArgb(0x66, 255, 255, 255) : Color.FromArgb(0x1F, 255, 255, 255)),
                Background = Paint.Diagonal(first.WithAlpha(selected ? 0.45 : 0.18), second.WithAlpha(selected ? 0.35 : 0.12)),
                Content = content,
            };
            ToolTipService.SetToolTip(button, "Clic per ascoltare · clic destro per eliminare");
            Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(button, "Profilo " + preset.Name);
            button.Click += (_, _) => _mixer.ApplyPreset(preset);
            button.RightTapped += async (_, _) => await DeletePresetAsync(preset);
            PresetsPanel.Children.Add(button);
            if (selected) Anim.Pop(button, 0.95);
        }
    }

    private static string Countdown(TimeSpan span)
    {
        var total = (int)Math.Ceiling(Math.Max(0, span.TotalSeconds));
        var (h, m, s) = (total / 3600, total % 3600 / 60, total % 60);
        return h > 0 ? $"{h}:{m:00}:{s:00}" : $"{m:00}:{s:00}";
    }

    private static string FormatMinutes(int min) =>
        min < 60 ? $"{min} min" : min % 60 == 0 ? $"{min / 60} h" : $"{min / 60} h {min % 60} min";

    private DispatcherQueueTimerHolder? _saveTimer;

    /// <summary>Il volume cambia di continuo mentre si trascina: si salva una volta sola, poco dopo.</summary>
    private void SaveSoon()
    {
        _saveTimer ??= new DispatcherQueueTimerHolder(DispatcherQueue, _store.Save);
        _saveTimer.Restart();
    }

    private sealed class DispatcherQueueTimerHolder
    {
        private readonly Microsoft.UI.Dispatching.DispatcherQueueTimer _timer;

        public DispatcherQueueTimerHolder(Microsoft.UI.Dispatching.DispatcherQueue queue, Action action)
        {
            _timer = queue.CreateTimer();
            _timer.Interval = TimeSpan.FromMilliseconds(600);
            _timer.IsRepeating = false;
            _timer.Tick += (_, _) => action();
        }

        public void Restart()
        {
            _timer.Stop();
            _timer.Start();
        }
    }

    // ------------------------------------------------------------------ comandi

    private void PlayButton_Click(object sender, RoutedEventArgs e) => _mixer.TogglePlay();

    private void StopAll_Click(object sender, RoutedEventArgs e) => _mixer.StopAll();

    private void RelaxCard_Click(object sender, RoutedEventArgs e)
    {
        HomeView.Visibility = Visibility.Collapsed;
        Relax.Open(_store);
        RenderPlayer();
        Relax.Focus(FocusState.Programmatic);
    }

    private void Root_KeyDown(object sender, KeyRoutedEventArgs e)
    {
        if (e.Key != VirtualKey.Space || Relax.Visibility == Visibility.Visible) return;
        if (e.OriginalSource is TextBox or Slider or Button or SoundCard) return;
        _mixer.TogglePlay();
        e.Handled = true;
    }

    private ContentDialog Dialog(string title, UIElement content, string? close = "Chiudi") => new()
    {
        XamlRoot = Content.XamlRoot,
        Title = title,
        Content = content,
        CloseButtonText = close,
        RequestedTheme = ElementTheme.Dark,
        Background = new SolidColorBrush(Catalog.Hex("#161B33")),
        CornerRadius = new CornerRadius(24),
    };

    private async Task SavePresetAsync()
    {
        if (!_mixer.HasMix)
        {
            await Dialog("Nessun suono acceso", new TextBlock { Text = "Accendi almeno un suono per salvare un mix.", TextWrapping = TextWrapping.Wrap }, "Ok").ShowAsync();
            return;
        }
        var name = new TextBox { Text = $"Il mio mix {_store.Presets.Count + 1}", MaxLength = 32, Header = "Nome del profilo" };
        var body = new StackPanel { Spacing = 14, MinWidth = 380 };
        body.Children.Add(new TextBlock { Text = "Ritrovi questo mix, con i suoi volumi, con un solo clic.", Foreground = (Brush)Application.Current.Resources["MutedBrush"], TextWrapping = TextWrapping.Wrap });
        body.Children.Add(name);
        foreach (var id in _mixer.Active)
        {
            var s = Catalog.Sound(id)!;
            var v = _store.Volume(id);
            var row = new Grid { ColumnSpacing = 12 };
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(110) });
            row.ColumnDefinitions.Add(new ColumnDefinition());
            row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(44) });
            row.Children.Add(Paint.Badge(s.Icon, s.Primary, s.Secondary, 28));
            var label = new TextBlock { Text = s.Name, VerticalAlignment = VerticalAlignment.Center };
            Grid.SetColumn(label, 1);
            var bar = new ProgressBar { Value = v * 100, VerticalAlignment = VerticalAlignment.Center, Foreground = Paint.Diagonal(s.Primary, s.Secondary) };
            Grid.SetColumn(bar, 2);
            var pct = new TextBlock { Text = $"{Math.Round(v * 100)}%", HorizontalAlignment = HorizontalAlignment.Right, Foreground = (Brush)Application.Current.Resources["MutedBrush"] };
            Grid.SetColumn(pct, 3);
            row.Children.Add(label);
            row.Children.Add(bar);
            row.Children.Add(pct);
            body.Children.Add(row);
        }
        var dialog = Dialog("Salva come profilo", body, "Annulla");
        dialog.PrimaryButtonText = "Salva profilo";
        dialog.DefaultButton = ContentDialogButton.Primary;
        if (await dialog.ShowAsync() != ContentDialogResult.Primary || string.IsNullOrWhiteSpace(name.Text)) return;
        var preset = new Preset { Name = name.Text.Trim(), Mix = _mixer.Active.ToDictionary(id => id, _store.Volume) };
        _store.Presets.Add(preset);
        _store.Save();
        _mixer.ApplyPreset(preset);
    }

    private async Task DeletePresetAsync(Preset preset)
    {
        var dialog = Dialog($"Eliminare \"{preset.Name}\"?", new TextBlock { Text = "Il profilo verrà rimosso. I suoni non verranno toccati.", TextWrapping = TextWrapping.Wrap }, "Annulla");
        dialog.PrimaryButtonText = "Elimina";
        if (await dialog.ShowAsync() != ContentDialogResult.Primary) return;
        _store.Presets.Remove(preset);
        _store.Save();
        _presetsSignature = "";
        Render();
    }

    private ContentDialog? _timerDialog;
    private Action? _updateTimerDialog;

    private void UpdateTimerDialog() => _updateTimerDialog?.Invoke();

    private async void TimerButton_Click(object sender, RoutedEventArgs e)
    {
        var off = new StackPanel { Spacing = 14 };
        var grid = new Grid { ColumnSpacing = 10, RowSpacing = 10 };
        for (var i = 0; i < 3; i++) grid.ColumnDefinitions.Add(new ColumnDefinition());
        grid.RowDefinitions.Add(new RowDefinition());
        grid.RowDefinitions.Add(new RowDefinition());
        for (var i = 0; i < QuickTimers.Length; i++)
        {
            var min = QuickTimers[i];
            var chip = new Button { Content = FormatMinutes(min), Style = (Style)Application.Current.Resources["PillButton"], CornerRadius = new CornerRadius(18) };
            chip.Click += (_, _) => { _mixer.StartTimer(min); _timerDialog?.Hide(); };
            Grid.SetColumn(chip, i % 3);
            Grid.SetRow(chip, i / 3);
            grid.Children.Add(chip);
        }
        off.Children.Add(grid);
        var custom = new Slider { Minimum = 5, Maximum = 240, StepFrequency = 5, Value = Math.Clamp(_store.LastTimerMinutes, 5, 240), Header = "Personalizzato" };
        var customLabel = new TextBlock { FontSize = 20, FontWeight = FontWeights.SemiBold, HorizontalAlignment = HorizontalAlignment.Right };
        customLabel.Text = FormatMinutes((int)custom.Value);
        custom.ValueChanged += (_, a) => customLabel.Text = FormatMinutes((int)a.NewValue);
        var start = new Button { Content = "Avvia timer", Style = (Style)Application.Current.Resources["GradientButton"] };
        start.Click += (_, _) => { _mixer.StartTimer((int)custom.Value); _timerDialog?.Hide(); };
        var customBox = new StackPanel { Spacing = 6, Padding = new Thickness(16), CornerRadius = new CornerRadius(20), Background = new SolidColorBrush(Color.FromArgb(0x0D, 255, 255, 255)) };
        customBox.Children.Add(customLabel);
        customBox.Children.Add(custom);
        customBox.Children.Add(start);
        off.Children.Add(customBox);

        var on = new StackPanel { Spacing = 18 };
        var ring = new ProgressRing { IsIndeterminate = false, Width = 200, Height = 200, Maximum = 1 };
        var time = new TextBlock { FontSize = 40, FontWeight = FontWeights.Light, HorizontalAlignment = HorizontalAlignment.Center };
        var ends = new TextBlock { FontSize = 12, HorizontalAlignment = HorizontalAlignment.Center, Foreground = (Brush)Application.Current.Resources["MutedBrush"] };
        var ringHost = new Grid { HorizontalAlignment = HorizontalAlignment.Center };
        ringHost.Children.Add(ring);
        var ringText = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        ringText.Children.Add(time);
        ringText.Children.Add(ends);
        ringHost.Children.Add(ringText);
        on.Children.Add(ringHost);
        var extend = new Grid { ColumnSpacing = 10 };
        for (var i = 0; i < 3; i++) extend.ColumnDefinitions.Add(new ColumnDefinition());
        var plus15 = new Button { Content = "+15 min", Style = (Style)Application.Current.Resources["PillButton"] };
        plus15.Click += (_, _) => _mixer.ExtendTimer(15);
        var plus30 = new Button { Content = "+30 min", Style = (Style)Application.Current.Resources["PillButton"] };
        plus30.Click += (_, _) => _mixer.ExtendTimer(30);
        var cancel = new Button { Content = "Annulla", Style = (Style)Application.Current.Resources["PillButton"], Foreground = new SolidColorBrush(Catalog.Hex("#FF9AA9")) };
        cancel.Click += (_, _) => _mixer.CancelTimer();
        Grid.SetColumn(plus30, 1);
        Grid.SetColumn(cancel, 2);
        extend.Children.Add(plus15);
        extend.Children.Add(plus30);
        extend.Children.Add(cancel);
        on.Children.Add(extend);

        var body = new StackPanel { Spacing = 18, MinWidth = 400 };
        body.Children.Add(new TextBlock { Text = "Allo scadere i suoni sfumano dolcemente fino al silenzio.", Foreground = (Brush)Application.Current.Resources["MutedBrush"] });
        body.Children.Add(off);
        body.Children.Add(on);

        _updateTimerDialog = () =>
        {
            var active = _mixer.TimerEndsAt is not null;
            off.Visibility = active ? Visibility.Collapsed : Visibility.Visible;
            on.Visibility = active ? Visibility.Visible : Visibility.Collapsed;
            if (_mixer.TimerEndsAt is { } end)
            {
                var remaining = end - DateTime.Now;
                time.Text = Countdown(remaining);
                ends.Text = "si spegne alle " + end.ToString("HH:mm");
                ring.Value = _mixer.TimerDuration.TotalSeconds > 0 ? Math.Max(0, remaining.TotalSeconds / _mixer.TimerDuration.TotalSeconds) : 0;
            }
        };
        _updateTimerDialog();
        _timerDialog = Dialog("Timer di spegnimento", body);
        await _timerDialog.ShowAsync();
        _timerDialog = null;
        _updateTimerDialog = null;
    }

    private async void HelpButton_Click(object sender, RoutedEventArgs e)
    {
        var body = new StackPanel { Spacing = 12, MaxWidth = 460 };
        (string Title, string Text)[] items =
        [
            ("Accendi e mescola", "Clic su una card per accendere un suono, di nuovo per spegnerlo. Puoi accenderne quanti vuoi insieme."),
            ("Regola il volume", "Ogni suono acceso ha il suo cursore."),
            ("Profili", "Salva il mix corrente con «Salva mix» e ritrovalo con un clic. Clic destro per eliminarlo."),
            ("Timer", "Dalla barra in basso: allo scadere i suoni sfumano fino al silenzio."),
            ("Un momento per te", "Scegli le tue passioni: dopo un breve respiro guidato ti propongo cosa fare per rilassarti."),
            ("Tastiera", "Spazio riproduce o mette in pausa, Esc chiude «Un momento per te»."),
        ];
        foreach (var (title, text) in items)
        {
            var block = new RichTextBlock { TextWrapping = TextWrapping.Wrap };
            var p = new Microsoft.UI.Xaml.Documents.Paragraph();
            p.Inlines.Add(new Microsoft.UI.Xaml.Documents.Run { Text = title + " — ", FontWeight = FontWeights.SemiBold });
            p.Inlines.Add(new Microsoft.UI.Xaml.Documents.Run { Text = text, Foreground = (Brush)Application.Current.Resources["MutedBrush"] });
            block.Blocks.Add(p);
            body.Children.Add(block);
        }
        await Dialog("Come funziona", body, "Ho capito").ShowAsync();
    }
}
