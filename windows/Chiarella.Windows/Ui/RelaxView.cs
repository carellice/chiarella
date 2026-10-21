using Chiarella.Data;
using Microsoft.UI;
using Microsoft.UI.Text;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;
using Microsoft.UI.Xaml.Shapes;
using Windows.System;
using Windows.UI;

namespace Chiarella.Ui;

/// <summary>
/// "Un momento per te": l'utente sceglie le sue passioni, fa un breve esercizio di respirazione
/// e poi Chiarella gli propone cosa fare per rilassarsi.
/// </summary>
public sealed class RelaxView : Grid
{
    private enum Step { Passions, Breathe, Reveal }

    /// <summary>Inspira 4 s, trattieni 2 s, espira 6 s (l'espirazione lunga calma), per 4 cicli.</summary>
    private static readonly (string Label, string Hint, int Seconds, double? To)[] Breath =
    [
        ("Inspira", "lentamente dal naso", 4, 1),
        ("Trattieni", "senza sforzo", 2, null),
        ("Espira", "piano, dalla bocca", 6, 0),
    ];
    private const int Cycles = 4;

    private static Brush TextBrush => (Brush)Application.Current.Resources["TextBrush"];
    private static Brush MutedBrush => (Brush)Application.Current.Resources["MutedBrush"];
    private static Brush FaintBrush => (Brush)Application.Current.Resources["FaintBrush"];
    private static Brush GlassBorder => (Brush)Application.Current.Resources["GlassBorderBrush"];

    private Store _store = new();
    private readonly Grid _host = new();
    private readonly Button _edit;
    private Step _step;
    private int _run;
    private PassionCategory? _selected;

    // Respiro
    private double _breath;
    private Func<double>? _breathAnim;
    private readonly Canvas _flower = new() { Width = 340, Height = 340, IsHitTestVisible = false };
    private readonly Ellipse _flowerHalo = new();
    private readonly Ellipse[] _petals = new Ellipse[6];
    private readonly Ellipse _ring = new() { StrokeThickness = 1, Stroke = new SolidColorBrush(Colors.White) };
    private readonly List<(Ellipse Shape, double Angle, double Dist)> _sparks = [];

    public event Action? Closed;

    /// <summary>Colori da dare allo sfondo: dipendono dal passo (vuoto = colori di riposo).</summary>
    public IReadOnlyList<Color> AuroraColors { get; private set; } = [];

    public RelaxView()
    {
        RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        RowDefinitions.Add(new RowDefinition());
        var top = new Grid { Padding = new Thickness(24, 8, 24, 8) };
        var close = RoundButton("", "Chiudi");
        close.Click += (_, _) => Close();
        _edit = RoundButton("", "Le mie passioni");
        _edit.HorizontalAlignment = HorizontalAlignment.Right;
        _edit.Click += (_, _) => Show(Step.Passions);
        top.Children.Add(close);
        top.Children.Add(_edit);
        Children.Add(top);
        SetRow(_host, 1);
        Children.Add(_host);
        BuildFlower();
        KeyDown += (_, e) =>
        {
            if (e.Key == VirtualKey.Escape) { Close(); e.Handled = true; }
        };
    }

    public void Open(Store store)
    {
        _store = store;
        Visibility = Visibility.Visible;
        Anim.Rise(this, 0, 24);
        Show(store.Passions.Count == 0 ? Step.Passions : Step.Breathe);
    }

    private void Close()
    {
        _run++;
        AuroraColors = [];
        Visibility = Visibility.Collapsed;
        Closed?.Invoke();
    }

    private void Show(Step step)
    {
        _run++;
        _step = step;
        _breathAnim = null;
        _edit.Visibility = step == Step.Passions ? Visibility.Collapsed : Visibility.Visible;
        _host.Children.Clear();
        UIElement view = step switch
        {
            Step.Passions => BuildPassions(),
            Step.Breathe => BuildBreathe(),
            _ => BuildReveal(),
        };
        AuroraColors = step == Step.Breathe ? [Catalog.Hex("#5B8DEF"), Catalog.Hex("#8B7CF6"), Catalog.Hex("#14B8A6")] : [];
        _host.Children.Add(view);
        Anim.Rise(view, 80, 20);
    }

