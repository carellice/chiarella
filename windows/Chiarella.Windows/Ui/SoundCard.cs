using Chiarella.Data;
using Microsoft.UI;
using Microsoft.UI.Input;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Hosting;
using Microsoft.UI.Xaml.Input;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Windows.Foundation;
using Windows.System;
using Windows.UI;

namespace Chiarella.Ui;

/// <summary>Card di un suono: tocco per accenderlo, cursore del volume, scena animata quando è attivo.</summary>
public sealed class SoundCard : Grid
{
    private const double Radius = 26;
    private readonly Border _tint;
    private readonly Border _icon;
    private readonly TextBlock _tagline;
    private readonly Slider _volume;
    private readonly Canvas _scene;
    private readonly List<(FrameworkElement Shape, double X, double Y, double Size, double Speed, double Phase)> _particles = [];
    private readonly Rectangle? _flash;
    private readonly Ellipse? _bottomGlow;
    private readonly Polyline[] _lines = [];
    private bool _active;
    private bool _syncing;

    public Sound Sound { get; }
    public event Action? Toggled;
    public event Action<double>? VolumeChanged;

    public SoundCard(Sound sound)
    {
        Sound = sound;
        IsTabStop = true;
        UseSystemFocusVisuals = true;
        CornerRadius = new CornerRadius(Radius);
        BorderThickness = new Thickness(1);
        BorderBrush = new SolidColorBrush(Color.FromArgb(0x1F, 255, 255, 255));
        Background = new SolidColorBrush(Color.FromArgb(0x0D, 255, 255, 255));
        RenderTransformOrigin = new Point(0.5, 0.5);
        RenderTransform = new CompositeTransform();

        _tint = new Border { Background = Paint.Diagonal(sound.Primary.WithAlpha(0.42), sound.Secondary.WithAlpha(0.30)), Opacity = 0 };
        _scene = new Canvas { IsHitTestVisible = false, Opacity = 0 };
        Children.Add(_tint);
        Children.Add(_scene);

        _icon = new Border
        {
            Width = 44, Height = 44, CornerRadius = new CornerRadius(22), HorizontalAlignment = HorizontalAlignment.Left,
            VerticalAlignment = VerticalAlignment.Top, Background = new SolidColorBrush(Color.FromArgb(0x14, 255, 255, 255)),
            Child = new TextBlock { Text = sound.Icon, FontSize = 21, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center },
            RenderTransformOrigin = new Point(0.5, 0.5), RenderTransform = new CompositeTransform(),
        };
        _tagline = new TextBlock { Text = sound.Tagline, FontSize = 13, Foreground = (Brush)Application.Current.Resources["MutedBrush"] };
        _volume = new Slider
        {
            Minimum = 0, Maximum = 1, StepFrequency = 0.01, Visibility = Visibility.Collapsed, Margin = new Thickness(0, 4, 0, -6),
            Foreground = new SolidColorBrush(Colors.White),
        };
        ToolTipService.SetToolTip(_volume, "Volume");
        _volume.ValueChanged += (_, e) => { if (!_syncing) VolumeChanged?.Invoke(e.NewValue); };
        _volume.ThumbToolTipValueConverter = new PercentConverter();

        var content = new Grid { Padding = new Thickness(18) };
        content.RowDefinitions.Add(new RowDefinition { Height = new GridLength(1, GridUnitType.Star) });
        content.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        content.Children.Add(_icon);
        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Bottom };
        text.Children.Add(new TextBlock { Text = sound.Name, FontSize = 18, FontWeight = Microsoft.UI.Text.FontWeights.SemiBold, Foreground = (Brush)Application.Current.Resources["TextBrush"] });
        text.Children.Add(_tagline);
        text.Children.Add(_volume);
        SetRow(text, 1);
        content.Children.Add(text);
        Children.Add(content);

