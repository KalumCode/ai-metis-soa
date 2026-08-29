using System.Text.Json;
using Metis.Desktop.Config;
using Microsoft.Web.WebView2.Core;

namespace Metis.Desktop.Bridge;

/// <summary>
/// Web 侧桥：页面通过 window.chrome.web.postMessage 请求配置/修改后端地址。
/// 协议：{"type":"get-config"} / {"type":"set-backend","url":"..."}，
/// 应答 postMessage 回 {"type":"config","backendUrl":"..."}。
/// </summary>
public sealed class DesktopBridge
{
    private readonly CoreWebView2 core;
    private readonly DesktopConfig config;
    private readonly Action onBackendChanged;

    public DesktopBridge(CoreWebView2 core, DesktopConfig config, Action onBackendChanged)
    {
        this.core = core;
        this.config = config;
        this.onBackendChanged = onBackendChanged;
        core.WebMessageReceived += OnWebMessageReceived;
    }

    /// <summary>注入初始配置脚本（每次文档创建时执行）。</summary>
    public string MakeRuntimeScript() =>
        $"window.__METIS_CONFIG__ = {{ backendUrl: {JsonSerializer.Serialize(config.BackendUrl)} }};";

    private void OnWebMessageReceived(object? sender, CoreWebView2WebMessageReceivedEventArgs e)
    {
        try
        {
            var message = JsonSerializer.Deserialize<WebMessage>(e.WebMessageAsJson);
            if (message is null)
            {
                return;
            }
            switch (message.Type)
            {
                case "get-config":
                    SendConfig();
                    break;
                case "set-backend" when !string.IsNullOrWhiteSpace(message.Url):
                    // 允许空路径的 base url；去除尾部斜杠统一格式
                    config.BackendUrl = message.Url.TrimEnd('/');
                    config.Save();
                    SendConfig();
                    onBackendChanged();
                    break;
            }
        }
        catch
        {
            // 非 JSON 消息忽略
        }
    }

    private void SendConfig() =>
        core.PostWebMessageAsJson(
            JsonSerializer.Serialize(new { type = "config", backendUrl = config.BackendUrl }));

    private sealed record WebMessage(string? Type, string? Url);
}