    // ------------------------------------------------------------------ passioni

    private UIElement BuildPassions()
    {
        var root = new Grid { MaxWidth = 760, Padding = new Thickness(32, 0, 32, 16) };
        root.RowDefinitions.Add(new RowDefinition());
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        var body = new StackPanel { Spacing = 0 };
        body.Children.Add(new TextBlock { Text = "UN MOMENTO PER TE", Style = (Style)Application.Current.Resources["OverlineText"] });
        body.Children.Add(new TextBlock { Text = "Le tue passioni", FontSize = 40, FontWeight = FontWeights.SemiBold, Foreground = TextBrush, Margin = new Thickness(0, 4, 0, 8) });
        body.Children.Add(new TextBlock
        {
            Text = "Dimmi cosa ti piace fare. Quando vorrai rilassarti, dopo un breve respiro guidato, ti proporrò io qualcosa tra queste.",
            FontSize = 16, Foreground = MutedBrush, TextWrapping = TextWrapping.Wrap,
        });

        var chips = new WrapPanel { Margin = new Thickness(0, 22, 0, 0) };
        var form = new StackPanel { Spacing = 10, Padding = new Thickness(18), Margin = new Thickness(0, 16, 0, 0), CornerRadius = new CornerRadius(24), BorderThickness = new Thickness(1), BorderBrush = GlassBorder, Background = new SolidColorBrush(Color.FromArgb(0x0D, 255, 255, 255)), Visibility = Visibility.Collapsed };
        var formTitle = new TextBlock { FontSize = 16, FontWeight = FontWeights.SemiBold, Foreground = TextBrush };
        var detail = new TextBox { MaxLength = 40, CornerRadius = new CornerRadius(14), Padding = new Thickness(14, 12, 14, 12), FontSize = 15 };
        var add = new Button { Content = "Aggiungi", Style = (Style)Application.Current.Resources["GradientButton"] };
        form.Children.Add(formTitle);
        form.Children.Add(new TextBlock { Text = "Facoltativo: scrivi cosa in particolare, così le proposte saranno più tue.", FontSize = 13, Foreground = MutedBrush, TextWrapping = TextWrapping.Wrap });
        form.Children.Add(detail);
        form.Children.Add(add);

        var listTitle = new TextBlock { FontSize = 18, FontWeight = FontWeights.SemiBold, Foreground = TextBrush, Margin = new Thickness(0, 26, 0, 10) };
        var list = new StackPanel { Spacing = 10 };
        var start = new Button { Style = (Style)Application.Current.Resources["GradientButton"], Margin = new Thickness(0, 14, 0, 0) };
        start.Click += (_, _) => Show(Step.Breathe);

        void RefreshList()
        {
            list.Children.Clear();
            foreach (var p in _store.Passions) list.Children.Add(PassionRow(p, () =>
            {
                _store.Passions.Remove(p);
                _store.Save();
                RefreshList();
            }));
            var n = _store.Passions.Count;
            listTitle.Text = n == 0 ? "Nessuna passione, per ora" : $"Le tue passioni ({n})";
            start.IsEnabled = n > 0;
            start.Opacity = n > 0 ? 1 : 0.4;
            start.Content = n == 0 ? "Aggiungi almeno una passione" : "Respira e scopri cosa fare";
        }

        var chipButtons = new List<(Button Button, PassionCategory Category)>();
        void RefreshChips()
        {
            foreach (var (b, c) in chipButtons)
            {
                var sel = _selected == c;
                b.Background = new SolidColorBrush(sel ? c.Primary.WithAlpha(0.32) : Color.FromArgb(0x10, 255, 255, 255));
                b.BorderBrush = sel ? new SolidColorBrush(c.Secondary.WithAlpha(0.8)) : GlassBorder;
            }
            form.Visibility = _selected is null ? Visibility.Collapsed : Visibility.Visible;
            if (_selected is { } cat)
            {
                formTitle.Text = cat.Label;
                detail.PlaceholderText = "Es. " + cat.Example;
            }
        }

        foreach (var c in Catalog.Categories)
        {
            var b = new Button
            {
                Content = $"{c.Icon}  {c.Label}", CornerRadius = new CornerRadius(20), Padding = new Thickness(14, 9, 14, 9),
                BorderThickness = new Thickness(1), FontWeight = FontWeights.SemiBold,
            };
            b.Click += (_, _) =>
            {
                _selected = _selected == c ? null : c;
                RefreshChips();
                if (_selected is not null)
                {
                    Anim.Pop(b, 0.88);
                    Anim.Rise(form, 0, 16);
                    detail.Focus(FocusState.Programmatic);
                }
            };
            chipButtons.Add((b, c));
            chips.Children.Add(b);
        }

        void AddPassion()
        {
            if (_selected is null) return;
            _store.Passions.Add(new Passion { Category = _selected.Id, Detail = detail.Text.Trim() });
            _store.Save();
            _selected = null;
            detail.Text = "";
            RefreshChips();
            RefreshList();
            if (list.Children.LastOrDefault() is UIElement last) Anim.Rise(last, 0, 30);
        }
        add.Click += (_, _) => AddPassion();
        detail.KeyDown += (_, e) =>
        {
            if (e.Key == VirtualKey.Enter) { AddPassion(); e.Handled = true; }
        };

        body.Children.Add(chips);
        body.Children.Add(form);
        body.Children.Add(listTitle);
        body.Children.Add(list);
        RefreshChips();
        RefreshList();

        root.Children.Add(new ScrollViewer { Content = body, Padding = new Thickness(0, 0, 12, 0) });
        SetRow(start, 1);
        root.Children.Add(start);
        return root;
    }

