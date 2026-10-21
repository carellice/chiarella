using Chiarella.Data;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Windows.Foundation;

namespace Chiarella.Ui;

/// <summary>Scena animata di un suono (pioggia, onde, braci…): usata nelle card e nel dettaglio.</summary>
public sealed class AmbienceScene : Canvas
{
    private readonly Sound _sound;
    private readonly List<(FrameworkElement Shape, double X, double Y, double Size, double Speed, double Phase)> _particles = [];
    private readonly Rectangle? _flash;
    private readonly Ellipse? _bottomGlow;
    private readonly Polyline[] _lines = [];

    public AmbienceScene(Sound sound)
    {
        _sound = sound;
        IsHitTestVisible = false;
        // Elementi della scena, diversi per ogni tipo di suono.
        var rnd = new Random(_sound.Id.GetHashCode());
        Shape Make(Ambience a, int i)
        {
            var color = i % 2 == 0 ? _sound.Primary : _sound.Secondary;
            return a switch
            {
                Ambience.Rain or Ambience.Storm => new Rectangle { Width = 1.6, Height = 12, RadiusX = 1, RadiusY = 1, Fill = new SolidColorBrush(_sound.Secondary.WithAlpha(0.5)) },
                Ambience.Fall => new Rectangle
                {
                    Width = 2, Height = 46, RadiusX = 1, RadiusY = 1,
                    Fill = new LinearGradientBrush
                    {
                        StartPoint = new Point(0, 0), EndPoint = new Point(0, 1),
                        GradientStops = { new GradientStop { Color = _sound.Secondary.WithAlpha(0) }, new GradientStop { Color = _sound.Secondary.WithAlpha(0.55), Offset = 1 } },
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
                Ambience.Fireflies => new Ellipse { Width = 14, Height = 14, Fill = Paint.Glow(_sound.Secondary, 0.9) },
                _ => new Ellipse { Width = 4, Height = 4, Fill = new SolidColorBrush(color.WithAlpha(0.85)) },
            };
        }
        var count = _sound.Ambience switch { Ambience.Rain or Ambience.Storm => 24, Ambience.Waves or Ambience.Ripple or Ambience.Heat => 8, _ => 16 };
        for (var i = 0; i < count; i++)
        {
            var shape = Make(_sound.Ambience, i);
            Children.Add(shape);
            _particles.Add((shape, rnd.NextDouble(), rnd.NextDouble(), rnd.NextDouble(), 0.5 + rnd.NextDouble(), rnd.NextDouble() * 6.28));
        }
        if (_sound.Ambience is Ambience.Waves or Ambience.Ripple or Ambience.Heat)
        {
            _lines = Enumerable.Range(0, _sound.Ambience == Ambience.Waves ? 3 : 4).Select(i => new Polyline
            {
                Stroke = new SolidColorBrush((i % 2 == 0 ? _sound.Secondary : _sound.Primary).WithAlpha(0.35 + i * 0.08)),
                StrokeThickness = _sound.Ambience == Ambience.Waves ? 3 : 1.6,
                StrokeLineJoin = PenLineJoin.Round,
            }).ToArray();
            foreach (var l in _lines) Children.Insert(0, l);
        }
        if (_sound.Ambience is Ambience.Embers or Ambience.Heat or Ambience.Fall)
        {
            _bottomGlow = new Ellipse { Fill = Paint.Glow(_sound.Ambience == Ambience.Fall ? _sound.Secondary : _sound.Primary, 0.4) };
            Children.Insert(0, _bottomGlow);
        }
        if (_sound.Ambience == Ambience.Storm)
        {
            _flash = new Rectangle { Fill = new SolidColorBrush(_sound.Secondary), Opacity = 0 };
            Children.Add(_flash);
        }
        SizeChanged += (_, e) =>
        {
            if (_flash is not null) { _flash.Width = e.NewSize.Width; _flash.Height = e.NewSize.Height; }
        };
    }

    /// <summary>Un fotogramma: <paramref name="a"/> è l'intensità (0..1).</summary>
    public void Tick(double sec, double a)
    {
        var w = ActualWidth;
        var h = ActualHeight;
        if (w <= 0 || h <= 0) return;

        foreach (var (shape, px, py, size, speed, phase) in _particles)
        {
            double x, y, op = a;
            switch (_sound.Ambience)
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
                    y = h * (0.2 + py * 0.7) + Math.Sin(sec * 2 + phase) * (_sound.Ambience == Ambience.Airflow ? 8 : 3);
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
            var waves = _sound.Ambience == Ambience.Waves;
            var baseY = _sound.Ambience switch
            {
                Ambience.Waves => h * (0.62 + i * 0.1),
                Ambience.Heat => h * (0.55 + i * 0.1) - (sec * 12 + i * 20) % 30,
                _ => h * (0.45 + i * 0.11),
            };
            for (double x = 0; x <= w + 6; x += 8)
            {
                var y = waves
                    ? baseY + Math.Sin(x / w * 6 + sec * (0.8 + i * 0.3) + i * 2) * 8
                    : baseY + Math.Sin(x / w * (_sound.Ambience == Ambience.Heat ? 9 : 12.5) - sec * (1.4 + i * 0.2) + i) * (_sound.Ambience == Ambience.Heat ? 4 : 3);
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
}
