using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using WpfWindow = System.Windows.Window;

namespace Metis.Desktop.Window;

public partial class TitleBar : UserControl
{
    public TitleBar()
    {
        InitializeComponent();
        // 空白区拖窗
        MouseLeftButtonDown += (_, e) =>
        {
            if (e.ButtonState == MouseButtonState.Pressed)
            {
                WpfWindow.GetWindow(this)?.DragMove();
            }
        };
        HoverEffect(MinimizeButton, closeHover: false);
        HoverEffect(MaximizeButton, closeHover: false);
        HoverEffect(CloseButton, closeHover: true);
    }

    private static void HoverEffect(System.Windows.Controls.Button button, bool closeHover)
    {
        var normal = (Brush)Application.Current.Resources["TitleBarForeground"];
        var hoverBg = (Brush)Application.Current.Resources[closeHover ? "TitleBarCloseHover" : "TitleBarButtonHover"];
        var hoverFg = closeHover ? Brushes.White : normal;
        button.MouseEnter += (_, _) => { button.Background = hoverBg; button.Foreground = hoverFg; };
        button.MouseLeave += (_, _) => { button.Background = Brushes.Transparent; button.Foreground = normal; };
    }

    private void OnMinimize(object sender, RoutedEventArgs e) =>
        WpfWindow.GetWindow(this)!.WindowState = WindowState.Minimized;

    private void OnMaximize(object sender, RoutedEventArgs e)
    {
        var window = WpfWindow.GetWindow(this)!;
        window.WindowState = window.WindowState == WindowState.Maximized
            ? WindowState.Normal
            : WindowState.Maximized;
    }

    private void OnClose(object sender, RoutedEventArgs e) =>
        WpfWindow.GetWindow(this)!.Close();
}