    private static UIElement PassionRow(Passion p, Action onDelete)
    {
        var c = Catalog.Category(p.Category);
        var row = new Grid
        {
            ColumnSpacing = 12, Padding = new Thickness(12, 10, 12, 10), CornerRadius = new CornerRadius(20),
            Background = new SolidColorBrush(Color.FromArgb(0x0D, 255, 255, 255)), BorderBrush = GlassBorder, BorderThickness = new Thickness(1),
        };
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.ColumnDefinitions.Add(new ColumnDefinition());
        row.ColumnDefinitions.Add(new ColumnDefinition { Width = GridLength.Auto });
        row.Children.Add(Paint.Badge(c.Icon, c.Primary, c.Secondary, 40));
        var text = new StackPanel { VerticalAlignment = VerticalAlignment.Center };
        text.Children.Add(new TextBlock { Text = p.Title, FontWeight = FontWeights.SemiBold, Foreground = TextBrush });
        if (!string.IsNullOrWhiteSpace(p.Detail)) text.Children.Add(new TextBlock { Text = c.Label, FontSize = 12, Foreground = MutedBrush });
        SetColumn(text, 1);
        row.Children.Add(text);
        var remove = RoundButton("", "Rimuovi");
        remove.Width = remove.Height = 34;
        remove.Click += (_, _) => onDelete();
        SetColumn(remove, 2);
        row.Children.Add(remove);
        return row;
    }

    // ------------------------------------------------------------------ respiro

    private void BuildFlower()
    {
        _flower.Children.Add(_flowerHalo);
        string[] colors = ["#5B8DEF", "#8B7CF6", "#14B8A6", "#5EC8F2", "#C4BCFF", "#99F6E4"];
        for (var i = 0; i < 6; i++)
        {
            var col = Catalog.Hex(colors[i]);
            _petals[i] = new Ellipse
            {
                Fill = new RadialGradientBrush
                {
                    GradientStops = { new GradientStop { Color = col.WithAlpha(0.55) }, new GradientStop { Color = col.WithAlpha(0.12), Offset = 1 } },
                },
            };
            _flower.Children.Add(_petals[i]);
        }
        _flower.Children.Add(_ring);
        var r = new Random(11);
        for (var i = 0; i < 18; i++)
        {
            var size = 2 + r.NextDouble() * 3;
            var spark = new Ellipse { Width = size, Height = size, Fill = new SolidColorBrush(Colors.White) };
            _flower.Children.Add(spark);
            _sparks.Add((spark, r.NextDouble() * 6.28, 0.75 + r.NextDouble() * 0.35));
        }
    }

