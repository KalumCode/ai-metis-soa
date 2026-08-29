using System.Windows;
using Metis.Desktop.Config;
using Metis.Desktop.WebView;

namespace Metis.Desktop.Window;

public partial class MainWindow : System.Windows.Window
{
    private readonly DesktopConfig config;
    private WebViewHost? webViewHost;

    public MainWindow()
    {
        config = DesktopConfig.Load();
        InitializeComponent();
        StateChanged += (_, _) => UpdateMaximizeButton();
        Loaded += async (_, _) => await StartWebViewAsync();
    }

    private async Task StartWebViewAsync()
    {
        webViewHost = new WebViewHost(Web, config);
        try
        {
            await webViewHost.InitializeAsync();
        }
        catch (Exception ex) when (ex.Message.Contains("WebView2"))
        {
            MessageBox.Show(
                "未检测到 Microsoft Edge WebView2 Runtime。\n请从 https://developer.microsoft.com/microsoft-edge/webview2/ 安装后重试。",
                "Metis", MessageBoxButton.OK, MessageBoxImage.Warning);
            Close();
        }
        catch (Exception ex)
        {
            MessageBox.Show($"WebView 初始化失败：\n{ex.Message}", "Metis",
                MessageBoxButton.OK, MessageBoxImage.Error);
            Close();
        }
    }

    private void UpdateMaximizeButton()
    {
        if (TitleBar?.MaximizeButton is null)
        {
            return;
        }
        TitleBar.MaximizeButton.Content = WindowState == WindowState.Maximized ? "" : "";
    }
}
