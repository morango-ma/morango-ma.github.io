param([string]$printer, [string]$file)
$src = @"
using System; using System.Runtime.InteropServices;
public class MorangoRaw {
  [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)]
  public class DOCINFO { [MarshalAs(UnmanagedType.LPWStr)] public string pDocName; [MarshalAs(UnmanagedType.LPWStr)] public string pOutputFile; [MarshalAs(UnmanagedType.LPWStr)] public string pDataType; }
  [DllImport("winspool.drv", EntryPoint="OpenPrinterW", SetLastError=true, CharSet=CharSet.Unicode)] public static extern bool OpenPrinter(string src, out IntPtr h, IntPtr pd);
  [DllImport("winspool.drv", SetLastError=true)] public static extern bool ClosePrinter(IntPtr h);
  [DllImport("winspool.drv", EntryPoint="StartDocPrinterW", SetLastError=true, CharSet=CharSet.Unicode)] public static extern bool StartDocPrinter(IntPtr h, int level, [In, MarshalAs(UnmanagedType.LPStruct)] DOCINFO di);
  [DllImport("winspool.drv", SetLastError=true)] public static extern bool EndDocPrinter(IntPtr h);
  [DllImport("winspool.drv", SetLastError=true)] public static extern bool StartPagePrinter(IntPtr h);
  [DllImport("winspool.drv", SetLastError=true)] public static extern bool EndPagePrinter(IntPtr h);
  [DllImport("winspool.drv", SetLastError=true)] public static extern bool WritePrinter(IntPtr h, IntPtr p, int n, out int w);
  public static string Send(string printer, byte[] bytes) {
    IntPtr h; if (!OpenPrinter(printer, out h, IntPtr.Zero)) return "open " + Marshal.GetLastWin32Error();
    DOCINFO di = new DOCINFO(); di.pDocName = "Morango"; di.pDataType = "RAW";
    try {
      if (!StartDocPrinter(h, 1, di)) return "doc " + Marshal.GetLastWin32Error();
      StartPagePrinter(h);
      IntPtr p = Marshal.AllocCoTaskMem(bytes.Length); Marshal.Copy(bytes, 0, p, bytes.Length);
      int w; bool ok = WritePrinter(h, p, bytes.Length, out w); Marshal.FreeCoTaskMem(p);
      EndPagePrinter(h); EndDocPrinter(h);
      return ok ? "ok" : "write " + Marshal.GetLastWin32Error();
    } finally { ClosePrinter(h); }
  }
}
"@
Add-Type -TypeDefinition $src
Write-Output ([MorangoRaw]::Send($printer, [System.IO.File]::ReadAllBytes($file)))