        // Elementi della scena, diversi per ogni tipo di suono.
        var rnd = new Random(sound.Id.GetHashCode());
        Shape Make(Ambience a, int i)
        {
            var color = i % 2 == 0 ? sound.Primary : sound.Secondary;
            return a switch
            {
                Ambience.Rain or Ambience.Storm => new Rectangle { Width = 1.6, Height = 12, RadiusX = 1, RadiusY = 1, Fill = new SolidColorBrush(sound.Secondary.WithAlpha(0.5)) },
                Ambience.Fall => new Rectangle
                {
                    Width = 2, Height = 46, RadiusX = 1, RadiusY = 1,
                    Fill = new LinearGradientBrush
                    {
                        StartPoint = new Point(0, 0), EndPoint = new Point(0, 1),
                        GradientStops = { new GradientStop { Color = sound.Secondary.WithAlpha(0) }, new GradientStop { Color = sound.Secondary.WithAlpha(0.55), Offset = 1 } },
                    },
                },
                Ambience.Wind or Ambience.Airflow => new Rectangle
                {
                    Width = 60, Height = 1.6, RadiusX = 1, RadiusY = 1,
                    Fill = new LinearGradientBrush
                    {
                        StartPoint = new Point(0, 0), EndPoint = new Point(1, 0),
                        GradientStops = { new GradientStop { Color = color.WithAlpha(0) }, new GradientStop { Color = color.WithAlpha(0.6), Offset = 0.5 }, new GradientStop { Color = color.WithAlpha(0), Offset = 1 } },
                    },
                },
                Ambience.Leaves => new Ellipse { Width = 9, Height = 5, Fill = new SolidColorBrush(color.WithAlpha(0.6)) },
                Ambience.Fireflies => new Ellipse { Width = 14, Height = 14, Fill = Paint.Glow(sound.Secondary, 0.9) },
                _ => new Ellipse { Width = 4, Height = 4, Fill = new SolidColorBrush(color.WithAlpha(0.85)) },
            };
        }
        var count = sound.Ambience switch { Ambience.Rain or Ambience.Storm => 24, Ambience.Waves or Ambience.Ripple or Ambience.Heat => 8, _ => 16 };
        for (var i = 0; i < count; i++)
        {
            var shape = Make(sound.Ambience, i);
            _scene.Children.Add(shape);
            _particles.Add((shape, rnd.NextDouble(), rnd.NextDouble(), rnd.NextDouble(), 0.5 + rnd.NextDouble(), rnd.NextDouble() * 6.28));
        }
        if (sound.Ambience is Ambience.Waves or Ambience.Ripple or Ambience.Heat)
        {
            _lines = Enumerable.Range(0, sound.Ambience == Ambience.Waves ? 3 : 4).Select(i => new Polyline
            {
                Stroke = new SolidColorBrush((i % 2 == 0 ? sound.Secondary : sound.Primary).WithAlpha(0.35 + i * 0.08)),
                StrokeThickness = sound.Ambience == Ambience.Waves ? 3 : 1.6,
                StrokeLineJoin = PenLineJoin.Round,
            }).ToArray();
            foreach (var l in _lines) _scene.Children.Insert(0, l);
        }
        if (sound.Ambience is Ambience.Embers or Ambience.Heat or Ambience.Fall)
        {
            _bottomGlow = new Ellipse { Fill = Paint.Glow(sound.Ambience == Ambience.Fall ? sound.Secondary : sound.Primary, 0.4) };
            _scene.Children.Insert(0, _bottomGlow);
        }
        if (sound.Ambience == Ambience.Storm)
        {
            _flash = new Rectangle { Fill = new SolidColorBrush(sound.Secondary), Opacity = 0 };
            _scene.Children.Add(_flash);
        }

        // Angoli arrotondati anche per la scena.
        SizeChanged += (_, e) =>
        {
            var visual = ElementCompositionPreview.GetElementVisual(this);
            var clip = visual.Compositor.CreateRectangleClip(0, 0, (float)e.NewSize.Width, (float)e.NewSize.Height,
                new((float)Radius), new((float)Radius), new((float)Radius), new((float)Radius));
            visual.Clip = clip;
            if (_flash is not null) { _flash.Width = e.NewSize.Width; _flash.Height = e.NewSize.Height; }
        };