    private UIElement BuildBreathe()
    {
        var root = new Grid { Padding = new Thickness(24, 0, 24, 16) };
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition());
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        var head = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
        head.Children.Add(new TextBlock { Text = "RESPIRA CON ME", Style = (Style)Application.Current.Resources["OverlineText"], HorizontalAlignment = HorizontalAlignment.Center });
        head.Children.Add(new TextBlock { Text = "Prima di tutto, un minuto per te", FontSize = 28, FontWeight = FontWeights.SemiBold, Foreground = TextBrush, Margin = new Thickness(0, 4, 0, 0) });
        root.Children.Add(head);

        var center = new Grid();
        // Il fiore è sempre lo stesso: va staccato dalla vista del respiro precedente.
        if (_flower.Parent is Panel previous) previous.Children.Remove(_flower);
        center.Children.Add(_flower);
        var phase = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        var phaseLabel = new TextBlock { FontSize = 32, FontWeight = FontWeights.Light, Foreground = new SolidColorBrush(Colors.White), HorizontalAlignment = HorizontalAlignment.Center };
        var phaseHint = new TextBlock { FontSize = 14, Foreground = new SolidColorBrush(Color.FromArgb(0xC0, 255, 255, 255)), HorizontalAlignment = HorizontalAlignment.Center };
        phase.Children.Add(phaseLabel);
        phase.Children.Add(phaseHint);
        center.Children.Add(phase);
        SetRow(center, 1);
        root.Children.Add(center);

