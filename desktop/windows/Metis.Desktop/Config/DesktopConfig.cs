using System.IO;
using System.Text.Json;

namespace Metis.Desktop.Config;

/// <summary>桌面端本地配置（%APPDATA%/Metis/config.json）。</summary>
public sealed class DesktopConfig
{
    private static readonly JsonSerializerOptions JsonOptions = new()
    {
        WriteIndented = true,
    };

    public string BackendUrl { get; set; } = "http://127.0.0.1:8080";

    private static string ConfigPath
    {
        get
        {
            string dir = Path.Combine(
                Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData),
                "Metis");
            return Path.Combine(dir, "config.json");
        }
    }

    public static DesktopConfig Load()
    {
        try
        {
            if (File.Exists(ConfigPath))
            {
                return JsonSerializer.Deserialize<DesktopConfig>(
                    File.ReadAllText(ConfigPath), JsonOptions) ?? new DesktopConfig();
            }
        }
        catch
        {
            // 配置损坏时回退默认值
        }
        return new DesktopConfig();
    }

    public void Save()
    {
        string dir = Path.GetDirectoryName(ConfigPath)!;
        Directory.CreateDirectory(dir);
        File.WriteAllText(ConfigPath, JsonSerializer.Serialize(this, JsonOptions));
    }
}