        Tapped += (_, e) =>
        {
            if (IsInside(e.OriginalSource as DependencyObject, _volume)) return;
            Toggled?.Invoke();
        };
        KeyDown += (_, e) =>
        {
            if (e.Key is VirtualKey.Enter or VirtualKey.Space && e.OriginalSource == this)
            {
                Toggled?.Invoke();
                e.Handled = true;
            }
        };
        PointerPressed += (_, e) => { if (!IsInside(e.OriginalSource as DependencyObject, _volume)) Scale(0.96); };
        PointerReleased += (_, _) => Scale(1);
        PointerCanceled += (_, _) => Scale(1);
        PointerExited += (_, _) => Scale(1);
        PointerEntered += (_, _) => ProtectedCursor = InputSystemCursor.Create(InputSystemCursorShape.Hand);
    }

    private static bool IsInside(DependencyObject? node, DependencyObject container)
    {
        for (; node is not null; node = VisualTreeHelper.GetParent(node))
            if (node == container) return true;
        return false;
    }

    private void Scale(double s)
    {
        // L'animazione d'ingresso usa una CompositeTransform: si riusa quella.
        if (RenderTransform is not CompositeTransform t) RenderTransform = t = new CompositeTransform();
        t.ScaleX = t.ScaleY = s;
    }

    public void SetState(bool active, double volume)
    {
        _syncing = true;
        _volume.Value = volume;
        _syncing = false;
        if (active == _active) return;
        _active = active;
        Anim.Fade(_tint, active ? 1 : 0, 500);
        Anim.Fade(_scene, active ? 1 : 0, 700);
        _volume.Visibility = active ? Visibility.Visible : Visibility.Collapsed;
        _tagline.Foreground = active ? new SolidColorBrush(Color.FromArgb(0xCC, 255, 255, 255)) : (Brush)Application.Current.Resources["MutedBrush"];
        _icon.Background = active ? Paint.Diagonal(Sound.Primary, Sound.Secondary) : new SolidColorBrush(Color.FromArgb(0x14, 255, 255, 255));
        BorderBrush = new SolidColorBrush(active ? Sound.Secondary.WithAlpha(0.6) : Color.FromArgb(0x1F, 255, 255, 255));
        var t = (CompositeTransform)_icon.RenderTransform;
        t.Rotation = active ? -8 : 0;
        if (active) Anim.Pop(_icon, 0.8);
    }

    /// <summary>Un fotogramma della scena animata (solo se il suono è acceso).</summary>
    public void Tick(double sec, bool playing)
    {
        if (!_active) return;
        var w = ActualWidth;
        var h = ActualHeight;
        if (w <= 0) return;
        var a = playing ? 1.0 : 0.35;

        foreach (var (shape, px, py, size, speed, phase) in _particles)
        {
            double x, y, op = a;
            switch (Sound.Ambience)
            {
                case Ambience.Rain or Ambience.Storm:
                    y = ((py + sec * 0.9 * speed) % 1.2 - 0.1) * h; x = px * w; op *= 0.5 + size * 0.5;
                    break;
                case Ambience.Fall:
                    y = ((py + sec * 1.3 * speed) % 1.3 - 0.3) * h; x = w * (0.2 + px * 0.6);
                    break;
                case Ambience.Leaves:
                    y = ((py + sec * 0.06 * speed) % 1.15 - 0.08) * h; x = (px + Math.Sin(sec * 0.8 * speed + phase) * 0.06) * w;
                    if (shape.RenderTransform is not RotateTransform spin) shape.RenderTransform = spin = new RotateTransform();
                    spin.Angle = (sec * 40 + phase * 57) % 360;
                    break;
                case Ambience.Wind or Ambience.Airflow:
                    x = ((px + sec * 0.25 * speed) % 1.4 - 0.4) * w;
                    y = h * (0.2 + py * 0.7) + Math.Sin(sec * 2 + phase) * (Sound.Ambience == Ambience.Airflow ? 8 : 3);
                    op *= 0.7;
                    break;
                case Ambience.Fireflies:
                    x = (px + Math.Sin(sec * 0.4 * speed + phase) * 0.08) * w; y = (py + Math.Cos(sec * 0.3 * speed + phase) * 0.06) * h;
                    op *= (Math.Sin(sec * 1.8 * speed + phase) + 1) / 2;
                    break;
                case Ambience.Waves or Ambience.Ripple:
                    x = px * w; y = h * (0.5 + py * 0.45);
                    op *= Math.Abs(Math.Sin(sec * 1.5 * speed + phase)) * 0.7;
                    break;
                default: // braci e calore: scintille che salgono
                    var pr = (py + sec * 0.22 * speed) % 1;
                    x = w * (0.3 + px * 0.4) + Math.Sin(sec * 2 * speed + phase) * 10 * pr;
                    y = h * (1.05 - pr);
                    op *= (1 - pr) * 0.9;
                    break;
            }
            Canvas.SetLeft(shape, x - shape.Width / 2);
            Canvas.SetTop(shape, y - shape.Height / 2);
            shape.Opacity = op;
        }

        for (var i = 0; i < _lines.Length; i++)
        {
            var points = new PointCollection();
            var waves = Sound.Ambience == Ambience.Waves;
            var baseY = Sound.Ambience switch
            {
                Ambience.Waves => h * (0.62 + i * 0.1),
                Ambience.Heat => h * (0.55 + i * 0.1) - (sec * 12 + i * 20) % 30,
                _ => h * (0.45 + i * 0.11),
            };
            for (double x = 0; x <= w + 6; x += 8)
            {
                var y = waves
                    ? baseY + Math.Sin(x / w * 6 + sec * (0.8 + i * 0.3) + i * 2) * 8
                    : baseY + Math.Sin(x / w * (Sound.Ambience == Ambience.Heat ? 9 : 12.5) - sec * (1.4 + i * 0.2) + i) * (Sound.Ambience == Ambience.Heat ? 4 : 3);
                points.Add(new Point(x, y));
            }
            _lines[i].Points = points;
            _lines[i].Opacity = a;
        }

        if (_bottomGlow is not null)
        {
            var r = w * 0.7;
            _bottomGlow.Width = _bottomGlow.Height = r * 2;
            Canvas.SetLeft(_bottomGlow, w / 2 - r);
            Canvas.SetTop(_bottomGlow, h * 1.1 - r);
            _bottomGlow.Opacity = a * (0.75 + 0.15 * Math.Sin(sec * 7) + 0.1 * Math.Sin(sec * 13));
        }
        if (_flash is not null)
            _flash.Opacity = Math.Pow(Math.Max(0, Math.Sin(sec * 0.9) * Math.Sin(sec * 7.3)), 6) * 0.45 * a;
    }

    public bool IsOn => _active;

    public void RaiseToggle() => Toggled?.Invoke();

    protected override Microsoft.UI.Xaml.Automation.Peers.AutomationPeer OnCreateAutomationPeer() => new CardPeer(this);

    /// <summary>Per gli screen reader (e l'automazione): la card è un interruttore con il nome del suono.</summary>
    private sealed class CardPeer(SoundCard card) : Microsoft.UI.Xaml.Automation.Peers.FrameworkElementAutomationPeer(card),
        Microsoft.UI.Xaml.Automation.Provider.IToggleProvider
    {
        protected override string GetNameCore() => card.Sound.Name;
        protected override string GetClassNameCore() => nameof(SoundCard);
        protected override Microsoft.UI.Xaml.Automation.Peers.AutomationControlType GetAutomationControlTypeCore() =>
            Microsoft.UI.Xaml.Automation.Peers.AutomationControlType.Button;
        protected override object? GetPatternCore(Microsoft.UI.Xaml.Automation.Peers.PatternInterface pattern) =>
            pattern == Microsoft.UI.Xaml.Automation.Peers.PatternInterface.Toggle ? this : base.GetPatternCore(pattern);

        public Microsoft.UI.Xaml.Automation.ToggleState ToggleState =>
            card.IsOn ? Microsoft.UI.Xaml.Automation.ToggleState.On : Microsoft.UI.Xaml.Automation.ToggleState.Off;

        public void Toggle() => card.RaiseToggle();
    }

    private sealed class PercentConverter : Microsoft.UI.Xaml.Data.IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, string language) => $"{Math.Round((double)value * 100)}%";
        public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotSupportedException();
    }
}
