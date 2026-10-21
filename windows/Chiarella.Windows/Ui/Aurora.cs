using Chiarella.Data;
using Microsoft.UI;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Windows.UI;

namespace Chiarella.Ui;

/// <summary>
/// Sfondo vivo: tre grandi bagliori che fluttuano lentamente e prendono il colore
/// dei suoni attivi, più un cielo di stelle che brillano.
/// </summary>
public sealed class Aurora
{
    private static readonly Color[] Idle = [Catalog.Hex("#3B2F8F"), Catalog.Hex("#1F4E8C"), Catalog.Hex("#5B2C83")];

    private readonly Canvas _canvas;
    private readonly Ellipse[] _glows = new Ellipse[3];
    // In virgola mobile: con i byte i passi piccoli si arrotondano a zero e il colore non arriva mai.
    private readonly (double R, double G, double B)[] _colors = Idle.Select(c => ((double)c.R, (double)c.G, (double)c.B)).ToArray();
    private readonly List<(Ellipse Shape, double X, double Y, double Phase)> _stars = [];
    private double _intensity = 0.55;

    public Aurora(Canvas canvas)
    {
        _canvas = canvas;
        for (var i = 0; i < 3; i++)
        {
            _glows[i] = new Ellipse();
            canvas.Children.Add(_glows[i]);
        }
        var r = new Random(7);
        for (var i = 0; i < 70; i++)
        {
            var size = 1 + r.NextDouble() * 2.6;
            var star = new Ellipse { Width = size, Height = size, Fill = new SolidColorBrush(Colors.White) };
            canvas.Children.Add(star);
            _stars.Add((star, r.NextDouble(), r.NextDouble() * 0.8, r.NextDouble() * 6.28));
        }
    }

    /// <param name="colors">Colori dei suoni attivi (o di una passione); vuoto = colori di riposo.</param>
    public void Update(double t, IReadOnlyList<Color> colors, bool lively)
    {
        var w = _canvas.ActualWidth;
        var h = _canvas.ActualHeight;
        if (w <= 0 || h <= 0) return;

        _intensity += ((lively ? 1 : 0.55) - _intensity) * 0.02;
        var s = t / 36 * Math.PI * 2;
        var pulse = lively ? 0.975 + 0.125 * Math.Sin(t / 5 * Math.PI) : 1;
        var big = Math.Max(w, h * 0.7);
        (double X, double Y, double R, double A)[] spots =
        [
            (w * (0.2 + 0.15 * Math.Cos(s)), h * (0.18 + 0.06 * Math.Sin(s * 2)), big * 0.85 * pulse, 0.42),
            (w * (0.85 + 0.1 * Math.Sin(s + 1)), h * (0.45 + 0.08 * Math.Cos(s)), big * 0.75, 0.32),
            (w * (0.35 + 0.2 * Math.Sin(s + 2.5)), h * (0.9 + 0.05 * Math.Cos(s * 2)), big * 0.9 * pulse, 0.30),
        ];
        for (var i = 0; i < 3; i++)
        {
            var target = colors.Count == 0 ? Idle[i] : colors[i % colors.Count];
            var (cr, cg, cb) = _colors[i];
            _colors[i] = (cr + (target.R - cr) * 0.03, cg + (target.G - cg) * 0.03, cb + (target.B - cb) * 0.03);
            var color = Color.FromArgb(255, (byte)Math.Round(_colors[i].R), (byte)Math.Round(_colors[i].G), (byte)Math.Round(_colors[i].B));
            var g = _glows[i];
            Paint.SetGlow(g, color, spots[i].A * _intensity);
            g.Width = g.Height = spots[i].R * 2;
            Canvas.SetLeft(g, spots[i].X - spots[i].R);
            Canvas.SetTop(g, spots[i].Y - spots[i].R);
        }

        var tw = t / 6 * Math.PI * 2;
        foreach (var (shape, x, y, phase) in _stars)
        {
            Canvas.SetLeft(shape, x * w);
            Canvas.SetTop(shape, y * h);
            shape.Opacity = 0.8 * (0.25 + 0.55 * ((Math.Sin(tw * (0.6 + phase % 1) + phase) + 1) / 2));
        }
    }
}
