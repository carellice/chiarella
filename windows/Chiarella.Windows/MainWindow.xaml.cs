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
    private bool _volumeChecked;
    private Microsoft.UI.Dispatching.DispatcherQueueTimer? _volumeTimer;

    public MainWindow()
    {
        InitializeComponent();
        Anim.ReducedMotion = _store.ReducedAnimations;
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
            card.DetailsRequested += c => Detail.Open(c.Sound, c, _mixer, _store);

            card.VolumeChanged += v =>
            {
                _mixer.SetVolume(sound.Id, v);
                SaveSoon();
            };
            _cards.Add(card);
            SoundsPanel.Children.Add(card);
        }

        Detail.VolumeChanged += SaveSoon;
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

        ApplyMotionPreference();
        Root.Loaded += (_, _) => CheckDeviceVolume();
        Activated += (_, args) =>
        {
            if (args.WindowActivationState == WindowActivationState.Deactivated)
            {
                _volumeChecked = false;
                _volumeTimer?.Stop();
            }
            else if (Root.XamlRoot is not null) CheckDeviceVolume();
        };
        Closed += (_, _) =>
        {
            _volumeTimer?.Stop();
            CompositionTarget.Rendering -= OnFrame;
            _store.Save();
            _mixer.Dispose();
        };
    }

    private void CheckDeviceVolume()
    {
        if (!_store.ShowVolumeNotice) { DeviceVolumeNotice.IsOpen = false; _volumeTimer?.Stop(); return; }
        if (_volumeChecked) return;
        _volumeChecked = true;
        var state = SystemVolume.Inspect();
        DeviceVolumeNotice.IsOpen = state?.NeedsIncrease ?? true;
        RaiseDeviceVolumeButton.Visibility = state is null ? Visibility.Collapsed : Visibility.Visible;
        UpdateDeviceVolumeMessage(state);
        if (!DeviceVolumeNotice.IsOpen) return;
        _volumeTimer ??= DispatcherQueue.CreateTimer();
        _volumeTimer.Interval = TimeSpan.FromSeconds(1);
        _volumeTimer.Tick -= DeviceVolumeTimer_Tick;
        _volumeTimer.Tick += DeviceVolumeTimer_Tick;
        _volumeTimer.Start();
    }

    private void DeviceVolumeTimer_Tick(Microsoft.UI.Dispatching.DispatcherQueueTimer sender, object args)
    {
        if (!DeviceVolumeNotice.IsOpen) { sender.Stop(); return; }
        var state = SystemVolume.Inspect();
        if (state is { NeedsIncrease: false }) DeviceVolumeNotice.IsOpen = false;
        else UpdateDeviceVolumeMessage(state);
    }

    private void UpdateDeviceVolumeMessage(SystemVolumeState? state)
    {
        var current = state is null ? "Controlla il volume del dispositivo."
            : state.Muted ? "Il dispositivo è in silenzioso."
            : $"Il volume del dispositivo è al {state.Percent}%.";
        DeviceVolumeNotice.Message = current + " Per ascoltare Chiarella come previsto, portalo al 100% e regola l'ascolto con i volumi dell'app. " +
            (RaiseDeviceVolumeButton.Visibility == Visibility.Visible
                ? "Se scegli di alzarlo, la modifica riguarda anche le altre app."
                : "Puoi regolarlo dai comandi del dispositivo.");
    }

    private void RaiseDeviceVolumeButton_Click(object sender, RoutedEventArgs e)
    {
        var state = SystemVolume.RaiseToMaximum();
        if (state is { NeedsIncrease: false }) DeviceVolumeNotice.IsOpen = false;
        else
        {
            RaiseDeviceVolumeButton.Visibility = Visibility.Collapsed;
            UpdateDeviceVolumeMessage(state);
        }
    }

    private void DeviceVolumeNotice_Closed(InfoBar sender, InfoBarClosedEventArgs args) => _volumeTimer?.Stop();

    private void DisableVolumeNotice_Click(object sender, RoutedEventArgs e)
    {
        _store.ShowVolumeNotice = false;
        _store.Save();
        DeviceVolumeNotice.IsOpen = false;
        _volumeTimer?.Stop();
    }

    private void ApplyMotionPreference()
    {
        Anim.ReducedMotion = _store.ReducedAnimations;
        CompositionTarget.Rendering -= OnFrame;
        if (!Anim.ReducedMotion) CompositionTarget.Rendering += OnFrame;
        // Un solo fotogramma statico, senza callback per ogni refresh dello schermo.
        OnFrame(this, new object());
    }

    private async void PreferencesButton_Click(object sender, RoutedEventArgs e)
    {
        var volume = new ToggleSwitch { Header = "Avviso volume del dispositivo", IsOn = _store.ShowVolumeNotice };
        var motion = new ToggleSwitch { Header = "Animazioni ridotte", IsOn = _store.ReducedAnimations };
        volume.Toggled += (_, _) =>
        {
            _store.ShowVolumeNotice = volume.IsOn;
            _store.Save();
            _volumeChecked = false;
            CheckDeviceVolume();
        };
        motion.Toggled += (_, _) =>
        {
            _store.ReducedAnimations = motion.IsOn;
            _store.Save();
            ApplyMotionPreference();
        };
        var body = new StackPanel { Spacing = 16, MaxWidth = 420 };
        body.Children.Add(volume);
        body.Children.Add(new TextBlock { Text = "Mostra l'avviso all'apertura se il volume non è al massimo.", TextWrapping = TextWrapping.Wrap });
        body.Children.Add(motion);
        body.Children.Add(new TextBlock { Text = "Ferma sfondi, particelle e pulsazioni per rendere l'app più leggera. Audio, timer e respiro continuano a funzionare.", TextWrapping = TextWrapping.Wrap });
        await Dialog("Impostazioni", body).ShowAsync();
    }

    private void OnFrame(object? sender, object e)
    {
        var t = Anim.ReducedMotion ? 0 : (DateTime.Now - _started).TotalSeconds;
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
        Detail.Tick(t);
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
        Detail.Refresh();
        if (Anim.ReducedMotion) OnFrame(this, new object());
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
        RenderMasterVolume();
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
        if (e.Key != VirtualKey.Space || Relax.Visibility == Visibility.Visible || Detail.IsOpen) return;
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

    // ------------------------------------------------------------------ volume generale

    private Action? _refreshVolumePanel;

    private void RenderMasterVolume()
    {
        var v = _mixer.MasterVolume;
        // L'arco è lungo circa 70.7 unità (270° su raggio 15) diviso lo spessore del tratto.
        var length = 70.7 / VolumeArc.StrokeThickness;
        VolumeArc.StrokeDashArray = [length * v, 100];
        VolumeArc.Opacity = v < 0.005 ? 0 : 1;
        VolumeIcon.Glyph = v < 0.01 ? "\uE74F" : v < 0.34 ? "\uE993" : v < 0.67 ? "\uE994" : "\uE995";
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(VolumeButton, $"Volume generale {Math.Round(v * 100)}%");
        _refreshVolumePanel?.Invoke();
    }

    private void VolumeButton_Click(object sender, RoutedEventArgs e)
    {
        var muted = (Brush)Application.Current.Resources["MutedBrush"];
        var panel = new StackPanel { Spacing = 12, Width = 380, Padding = new Thickness(6) };
        var head = new Grid();
        var titles = new StackPanel { Margin = new Thickness(0, 0, 90, 0) };
        titles.Children.Add(new TextBlock { Text = "Volume generale", FontSize = 20, FontWeight = FontWeights.SemiBold, Foreground = (Brush)Application.Current.Resources["TextBrush"] });
        titles.Children.Add(new TextBlock { Text = "Alza o abbassa tutta l'app insieme. I volumi dei singoli suoni restano come li hai scelti.", FontSize = 13, Foreground = muted, TextWrapping = TextWrapping.Wrap });
        var pct = new TextBlock { FontSize = 36, FontWeight = FontWeights.Light, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Bottom };
        head.Children.Add(titles);
        head.Children.Add(pct);
        panel.Children.Add(head);

        var slider = new Slider { Minimum = 0, Maximum = 1, StepFrequency = 0.01, Value = _mixer.MasterVolume, Foreground = (Brush)Application.Current.Resources["AccentGradient"] };
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(slider, "Livello volume generale");
        var syncing = false;
        slider.ValueChanged += (_, a) =>
        {
            if (syncing) return;
            _mixer.SetMasterVolume(a.NewValue);
            SaveSoon();
        };
        panel.Children.Add(slider);

        var chips = new Grid { ColumnSpacing = 8 };
        var chipButtons = new List<(Button Button, double Level)>();
        foreach (var (level, i) in new[] { 0.25, 0.5, 0.75, 1.0 }.Select((l, i) => (l, i)))
        {
            chips.ColumnDefinitions.Add(new ColumnDefinition());
            var b = new Button { Content = $"{level * 100:0}%", Style = (Style)Application.Current.Resources["PillButton"], Height = 42, CornerRadius = new CornerRadius(16) };
            b.Click += (_, _) =>
            {
                _mixer.SetMasterVolume(level);
                SaveSoon();
            };
            Grid.SetColumn(b, i);
            chips.Children.Add(b);
            chipButtons.Add((b, level));
        }
        panel.Children.Add(chips);

        var mix = new StackPanel { Spacing = 8 };
        panel.Children.Add(mix);

        _refreshVolumePanel = () =>
        {
            var v = _mixer.MasterVolume;
            pct.Text = $"{Math.Round(v * 100)}%";
            syncing = true;
            slider.Value = v;
            syncing = false;
            foreach (var (b, level) in chipButtons)
            {
                var sel = Math.Round(level * 100) == Math.Round(v * 100);
                b.Background = new SolidColorBrush(sel ? Catalog.Hex("#8B7CF6").WithAlpha(0.45) : Color.FromArgb(0x12, 255, 255, 255));
            }
            mix.Children.Clear();
            if (_mixer.Active.Count == 0) return;
            mix.Children.Add(new TextBlock { Text = "COME ESCE OGNI SUONO", Style = (Style)Application.Current.Resources["OverlineText"], Foreground = (Brush)Application.Current.Resources["FaintBrush"], Margin = new Thickness(0, 6, 0, 0) });
            foreach (var id in _mixer.Active)
            {
                var s = Catalog.Sound(id)!;
                var own = _store.Volume(id);
                var row = new Grid { ColumnSpacing = 10 };
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(100) });
                row.ColumnDefinitions.Add(new ColumnDefinition());
                row.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(40) });
                row.Children.Add(Paint.Badge(s.Icon, s.Primary, s.Secondary, 26));
                var name = new TextBlock { Text = s.Name, VerticalAlignment = VerticalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis };
                Grid.SetColumn(name, 1);
                // In trasparenza il volume del suono, pieno quello che si sente davvero.
                var bar = new Grid { Height = 8, VerticalAlignment = VerticalAlignment.Center, CornerRadius = new CornerRadius(4), Background = new SolidColorBrush(Color.FromArgb(0x14, 255, 255, 255)) };
                bar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(own, GridUnitType.Star) });
                bar.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1 - own, GridUnitType.Star) });
                var ownFill = new Grid { Background = new SolidColorBrush(Color.FromArgb(0x24, 255, 255, 255)), CornerRadius = new CornerRadius(4) };
                ownFill.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(Math.Max(v, 0.0001), GridUnitType.Star) });
                ownFill.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(Math.Max(1 - v, 0.0001), GridUnitType.Star) });
                ownFill.Children.Add(new Border { Background = Paint.Diagonal(s.Primary, s.Secondary), CornerRadius = new CornerRadius(4) });
                bar.Children.Add(ownFill);
                Grid.SetColumn(bar, 2);
                var value = new TextBlock { Text = $"{Math.Round(own * v * 100)}%", Foreground = muted, HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Center };
                Grid.SetColumn(value, 3);
                row.Children.Add(name);
                row.Children.Add(bar);
                row.Children.Add(value);
                mix.Children.Add(row);
            }
        };
        _refreshVolumePanel();

        var flyout = new Flyout { Content = panel, Placement = Microsoft.UI.Xaml.Controls.Primitives.FlyoutPlacementMode.Top };
        flyout.FlyoutPresenterStyle = new Style(typeof(FlyoutPresenter))
        {
            Setters =
            {
                new Setter(Control.BackgroundProperty, new SolidColorBrush(Catalog.Hex("#161B33"))),
                new Setter(Control.CornerRadiusProperty, new CornerRadius(24)),
                new Setter(Control.PaddingProperty, new Thickness(18)),
                new Setter(FrameworkElement.MaxWidthProperty, 460.0),
            },
        };
        flyout.Closed += (_, _) => _refreshVolumePanel = null;
        flyout.ShowAt(VolumeButton);
    }

    private async void BackupButton_Click(object sender, RoutedEventArgs e)
    {
        var body = new TextBlock
        {
            Text = "Porta profili, passioni, volumi e preferenze su un altro dispositivo. Lo stesso file funziona su web, Windows e Android.",
            TextWrapping = TextWrapping.Wrap, MaxWidth = 420,
        };
        var choice = Dialog("Backup e ripristino", body);
        choice.PrimaryButtonText = "Esporta backup";
        choice.SecondaryButtonText = "Importa backup";
        var action = await choice.ShowAsync();
        try
        {
            var window = WinRT.Interop.WindowNative.GetWindowHandle(this);
            if (action == ContentDialogResult.Primary)
            {
                if (_mixer.HasMix) _store.LastMix = _mixer.Active.ToDictionary(id => id, id => _store.Volume(id));
                var raw = Backup.Stringify(_store.BackupData());
                var picker = new Windows.Storage.Pickers.FileSavePicker
                {
                    SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.DocumentsLibrary,
                    SuggestedFileName = $"Chiarella-backup-{DateTime.Now:yyyy-MM-dd}",
                };
                picker.FileTypeChoices.Add("Backup Chiarella", new List<string> { ".json" });
                WinRT.Interop.InitializeWithWindow.Initialize(picker, window);
                var file = await picker.PickSaveFileAsync();
                if (file is null) return;
                await Windows.Storage.FileIO.WriteTextAsync(file, raw);
                await Dialog("Backup esportato", new TextBlock { Text = "Conserva il file per ripristinare i tuoi dati su qualsiasi dispositivo.", TextWrapping = TextWrapping.Wrap }).ShowAsync();
            }
            else if (action == ContentDialogResult.Secondary)
            {
                var picker = new Windows.Storage.Pickers.FileOpenPicker
                {
                    SuggestedStartLocation = Windows.Storage.Pickers.PickerLocationId.DocumentsLibrary,
                };
                picker.FileTypeFilter.Add(".json");
                WinRT.Interop.InitializeWithWindow.Initialize(picker, window);
                var file = await picker.PickSingleFileAsync();
                if (file is null) return;
                // Limite anche sullo stream: evita di leggere file grandi prima della validazione.
                using var input = await file.OpenStreamForReadAsync();
                using var bytes = new MemoryStream();
                var buffer = new byte[8192];
                int count;
                while ((count = await input.ReadAsync(buffer)) > 0)
                {
                    if (bytes.Length + count > Backup.MaxBytes) throw new FormatException("Il file è troppo grande (massimo 2 MB).");
                    bytes.Write(buffer, 0, count);
                }
                var data = Backup.Parse(System.Text.Encoding.UTF8.GetString(bytes.ToArray()));
                var confirm = Dialog("Importare il backup?", new TextBlock
                {
                    Text = $"{data.Presets.Count} profili e {data.Passions.Count} passioni, con volumi e preferenze.\n\nSostituirà tutti i dati presenti su questo dispositivo. La riproduzione e il timer verranno fermati.",
                    TextWrapping = TextWrapping.Wrap, MaxWidth = 420,
                }, "Annulla");
                confirm.PrimaryButtonText = "Sostituisci e importa";
                if (await confirm.ShowAsync() != ContentDialogResult.Primary) return;
                _store.ImportBackup(data);
                _presetsSignature = "";
                _mixer.ReloadFromStore();
                Render();
                await Dialog("Backup importato", new TextBlock { Text = "Profili, passioni e preferenze sono pronti.", TextWrapping = TextWrapping.Wrap }).ShowAsync();
            }
        }
        catch (Exception ex)
        {
            await Dialog("Operazione non riuscita", new TextBlock { Text = ex.Message, TextWrapping = TextWrapping.Wrap, MaxWidth = 420 }).ShowAsync();
        }
    }

    private async void HelpButton_Click(object sender, RoutedEventArgs e)
    {
        var body = new StackPanel { Spacing = 12, MaxWidth = 460 };
        (string Title, string Text)[] items =
        [
            ("Accendi e mescola", "Clic su una card per accendere un suono, di nuovo per spegnerlo. Puoi accenderne quanti vuoi insieme."),
            ("Regola il volume", "Ogni suono acceso ha il suo cursore; il pulsante con l'altoparlante nella barra in basso regola il volume generale di tutta l'app."),
            ("Dettaglio del suono", "Clic destro (o pressione lunga sul touch) su una card: stato, volume, descrizione e suoni da abbinare."),
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
