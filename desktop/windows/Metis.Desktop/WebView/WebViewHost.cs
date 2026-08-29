using System.IO;
using Metis.Desktop.Bridge;
using Metis.Desktop.Config;
using Microsoft.Web.WebView2.Core;
using Microsoft.Web.WebView2.Wpf;

namespace Metis.Desktop.WebView;

/// <summary>
/// WebView2 宿主：虚拟主机映射本地 webdist 产物，注入后端地址，加载应用页面。
/// </summary>
public sealed class WebViewHost
{
    public const string VirtualHost = "app.local";

    private readonly WebView2 webView;
    private readonly DesktopConfig config;
    private DesktopBridge? bridge;

    public WebViewHost(WebView2 webView, DesktopConfig config)
    {
        this.webView = webView;
        this.config = config;
    }

    public async Task InitializeAsync()
    {
        string userDataFolder = Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Metis", "WebView2");
        Directory.CreateDirectory(userDataFolder);

        var options = new CoreWebView2EnvironmentOptions
        {
            AdditionalBrowserArguments = string.Join(' ',
                "--disable-renderer-backgrounding",
                "--disable-background-timer-throttling"),
        };
        CoreWebView2Environment environment =
            await CoreWebView2Environment.CreateAsync(null, userDataFolder, options);
        await webView.EnsureCoreWebView2Async(environment);

        CoreWebView2 core = webView.CoreWebView2;
        core.Settings.AreDefaultContextMenusEnabled = false;
        core.Settings.IsStatusBarEnabled = false;
        core.Settings.IsZoomControlEnabled = false;
        core.Settings.AreDevToolsEnabled = true;

        // webdist 产物目录 -> https://app.local/
        string webDistRoot = ResolveWebDistRoot();
        core.SetVirtualHostNameToFolderMapping(
            VirtualHost, webDistRoot, CoreWebView2HostResourceAccessKind.Allow);

        bridge = new DesktopBridge(core, config, OnBackendChanged);
        await core.AddScriptToExecuteOnDocumentCreatedAsync(bridge.MakeRuntimeScript());

        core.NavigationCompleted += (_, _) =>
            Console.Error.WriteLine("[metis-desktop] navigation completed");

        core.Navigate($"https://{VirtualHost}/index.html");
    }

    private void OnBackendChanged()
    {
        // 后端地址变更后重新加载页面以应用新配置
        webView.CoreWebView2.Navigate($"https://{VirtualHost}/index.html");
    }

    /// <summary>定位 webdist：优先输出目录旁的 webdist，其次工程 Resources 目录（开发期）。</summary>
    private static string ResolveWebDistRoot()
    {
        string[] candidates =
        {
            Path.Combine(AppContext.BaseDirectory, "webdist"),
            Path.Combine(AppContext.BaseDirectory, "Resources", "webdist"),
        };
        foreach (string candidate in candidates)
        {
            if (File.Exists(Path.Combine(candidate, "index.html")))
            {
                return candidate;
            }
        }
        throw new InvalidOperationException(
            $"未找到 webdist 产物（index.html）。请先运行 build.ps1 生成前端产物。已尝试: {string.Join(", ", candidates)}");
    }
}