        var bottom = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, Spacing = 12 };
        var count = new TextBlock { FontSize = 40, FontWeight = FontWeights.Light, Foreground = TextBrush, HorizontalAlignment = HorizontalAlignment.Center };
        var dots = new StackPanel { Orientation = Orientation.Horizontal, Spacing = 8, HorizontalAlignment = HorizontalAlignment.Center };
        var dotList = Enumerable.Range(0, Cycles).Select(_ => new Border { Height = 8, Width = 8, CornerRadius = new CornerRadius(4), Background = new SolidColorBrush(Color.FromArgb(0x2E, 255, 255, 255)) }).ToList();
        dotList.ForEach(dots.Children.Add);
        var skip = new HyperlinkButton { Content = "Salta", Foreground = FaintBrush, HorizontalAlignment = HorizontalAlignment.Center };
        skip.Click += (_, _) => { _run++; Pick(); };
        bottom.Children.Add(count);
        bottom.Children.Add(dots);
        bottom.Children.Add(skip);
        SetRow(bottom, 2);
        root.Children.Add(bottom);

        void SetPhase(string label, string hint)
        {
            phaseLabel.Text = label;
            phaseHint.Text = hint;
            Anim.Rise(phase, 0, 14);
        }

        _ = RunBreathing(SetPhase, count, dotList);
        return root;
    }

    private async Task RunBreathing(Action<string, string> setPhase, TextBlock count, List<Border> dots)
    {
        var run = _run;
        bool Alive() => run == _run;
        _breath = 0;
        setPhase("Mettiti comodo", "e chiudi gli occhi, se vuoi");
        for (var s = 3; s >= 1; s--)
        {
            count.Text = s.ToString();
            await Task.Delay(1000);
            if (!Alive()) return;
        }
        for (var c = 0; c < Cycles; c++)
        {
            dots[c].Width = 26;
            dots[c].Background = new SolidColorBrush(Color.FromArgb(0xC8, 255, 255, 255));
            foreach (var (label, hint, seconds, to) in Breath)
            {
                setPhase(label, hint);
                if (to is { } target)
                {
                    var from = _breath;
                    var started = DateTime.Now;
                    _breathAnim = () =>
                    {
                        var k = Math.Min((DateTime.Now - started).TotalSeconds / seconds, 1);
                        var eased = k < 0.5 ? 2 * k * k : 1 - Math.Pow(-2 * k + 2, 2) / 2;
                        return from + (target - from) * eased;
                    };
                }
                else _breathAnim = null;
                for (var s = seconds; s >= 1; s--)
                {
                    count.Text = s.ToString();
                    await Task.Delay(1000);
                    if (!Alive()) return;
                }
            }
        }
        _breathAnim = null;
        await Task.Delay(400);
        if (Alive()) Pick();
    }

    /// <summary>Un fotogramma del fiore che respira: si apre con l'inspirazione e si chiude con l'espirazione.</summary>
    public void Tick(double t)
    {
        if (Visibility != Visibility.Visible || _step != Step.Breathe) return;
        if (_breathAnim is not null) _breath = _breathAnim();
        var b = _breath;
        const double cx = 170, cy = 170, maxR = 170;
        var spin = t / 40 * Math.PI * 2;
        var shimmer = (Math.Sin(t / 3 * Math.PI) + 1) / 2;

        var hr = maxR * (0.7 + 0.3 * b);
        _flowerHalo.Width = _flowerHalo.Height = hr * 2;
        Canvas.SetLeft(_flowerHalo, cx - hr);
        Canvas.SetTop(_flowerHalo, cy - hr);
        Paint.SetGlow(_flowerHalo, Catalog.Hex("#8B7CF6"), 0.35 + 0.2 * b);

        var dist = maxR * (0.05 + 0.33 * b);
        var pr = maxR * (0.3 + 0.16 * b);
        for (var i = 0; i < 6; i++)
        {
            var a = spin * 0.6 + i * Math.PI / 3 + b * 0.5;
            var p = _petals[i];
            p.Width = p.Height = pr * 2;
            Canvas.SetLeft(p, cx + Math.Cos(a) * dist - pr);
            Canvas.SetTop(p, cy + Math.Sin(a) * dist - pr);
        }
        var rr = maxR * (0.8 + 0.1 * b);
        _ring.Width = _ring.Height = rr * 2;
        Canvas.SetLeft(_ring, cx - rr);
        Canvas.SetTop(_ring, cy - rr);
        _ring.Opacity = 0.25 + 0.15 * shimmer;
        foreach (var (shape, angle, d) in _sparks)
        {
            var a = angle + spin * (0.8 + d * 0.2);
            var r = maxR * d * (0.7 + 0.3 * b);
            Canvas.SetLeft(shape, cx + Math.Cos(a) * r);
            Canvas.SetTop(shape, cy + Math.Sin(a) * r);
            shape.Opacity = 0.35 + 0.4 * shimmer * b;
        }
    }

    // ------------------------------------------------------------------ proposta

    private Passion? _picked;

    private void Pick()
    {
        var all = _store.Passions;
        if (all.Count == 0)
        {
            Show(Step.Passions);
            return;
        }
        var pool = all.Where(p => p.Id != _store.LastPassionId).ToList();
        if (pool.Count == 0) pool = all;
        _picked = pool[Random.Shared.Next(pool.Count)];
        _store.LastPassionId = _picked.Id;
        _store.Save();
        Show(Step.Reveal);
    }

    private UIElement BuildReveal()
    {
        var chosen = _picked!;
        var all = _store.Passions.ToList();
        var root = new Grid { Padding = new Thickness(24, 0, 24, 20), MaxWidth = 720 };
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });
        root.RowDefinitions.Add(new RowDefinition());
        root.RowDefinitions.Add(new RowDefinition { Height = GridLength.Auto });

        var head = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center };
        head.Children.Add(new TextBlock { Text = "BEN FATTO, ORA VA MEGLIO", Style = (Style)Application.Current.Resources["OverlineText"], HorizontalAlignment = HorizontalAlignment.Center });
        head.Children.Add(new TextBlock { Text = "Ecco cosa ti propongo", FontSize = 28, FontWeight = FontWeights.SemiBold, Foreground = TextBrush, Margin = new Thickness(0, 4, 0, 0) });
        root.Children.Add(head);

        var center = new StackPanel { VerticalAlignment = VerticalAlignment.Center, HorizontalAlignment = HorizontalAlignment.Center };
        var badgeHost = new Grid { Width = 240, Height = 240, HorizontalAlignment = HorizontalAlignment.Center };
        var halo = new Ellipse();
        var badgeSlot = new Grid { HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        badgeHost.Children.Add(halo);
        badgeHost.Children.Add(badgeSlot);
        var title = new TextBlock { FontSize = 30, FontWeight = FontWeights.SemiBold, Foreground = TextBrush, HorizontalAlignment = HorizontalAlignment.Center, TextAlignment = TextAlignment.Center, TextWrapping = TextWrapping.Wrap };
        var idea = new TextBlock { FontSize = 18, Foreground = MutedBrush, HorizontalAlignment = HorizontalAlignment.Center, TextAlignment = TextAlignment.Center, TextWrapping = TextWrapping.Wrap, MaxWidth = 520, Margin = new Thickness(0, 12, 0, 0), Opacity = 0 };
        center.Children.Add(badgeHost);
        center.Children.Add(title);
        center.Children.Add(idea);
        SetRow(center, 1);
        root.Children.Add(center);

        var actions = new StackPanel { Spacing = 10, Opacity = 0, IsHitTestVisible = false };
        var done = new Button { Content = "Perfetto, lo faccio", Style = (Style)Application.Current.Resources["GradientButton"] };
        done.Click += (_, _) => Close();
        var row = new Grid { ColumnSpacing = 10 };
        row.ColumnDefinitions.Add(new ColumnDefinition());
        row.ColumnDefinitions.Add(new ColumnDefinition());
        var another = new Button { Content = "↻  Un'altra idea", Style = (Style)Application.Current.Resources["PillButton"] };
        another.Click += (_, _) => Pick();
        var again = new Button { Content = "Respira ancora", Style = (Style)Application.Current.Resources["PillButton"] };
        again.Click += (_, _) => Show(Step.Breathe);
        SetColumn(again, 1);
        if (all.Count > 1) row.Children.Add(another);
        else SetColumnSpan(again, 2);
        row.Children.Add(again);
        actions.Children.Add(done);
        actions.Children.Add(row);
        SetRow(actions, 2);
        root.Children.Add(actions);

        void ShowPassion(Passion p)
        {
            var c = Catalog.Category(p.Category);
            badgeSlot.Children.Clear();
            badgeSlot.Children.Add(Paint.Badge(c.Icon, c.Primary, c.Secondary, 120));
            title.Text = p.Title;
        }

        // Breve "rullo": le passioni scorrono rallentando, poi si ferma su quella scelta.
        async Task Roll()
        {
            var run = _run;
            if (all.Count > 1)
            {
                var wait = 60.0;
                var i = all.IndexOf(chosen) + 1;
                for (var k = 0; k < 12; k++)
                {
                    ShowPassion(all[i++ % all.Count]);
                    Anim.Rise(badgeSlot, 0, 30);
                    await Task.Delay((int)wait);
                    if (run != _run) return;
                    wait *= 1.22;
                }
            }
            var c = Catalog.Category(chosen.Category);
            ShowPassion(chosen);
            Anim.Pop(badgeSlot, 0.7);
            AuroraColors = [c.Primary, c.Secondary, c.Primary];
            Paint.SetGlow(halo, c.Primary, 0.55);
            idea.Text = c.Idea(chosen, Random.Shared.Next(c.Ideas.Length));
            Anim.Fade(idea, 1, 500, 150);
            Anim.Fade(actions, 1, 400, 350);
            actions.IsHitTestVisible = true;
            // Alone che pulsa piano.
            var started = DateTime.Now;
            while (run == _run)
            {
                var s = 0.9 + 0.25 * (Math.Sin((DateTime.Now - started).TotalSeconds / 2.4 * Math.PI) + 1) / 2;
                halo.Width = halo.Height = 240 * s;
                await Task.Delay(33);
            }
        }
        _ = Roll();
        return root;
    }

    private static Button RoundButton(string glyph, string tip)
    {
        var b = new Button
        {
            Style = (Style)Application.Current.Resources["RoundButton"],
            Content = new FontIcon { Glyph = glyph, FontSize = 14 },
        };
        ToolTipService.SetToolTip(b, tip);
        Microsoft.UI.Xaml.Automation.AutomationProperties.SetName(b, tip);
        return b;
    }
}
