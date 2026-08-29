using System.Windows;

namespace Metis.Desktop;

public partial class App : Application
{
    protected override void OnStartup(StartupEventArgs e)
    {
        base.OnStartup(e);
        var window = new Window.MainWindow();
        window.Show();
    }
}
