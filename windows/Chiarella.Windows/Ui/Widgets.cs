using Microsoft.UI;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Media.Animation;
using Microsoft.UI.Xaml.Shapes;
using Windows.Foundation;
using Windows.UI;

namespace Chiarella.Ui;

/// <summary>Griglia che si adatta alla larghezza: tante colonne quante ne stanno, celle che si allargano.</summary>
public sealed class UniformWrapPanel : Panel
{
    public double MinItemWidth { get; set; } = 220;
    public double ItemHeight { get; set; } = 200;
    public double Spacing { get; set; } = 12;

    private int Columns(double width) =>
        Math.Max(1, (int)((width + Spacing) / (MinItemWidth + Spacing)));

    protected override Size MeasureOverride(Size available)
    {
        var width = double.IsInfinity(available.Width) ? MinItemWidth * 3 : available.Width;
        var cols = Columns(width);
        var itemW = (width - Spacing * (cols - 1)) / cols;
        foreach (var child in Children) child.Measure(new Size(itemW, ItemHeight));
        var rows = (Children.Count + cols - 1) / cols;
        return new Size(width, rows == 0 ? 0 : rows * ItemHeight + (rows - 1) * Spacing);
    }

    protected override Size ArrangeOverride(Size final)
    {
        var cols = Columns(final.Width);
        var itemW = (final.Width - Spacing * (cols - 1)) / cols;
        for (var i = 0; i < Children.Count; i++)
        {
            var (row, col) = (i / cols, i % cols);
            Children[i].Arrange(new Rect(col * (itemW + Spacing), row * (ItemHeight + Spacing), itemW, ItemHeight));
        }
        return final;
    }
}

/// <summary>Elementi in fila che vanno a capo quando finisce lo spazio (come le chip delle categorie).</summary>
public sealed class WrapPanel : Panel
{
    public double Spacing { get; set; } = 8;

    protected override Size MeasureOverride(Size available) => Layout(available.Width, arrange: false);

    protected override Size ArrangeOverride(Size final)
    {
        Layout(final.Width, arrange: true);
        return final;
    }

    private Size Layout(double width, bool arrange)
    {
        double x = 0, y = 0, rowH = 0, maxW = 0;
        foreach (var child in Children)
        {
            if (!arrange) child.Measure(new Size(width, double.PositiveInfinity));
            var size = child.DesiredSize;
            if (x > 0 && x + size.Width > width)
            {
                x = 0;
                y += rowH + Spacing;
                rowH = 0;
            }
            if (arrange) child.Arrange(new Rect(x, y, size.Width, size.Height));
            x += size.Width + Spacing;
            rowH = Math.Max(rowH, size.Height);
            maxW = Math.Max(maxW, x - Spacing);
        }
        return new Size(double.IsInfinity(width) ? maxW : width, y + rowH);
    }
}

/// <summary>Barrette che danzano mentre la musica suona e si adagiano in pausa.</summary>
public sealed class Equalizer : Grid
{
    private readonly Rectangle[] _bars = new Rectangle[4];
    private bool _playing;

    public Equalizer()
    {
        ColumnSpacing = 2;
        for (var i = 0; i < _bars.Length; i++)
        {
            ColumnDefinitions.Add(new ColumnDefinition());
            _bars[i] = new Rectangle
            {
                Fill = new SolidColorBrush(Color.FromArgb(255, 0x5E, 0xC8, 0xF2)),
                RadiusX = 1.5, RadiusY = 1.5, VerticalAlignment = VerticalAlignment.Bottom, Height = 4,
            };
            SetColumn(_bars[i], i);
            Children.Add(_bars[i]);
        }
    }

    public bool Playing
    {
        get => _playing;
        set => _playing = value;
    }

    public void Tick(double seconds)
    {
        for (var i = 0; i < _bars.Length; i++)
        {
            var wave = (Math.Sin(seconds * Math.PI * 2 / (0.84 + i * 0.26)) + 1) / 2;
            var target = _playing ? 0.25 + 0.75 * wave : 0.2;
            _bars[i].Height = Math.Max(2, (ActualHeight > 0 ? ActualHeight : 14) * target);
        }
    }
}

public static class Anim
{
    public static bool ReducedMotion { get; set; }
    /// <summary>Ingresso: l'elemento sale e appare, con un piccolo ritardo per l'effetto a cascata.</summary>
    public static void Rise(UIElement element, int delayMs, double distance = 36)
    {
        if (ReducedMotion) { element.Opacity = 1; element.RenderTransform = new CompositeTransform(); return; }
        var transform = new CompositeTransform();
        element.RenderTransform = transform;
        element.RenderTransformOrigin = new Point(0.5, 0.5);
        element.Opacity = 0;
        var ease = new CubicEase { EasingMode = EasingMode.EaseOut };
        var sb = new Storyboard();
        Add(sb, element, "Opacity", 0, 1, 700, delayMs, ease);
        Add(sb, transform, "TranslateY", distance, 0, 900, delayMs, ease);
        Add(sb, transform, "ScaleX", 0.94, 1, 900, delayMs, ease);
        Add(sb, transform, "ScaleY", 0.94, 1, 900, delayMs, ease);
        sb.Begin();
    }

