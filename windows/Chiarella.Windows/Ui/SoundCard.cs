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
    private readonly AmbienceScene _scene;
    private bool _active;
    private bool _syncing;

    public Sound Sound { get; }
    public event Action? Toggled;
    public event Action<double>? VolumeChanged;
    /// <summary>Clic destro, pressione lunga (touch/penna) o tasto Menu: apre il dettaglio del suono.</summary>
    public event Action<SoundCard>? DetailsRequested;

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
        _scene = new AmbienceScene(sound) { Opacity = 0 };
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


        // Angoli arrotondati anche per la scena.
        SizeChanged += (_, e) =>
        {
            var visual = ElementCompositionPreview.GetElementVisual(this);
            var clip = visual.Compositor.CreateRectangleClip(0, 0, (float)e.NewSize.Width, (float)e.NewSize.Height,
                new((float)Radius), new((float)Radius), new((float)Radius), new((float)Radius));
            visual.Clip = clip;
        };

        Tapped += (_, e) =>
        {
            if (IsInside(e.OriginalSource as DependencyObject, _volume)) return;
            Toggled?.Invoke();
        };
        KeyDown += (_, e) =>
        {
            if (e.Key is VirtualKey.Enter or VirtualKey.Space && ReferenceEquals(e.OriginalSource, this))
            {
                Toggled?.Invoke();
                e.Handled = true;
            }
        };
        // Clic destro, pressione lunga sul touch e tasto Menu/Maiusc+F10 arrivano tutti qui.
        ContextRequested += (_, e) =>
        {
            e.Handled = true;
            PressScale(1);
            OpenDetails();
        };
        PointerPressed += (_, e) => { if (!IsInside(e.OriginalSource as DependencyObject, _volume)) PressScale(0.96); };
        PointerReleased += (_, _) => PressScale(1);
        PointerCanceled += (_, _) => PressScale(1);
        PointerExited += (_, _) => PressScale(1);
        PointerEntered += (_, _) => ProtectedCursor = InputSystemCursor.Create(InputSystemCursorShape.Hand);
    }

    private static bool IsInside(DependencyObject? node, DependencyObject container)
    {
        for (; node is not null; node = VisualTreeHelper.GetParent(node))
            if (node == container) return true;
        return false;
    }

    private void PressScale(double s)
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
        if (_active) _scene.Tick(sec, playing ? 1.0 : 0.35);
    }

    public bool IsOn => _active;

    public void RaiseToggle() => Toggled?.Invoke();

    public void OpenDetails() => DetailsRequested?.Invoke(this);

    protected override Microsoft.UI.Xaml.Automation.Peers.AutomationPeer OnCreateAutomationPeer() => new CardPeer(this);

    /// <summary>Per gli screen reader (e l'automazione): la card è un interruttore con il nome del suono.</summary>
    private sealed class CardPeer(SoundCard card) : Microsoft.UI.Xaml.Automation.Peers.FrameworkElementAutomationPeer(card),
        Microsoft.UI.Xaml.Automation.Provider.IToggleProvider, Microsoft.UI.Xaml.Automation.Provider.IExpandCollapseProvider
    {
        protected override string GetNameCore() => card.Sound.Name;
        protected override string GetClassNameCore() => nameof(SoundCard);
        protected override Microsoft.UI.Xaml.Automation.Peers.AutomationControlType GetAutomationControlTypeCore() =>
            Microsoft.UI.Xaml.Automation.Peers.AutomationControlType.Button;
        protected override object? GetPatternCore(Microsoft.UI.Xaml.Automation.Peers.PatternInterface pattern) =>
            pattern is Microsoft.UI.Xaml.Automation.Peers.PatternInterface.Toggle or Microsoft.UI.Xaml.Automation.Peers.PatternInterface.ExpandCollapse
                ? this : base.GetPatternCore(pattern);

        public Microsoft.UI.Xaml.Automation.ToggleState ToggleState =>
            card.IsOn ? Microsoft.UI.Xaml.Automation.ToggleState.On : Microsoft.UI.Xaml.Automation.ToggleState.Off;

        public void Toggle() => card.RaiseToggle();

        // "Espandi" apre il dettaglio del suono.
        public Microsoft.UI.Xaml.Automation.ExpandCollapseState ExpandCollapseState => Microsoft.UI.Xaml.Automation.ExpandCollapseState.Collapsed;
        public void Expand() => card.OpenDetails();
        public void Collapse() { }
    }

    private sealed class PercentConverter : Microsoft.UI.Xaml.Data.IValueConverter
    {
        public object Convert(object value, Type targetType, object parameter, string language) => $"{Math.Round((double)value * 100)}%";
        public object ConvertBack(object value, Type targetType, object parameter, string language) => throw new NotSupportedException();
    }
}
