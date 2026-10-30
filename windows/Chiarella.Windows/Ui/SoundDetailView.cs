using Chiarella.Audio;
using Chiarella.Data;
using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Hosting;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Animation;
using Microsoft.UI.Xaml.Shapes;
using Windows.Foundation;
using Windows.System;
using Windows.UI;

namespace Chiarella.Ui;

/// <summary>
/// Dettaglio di un suono (clic destro o pressione lunga sulla card): una finestra che "sboccia"
/// dalla card, con la scena animata, lo stato acceso/spento, il volume, la descrizione e gli abbinamenti.
/// </summary>
public sealed class SoundDetailView : Grid
{
    private static Brush TextBrush => (Brush)Application.Current.Resources["TextBrush"];
    private static Brush MutedBrush => (Brush)Application.Current.Resources["MutedBrush"];
    private static Brush FaintBrush => (Brush)Application.Current.Resources["FaintBrush"];
    private static Brush GlassBorder => (Brush)Application.Current.Resources["GlassBorderBrush"];

    private readonly Border _scrim;
    private readonly Grid _host = new() { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
    private Mixer? _mixer;
    private Store? _store;
    private Sound? _sound;
    private AmbienceScene? _scene;
    private readonly List<Ellipse> _ripples = [];
    private Border? _badge;
    private Equalizer? _eq;
    private Action? _refresh;
    private bool _closing;

    public event Action? Closed;
    public event Action? VolumeChanged;
    public bool IsOpen => Visibility == Visibility.Visible && !_closing;

    public SoundDetailView()
    {
        Visibility = Visibility.Collapsed;
        _scrim = new Border { Background = new SolidColorBrush(Color.FromArgb(0xB8, 0x05, 0x07, 0x0F)), Opacity = 0 };
        _scrim.Tapped += (_, _) => Close();
        Children.Add(_scrim);
        Children.Add(_host);
        KeyDown += (_, e) =>
        {
            if (e.Key == VirtualKey.Escape) { Close(); e.Handled = true; }
        };
    }

    public void Open(Sound sound, FrameworkElement origin, Mixer mixer, Store store)
    {
        if (IsOpen) return;
        _sound = sound;
        _mixer = mixer;
        _store = store;
        _closing = false;
        _host.Children.Clear();
        var card = Build(sound);
        _host.Children.Add(card);
        Visibility = Visibility.Visible;
        Refresh();

        if (Anim.ReducedMotion)
        {
            card.RenderTransform = new CompositeTransform();
            card.Opacity = _scrim.Opacity = 1;
            Tick(0);
            Focus(FocusState.Programmatic);
            return;
        }
        // La finestra parte dal centro della card toccata e si ingrandisce fino al centro della finestra.
        var center = origin.TransformToVisual(this).TransformPoint(new Point(origin.ActualWidth / 2, origin.ActualHeight / 2));
        var transform = new CompositeTransform
        {
            TranslateX = center.X - ActualWidth / 2, TranslateY = center.Y - ActualHeight / 2, ScaleX = 0.35, ScaleY = 0.35,
        };
        card.RenderTransform = transform;
        card.RenderTransformOrigin = new Point(0.5, 0.5);
        card.Opacity = 0;
        var spring = new BackEase { Amplitude = 0.35, EasingMode = EasingMode.EaseOut };
        var sb = new Storyboard();
        Add(sb, _scrim, "Opacity", 0, 1, 320, new QuadraticEase());
        Add(sb, card, "Opacity", 0, 1, 260, new QuadraticEase());
        Add(sb, transform, "TranslateX", transform.TranslateX, 0, 620, spring);
        Add(sb, transform, "TranslateY", transform.TranslateY, 0, 620, spring);
        Add(sb, transform, "ScaleX", 0.35, 1, 620, spring);
        Add(sb, transform, "ScaleY", 0.35, 1, 620, spring);
        sb.Begin();
        _ = PopBadgeAsync();
        Focus(FocusState.Programmatic);
    }

    private async Task PopBadgeAsync()
    {
        if (_badge is null) return;
        _badge.Opacity = 0;
        await Task.Delay(180);
        _badge.Opacity = 1;
        Anim.Pop(_badge, 0.1);
    }

    public void Close()
    {
        if (!IsOpen || _host.Children.FirstOrDefault() is not FrameworkElement card) return;
        if (Anim.ReducedMotion)
        {
            Visibility = Visibility.Collapsed;
            _host.Children.Clear();
            _scene = null;
            _refresh = null;
            _closing = false;
            Closed?.Invoke();
            return;
        }
        _closing = true;
        var transform = (CompositeTransform)card.RenderTransform;
        var ease = new QuadraticEase { EasingMode = EasingMode.EaseIn };
        var sb = new Storyboard();
        Add(sb, _scrim, "Opacity", _scrim.Opacity, 0, 250, ease);
        Add(sb, card, "Opacity", 1, 0, 230, ease);
        Add(sb, transform, "ScaleX", 1, 0.85, 250, ease);
        Add(sb, transform, "ScaleY", 1, 0.85, 250, ease);
        Add(sb, transform, "TranslateY", 0, 40, 250, ease);
        sb.Completed += (_, _) =>
        {
            Visibility = Visibility.Collapsed;
            _host.Children.Clear();
            _scene = null;
            _refresh = null;
            _closing = false;
            Closed?.Invoke();
        };
        sb.Begin();
    }

    /// <summary>Aggiorna stato, volume e abbinamenti (chiamato quando cambia il mix).</summary>
    public void Refresh() => _refresh?.Invoke();

    /// <summary>Un fotogramma: scena del suono e onde attorno all'icona.</summary>
    public void Tick(double t)
    {
        if (!IsOpen || _sound is null || _mixer is null || _store is null) return;
        var live = _mixer.IsActive(_sound.Id) && _mixer.Playing;
        _scene?.Tick(t, live ? 0.55 + 0.45 * _store.Volume(_sound.Id) : 0.45);
        for (var i = 0; i < _ripples.Count; i++)
        {
            var k = (t / 2.6 + i / 3.0) % 1;
            var r = _ripples[i];
            r.Opacity = (1 - k) * (live ? 0.6 : 0.3);
            var s = 1 + k * 0.9;
            ((ScaleTransform)r.RenderTransform).ScaleX = s;
            ((ScaleTransform)r.RenderTransform).ScaleY = s;
        }
        _eq?.Tick(t);
    }

    private FrameworkElement Build(Sound sound)
    {
        var info = Catalog.Info(sound.Id);
        var card = new Border
        {
            Width = 440, Margin = new Thickness(20), CornerRadius = new CornerRadius(32),
            Background = new SolidColorBrush(Catalog.Hex("#161B33")),
            BorderThickness = new Thickness(1), BorderBrush = new SolidColorBrush(sound.Secondary.WithAlpha(0.45)),
        };
        card.Tapped += (_, e) => e.Handled = true;
        card.SizeChanged += (_, e) =>
        {
            var visual = ElementCompositionPreview.GetElementVisual(card);
            visual.Clip = visual.Compositor.CreateRectangleClip(0, 0, (float)e.NewSize.Width, (float)e.NewSize.Height,
                new(32), new(32), new(32), new(32));
        };
        var root = new StackPanel();
        card.Child = new ScrollViewer { Content = root, MaxHeight = 760 };

        // ---- intestazione con la scena animata
        var header = new Grid { Height = 230, Background = Paint.Diagonal(sound.Primary.WithAlpha(0.75), sound.Secondary.WithAlpha(0.2)) };
        _scene = new AmbienceScene(sound);
        header.Children.Add(_scene);
        header.Children.Add(new Border
        {
            Background = new LinearGradientBrush
            {
                StartPoint = new Point(0, 0), EndPoint = new Point(0, 1),
                GradientStops =
                {
                    new GradientStop { Color = Catalog.Hex("#161B33").WithAlpha(0), Offset = 0.5 },
                    new GradientStop { Color = Catalog.Hex("#161B33"), Offset = 1 },
                },
            },
        });
        var iconHost = new Grid { Width = 96, Height = 96, Margin = new Thickness(0, 0, 0, 44), HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        _ripples.Clear();
        for (var i = 0; i < 3; i++)
        {
            var ripple = new Ellipse
            {
                Stroke = new SolidColorBrush(sound.Secondary), StrokeThickness = 1.5, Opacity = 0,
                RenderTransform = new ScaleTransform(), RenderTransformOrigin = new Point(0.5, 0.5),
            };
            _ripples.Add(ripple);
            iconHost.Children.Add(ripple);
        }
        _badge = Paint.Badge(sound.Icon, sound.Primary, sound.Secondary, 96);
        iconHost.Children.Add(_badge);
        header.Children.Add(iconHost);

        var title = new StackPanel { VerticalAlignment = VerticalAlignment.Bottom, HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 0, 0, 8) };
        title.Children.Add(new TextBlock { Text = sound.Name, FontSize = 24, FontWeight = FontWeights.SemiBold, Foreground = TextBrush, HorizontalAlignment = HorizontalAlignment.Center });
        // Stato ben visibile: acceso (con equalizzatore) o spento.
        var statePill = new Border { CornerRadius = new CornerRadius(14), Padding = new Thickness(12, 4, 12, 4), Margin = new Thickness(0, 6, 0, 0), HorizontalAlignment = HorizontalAlignment.Center, BorderThickness = new Thickness(1) };
        var stateRow = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8 };
        var eq = _eq = new Equalizer { Width = 14, Height = 11, VerticalAlignment = VerticalAlignment.Center };
        var stateText = new TextBlock { FontSize = 12, FontWeight = FontWeights.SemiBold, VerticalAlignment = VerticalAlignment.Center };
        stateRow.Children.Add(eq);
        stateRow.Children.Add(stateText);
        statePill.Child = stateRow;
        title.Children.Add(statePill);
        header.Children.Add(title);

        var close = new Button
        {
            Style = (Style)Application.Current.Resources["RoundButton"], Width = 36, Height = 36, Background = new SolidColorBrush(Color.FromArgb(0x40, 0, 0, 0)),
            Foreground = new SolidColorBrush(Colors.White), HorizontalAlignment = HorizontalAlignment.Right, VerticalAlignment = VerticalAlignment.Top,
            Margin = new Thickness(12), Content = new FontIcon { Glyph = "", FontSize = 12 },
        };
        ToolTipService.SetToolTip(close, "Chiudi");
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(close, "Chiudi dettaglio");
        close.Click += (_, _) => Close();
        header.Children.Add(close);
        root.Children.Add(header);

        // ---- corpo
        var body = new StackPanel { Padding = new Thickness(22, 16, 22, 22) };
        root.Children.Add(body);

        // Volume sempre visibile: a suono spento si regola il volume con cui ripartirà.
        var volumeBox = new StackPanel
        {
            Padding = new Thickness(16, 12, 16, 6), CornerRadius = new CornerRadius(20), Margin = new Thickness(0, 0, 0, 18),
            Background = new SolidColorBrush(Color.FromArgb(0x0D, 255, 255, 255)), BorderThickness = new Thickness(1), BorderBrush = GlassBorder,
        };
        var volumeHead = new Grid();
        var volumeLabel = new TextBlock { Text = "VOLUME", Style = (Style)Application.Current.Resources["OverlineText"], Foreground = FaintBrush };
        var volumeValue = new TextBlock { FontSize = 16, FontWeight = FontWeights.SemiBold, Foreground = TextBrush, HorizontalAlignment = HorizontalAlignment.Right };
        volumeHead.Children.Add(volumeLabel);
        volumeHead.Children.Add(volumeValue);
        var volume = new Slider { Minimum = 0, Maximum = 1, StepFrequency = 0.01, Foreground = Paint.Diagonal(sound.Primary, sound.Secondary) };
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(volume, "Volume " + sound.Name);
        var volumeHint = new TextBlock { FontSize = 12, Foreground = FaintBrush, Margin = new Thickness(0, -4, 0, 4) };
        var syncing = false;
        volume.ValueChanged += (_, e) =>
        {
            volumeValue.Text = $"{Math.Round(e.NewValue * 100)}%";
            if (syncing) return;
            _mixer?.SetVolume(sound.Id, e.NewValue);
            VolumeChanged?.Invoke();
        };
        volumeBox.Children.Add(volumeHead);
        volumeBox.Children.Add(volume);
        volumeBox.Children.Add(volumeHint);
        body.Children.Add(volumeBox);

        body.Children.Add(new TextBlock { Text = info.Description, FontSize = 15, Foreground = MutedBrush, TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 0, 0, 18) });