    /// <summary>Rimbalzo elastico della scala (per selezioni e "atterraggi").</summary>
    public static void Pop(UIElement element, double from = 0.7)
    {
        if (ReducedMotion) { element.RenderTransform = new CompositeTransform(); return; }
        if (element.RenderTransform is not CompositeTransform transform)
        {
            transform = new CompositeTransform();
            element.RenderTransform = transform;
            element.RenderTransformOrigin = new Point(0.5, 0.5);
        }
        var ease = new ElasticEase { Oscillations = 1, Springiness = 4, EasingMode = EasingMode.EaseOut };
        var sb = new Storyboard();
        Add(sb, transform, "ScaleX", from, 1, 700, 0, ease);
        Add(sb, transform, "ScaleY", from, 1, 700, 0, ease);
        sb.Begin();
    }

    public static void Fade(UIElement element, double to, int ms, int delayMs = 0)
    {
        if (ReducedMotion) { element.Opacity = to; return; }
        var sb = new Storyboard();
        Add(sb, element, "Opacity", element.Opacity, to, ms, delayMs, new QuadraticEase());
        sb.Begin();
    }

    private static void Add(Storyboard sb, DependencyObject target, string property, double from, double to, int ms, int delayMs, EasingFunctionBase ease)
    {
        var a = new DoubleAnimation
        {
            From = from, To = to, Duration = TimeSpan.FromMilliseconds(ms), BeginTime = TimeSpan.FromMilliseconds(delayMs),
            EasingFunction = ease, EnableDependentAnimation = true,
        };
        Storyboard.SetTarget(a, target);
        Storyboard.SetTargetProperty(a, property);
        sb.Children.Add(a);
    }
}

public static class Paint
{
    public static Color WithAlpha(this Color c, double alpha) =>
        Color.FromArgb((byte)Math.Clamp(alpha * 255, 0, 255), c.R, c.G, c.B);

    public static Color Lerp(Color a, Color b, double t) => Color.FromArgb(
        (byte)(a.A + (b.A - a.A) * t), (byte)(a.R + (b.R - a.R) * t), (byte)(a.G + (b.G - a.G) * t), (byte)(a.B + (b.B - a.B) * t));

    public static LinearGradientBrush Diagonal(Color a, Color b) => new()
    {
        StartPoint = new Point(0, 0), EndPoint = new Point(1, 1),
        GradientStops = { new GradientStop { Color = a, Offset = 0 }, new GradientStop { Color = b, Offset = 1 } },
    };

    /// <summary>Bagliore circolare: colore pieno al centro che sfuma nel trasparente.</summary>
    public static RadialGradientBrush Glow(Color c, double alpha) => new()
    {
        GradientStops =
        {
            new GradientStop { Color = c.WithAlpha(alpha), Offset = 0 },
            new GradientStop { Color = c.WithAlpha(alpha * 0.45), Offset = 0.45 },
            new GradientStop { Color = c.WithAlpha(0), Offset = 1 },
        },
    };

    /// <summary>
    /// Aggiorna il bagliore di una forma. WinUI non ridisegna un RadialGradientBrush quando ne cambiano
    /// le tappe, quindi si crea un pennello nuovo, ma solo quando colore o intensità cambiano davvero.
    /// </summary>
    public static void SetGlow(Shape shape, Color c, double alpha)
    {
        var key = (c.R / 3, c.G / 3, c.B / 3, (int)Math.Round(alpha * 100));
        if (shape.Tag is ValueTuple<int, int, int, int> last && last.Equals(key)) return;
        shape.Tag = key;
        shape.Fill = Glow(c, alpha);
    }

    /// <summary>Cerchio con gradiente e un'emoji al centro: icona di suoni e passioni.</summary>
    public static Border Badge(string icon, Color a, Color b, double size) => new()
    {
        Width = size, Height = size, CornerRadius = new CornerRadius(size / 2),
        Background = Diagonal(a, b),
        Child = new TextBlock
        {
            Text = icon, FontSize = size * 0.48, HorizontalAlignment = HorizontalAlignment.Center,
            VerticalAlignment = VerticalAlignment.Center, Foreground = new SolidColorBrush(Colors.White),
        },
    };
}