        body.Children.Add(Label("IDEALE PER"));
        var moods = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, Margin = new Thickness(0, 0, 0, 18) };
        foreach (var mood in info.Moods)
        {
            moods.Children.Add(new Border
            {
                CornerRadius = new CornerRadius(16), Padding = new Thickness(12, 6, 12, 6), BorderThickness = new Thickness(1),
                Background = new SolidColorBrush(sound.Primary.WithAlpha(0.18)), BorderBrush = new SolidColorBrush(sound.Secondary.WithAlpha(0.35)),
                Child = new TextBlock { Text = mood, FontWeight = FontWeights.SemiBold, FontSize = 13, Foreground = TextBrush },
            });
        }
        body.Children.Add(moods);

        body.Children.Add(Label("SI ABBINA BENE CON"));
        var pairs = new Grid { ColumnSpacing = 8, Margin = new Thickness(0, 0, 0, 20) };
        var pairButtons = new List<(Button Button, Sound Sound, TextBlock State)>();
        foreach (var (pid, i) in info.Pairs.Select((p, i) => (p, i)))
        {
            if (Catalog.Sound(pid) is not { } other) continue;
            pairs.ColumnDefinitions.Add(new ColumnDefinition());
            var stack = new StackPanel { Spacing = 4, HorizontalAlignment = HorizontalAlignment.Center };
            var badge = Paint.Badge(other.Icon, other.Primary, other.Secondary, 36);
            badge.HorizontalAlignment = HorizontalAlignment.Center;
            stack.Children.Add(badge);
            stack.Children.Add(new TextBlock { Text = other.Name, FontSize = 12, FontWeight = FontWeights.SemiBold, Foreground = TextBrush, HorizontalAlignment = HorizontalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis });
            var st = new TextBlock { FontSize = 11, HorizontalAlignment = HorizontalAlignment.Center };
            stack.Children.Add(st);
            var b = new Button
            {
                Content = stack, HorizontalAlignment = HorizontalAlignment.Stretch, Padding = new Thickness(6, 12, 6, 12),
                CornerRadius = new CornerRadius(20), BorderThickness = new Thickness(1),
            };
            Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(b, "Abbina " + other.Name);
            b.Click += (_, _) =>
            {
                Anim.Pop(b, 0.9);
                _mixer?.Toggle(other.Id);
            };
            SetColumn(b, i);
            pairs.Children.Add(b);
            pairButtons.Add((b, other, st));
        }
        body.Children.Add(pairs);

        var toggle = new Button { Style = (Style)Application.Current.Resources["GradientButton"] };
        toggle.Click += (_, _) => _mixer?.Toggle(sound.Id);
        body.Children.Add(toggle);

        // Ingresso a cascata delle sezioni.
        for (var i = 0; i < body.Children.Count; i++) Anim.Rise(body.Children[i], 140 + i * 55, 22);
        Anim.Rise(title, 100, 16);

        _refresh = () =>
        {
            if (_mixer is null || _store is null) return;
            var on = _mixer.IsActive(sound.Id);
            var live = on && _mixer.Playing;
            stateText.Text = live ? "Acceso · in riproduzione" : on ? "Acceso · in pausa" : "Spento";
            stateText.Foreground = on ? new SolidColorBrush(Colors.White) : MutedBrush;
            statePill.Background = new SolidColorBrush(on ? sound.Primary.WithAlpha(0.55) : Color.FromArgb(0x26, 0, 0, 0));
            statePill.BorderBrush = on ? new SolidColorBrush(sound.Secondary.WithAlpha(0.8)) : GlassBorder;
            eq.Visibility = on ? Visibility.Visible : Visibility.Collapsed;
            eq.Playing = live;

            syncing = true;
            volume.Value = _store.Volume(sound.Id);
            syncing = false;
            volumeValue.Text = $"{Math.Round(volume.Value * 100)}%";
            volumeHint.Text = on ? "" : "Il suono è spento: partirà con questo volume.";
            volumeHint.Visibility = on ? Visibility.Collapsed : Visibility.Visible;

            foreach (var (b, other, st) in pairButtons)
            {
                var pon = _mixer.IsActive(other.Id);
                b.Background = new SolidColorBrush(pon ? other.Primary.WithAlpha(0.35) : Color.FromArgb(0x0D, 255, 255, 255));
                b.BorderBrush = pon ? new SolidColorBrush(other.Secondary.WithAlpha(0.7)) : GlassBorder;
                st.Text = pon ? "Acceso" : "Aggiungi";
                st.Foreground = pon ? new SolidColorBrush(other.Secondary) : FaintBrush;
            }

            toggle.Content = (on ? "Spegni " : "Accendi ") + sound.Name.ToLowerInvariant();
            toggle.Background = on ? new SolidColorBrush(Color.FromArgb(0x14, 255, 255, 255)) : Paint.Diagonal(sound.Primary, sound.Secondary);
            toggle.BorderThickness = new Thickness(on ? 1 : 0);
            toggle.BorderBrush = GlassBorder;
        };
        return card;
    }

    private static TextBlock Label(string text) => new()
    {
        Text = text, Style = (Style)Application.Current.Resources["OverlineText"], Foreground = FaintBrush, Margin = new Thickness(0, 0, 0, 8),
    };

    private static void Add(Storyboard sb, DependencyObject target, string property, double from, double to, int ms, EasingFunctionBase ease)
    {
        var a = new DoubleAnimation { From = from, To = to, Duration = TimeSpan.FromMilliseconds(ms), EasingFunction = ease, EnableDependentAnimation = true };
        Storyboard.SetTarget(a, target);
        Storyboard.SetTargetProperty(a, property);
        sb.Children.Add(a);
    }
}
