package ma.morango.app;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends Activity {
    static final String HOME = "https://morango-ma.github.io/";
    static final String HOST = "morango-ma.github.io";
    static final int REQ_FILE = 11, REQ_PERM = 12;
    static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    WebView wv;
    ValueCallback<Uri[]> fileCb;
    Uri camUri;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        wv = new WebView(this);
        setContentView(wv);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setUserAgentString(s.getUserAgentString() + " MorangoApp/2");
        wv.addJavascriptInterface(new Bridge(), "MorangoNative");
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (HOST.equals(u.getHost()) && !u.getPath().endsWith(".apk")) return false;
                try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception e) { }
                return true;
            }
        });
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest r) {
                runOnUiThread(() -> r.grant(r.getResources()));
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                camUri = null;
                String acc = String.join(",", p.getAcceptTypes());
                boolean video = acc.contains("video");
                Intent cam = null;
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        ContentValues cv = new ContentValues();
                        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, "morango_" + System.currentTimeMillis() + (video ? ".mp4" : ".jpg"));
                        cv.put(MediaStore.MediaColumns.MIME_TYPE, video ? "video/mp4" : "image/jpeg");
                        camUri = getContentResolver().insert(video ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                        cam = new Intent(video ? MediaStore.ACTION_VIDEO_CAPTURE : MediaStore.ACTION_IMAGE_CAPTURE);
                        cam.putExtra(MediaStore.EXTRA_OUTPUT, camUri);
                        cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (Exception e) { cam = null; camUri = null; }
                }
                Intent i;
                if (p.isCaptureEnabled() && cam != null) i = cam;
                else {
                    Intent pick = p.createIntent();
                    if (p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    i = Intent.createChooser(pick, "Morango");
                    if (cam != null) i.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                }
                try { startActivityForResult(i, REQ_FILE); } catch (Exception e) { fileCb = null; return false; }
                return true;
            }
        });
        wv.setDownloadListener((url, ua, cd, mime, len) -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) { }
        });
        ArrayList<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.CAMERA);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) need.add("android.permission.BLUETOOTH_CONNECT");
        if (Build.VERSION.SDK_INT < 29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        if (!need.isEmpty()) requestPermissions(need.toArray(new String[0]), REQ_PERM);
        if (b != null) wv.restoreState(b); else wv.loadUrl(HOME);
    }

    @Override
    protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); wv.saveState(o); }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FILE || fileCb == null) return;
        Uri[] out = null;
        if (res == RESULT_OK) {
            if (data != null && data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                out = new Uri[n];
                for (int k = 0; k < n; k++) out[k] = data.getClipData().getItemAt(k).getUri();
            } else if (data != null && data.getData() != null) out = new Uri[]{data.getData()};
            else if (camUri != null) out = new Uri[]{camUri};
        } else if (camUri != null) {
            try { getContentResolver().delete(camUri, null, null); } catch (Exception e) { }
        }
        fileCb.onReceiveValue(out);
        fileCb = null;
    }

    @Override
    public void onBackPressed() {
        wv.evaluateJavascript("(window.__nativeBack?window.__nativeBack():false)", v -> {
            if (!"true".equals(v)) moveTaskToBack(true);
        });
    }

    @Override
    public void onWindowFocusChanged(boolean f) {
        super.onWindowFocusChanged(f);
        if (f) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    /* printers: classic Bluetooth (SPP) and USB host, raw ESC/POS bytes */
    class Bridge {
        BluetoothSocket bt;
        OutputStream btOut;
        UsbDeviceConnection usbC;
        UsbEndpoint usbEp;
        UsbInterface usbI;
        String cur;

        @JavascriptInterface
        public String version() { return "5"; }

        /* alert: always vibrate; when the phone is not on silent, also play the alarm sound (loud) */
        @JavascriptInterface
        public String alarm() {
            runOnUiThread(() -> {
                try {
                    Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                    long[] pat = {0, 600, 250, 600, 250, 900};
                    if (v != null && v.hasVibrator()) {
                        if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(pat, -1)); else v.vibrate(pat, -1);
                    }
                } catch (Exception e) { }
                try {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    if (am != null && am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                        Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                        final Ringtone r = RingtoneManager.getRingtone(getApplicationContext(), u);
                        if (r != null) {
                            r.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                            r.play();
                            wv.postDelayed(() -> { try { r.stop(); } catch (Exception e) { } }, 3500);
                        }
                    }
                } catch (Exception e) { }
            });
            return "ok";
        }

        @JavascriptInterface
        public String listPrinters() {
            JSONArray a = new JSONArray();
            try {
                BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
                if (ad != null && ad.isEnabled()) for (BluetoothDevice d : ad.getBondedDevices()) {
                    JSONObject o = new JSONObject();
                    o.put("id", "bt:" + d.getAddress());
                    o.put("name", (d.getName() == null ? "Bluetooth" : d.getName()) + " (Bluetooth)");
                    a.put(o);
                }
            } catch (Exception e) { }
            try {
                UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
                for (UsbDevice d : um.getDeviceList().values()) {
                    JSONObject o = new JSONObject();
                    o.put("id", "usb:" + d.getVendorId() + ":" + d.getProductId());
                    String n = d.getProductName();
                    o.put("name", (n == null ? "USB " + d.getVendorId() + ":" + d.getProductId() : n) + " (USB)");
                    a.put(o);
                }
            } catch (Exception e) { }
            return a.toString();
        }

        synchronized void close() {
            try { if (btOut != null) btOut.close(); } catch (Exception e) { }
            try { if (bt != null) bt.close(); } catch (Exception e) { }
            try { if (usbC != null) { if (usbI != null) usbC.releaseInterface(usbI); usbC.close(); } } catch (Exception e) { }
            bt = null; btOut = null; usbC = null; usbEp = null; usbI = null; cur = null;
        }

        @JavascriptInterface
        public synchronized String connect(String id) {
            if (id != null && id.equals(cur) && (btOut != null || usbC != null)) return "ok";
            close();
            try {
                if (id.startsWith("bt:")) {
                    BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
                    if (ad == null) return "ما كاينش Bluetooth فهاد الجهاز";
                    if (!ad.isEnabled()) return "شعل Bluetooth";
                    BluetoothDevice d = ad.getRemoteDevice(id.substring(3));
                    ad.cancelDiscovery();
                    try {
                        bt = d.createRfcommSocketToServiceRecord(SPP);
                        bt.connect();
                    } catch (Exception e1) {
                        try { if (bt != null) bt.close(); } catch (Exception e) { }
                        bt = d.createInsecureRfcommSocketToServiceRecord(SPP);
                        bt.connect();
                    }
                    btOut = bt.getOutputStream();
                    cur = id;
                    return "ok";
                }
                if (id.startsWith("usb:")) {
                    String[] p = id.split(":");
                    int vid = Integer.parseInt(p[1]), pid = Integer.parseInt(p[2]);
                    UsbManager um = (UsbManager) getSystemService(Context.USB_SERVICE);
                    UsbDevice dev = null;
                    for (UsbDevice d : um.getDeviceList().values()) if (d.getVendorId() == vid && d.getProductId() == pid) dev = d;
                    if (dev == null) return "الطابعة USB ما مركباش";
                    if (!um.hasPermission(dev)) {
                        PendingIntent pi = PendingIntent.getBroadcast(MainActivity.this, 0, new Intent("ma.morango.USB_PERMISSION").setPackage(getPackageName()), Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                        um.requestPermission(dev, pi);
                        return "perm";
                    }
                    for (int k = 0; k < dev.getInterfaceCount() && usbEp == null; k++) {
                        UsbInterface it = dev.getInterface(k);
                        for (int e = 0; e < it.getEndpointCount(); e++) {
                            UsbEndpoint ep = it.getEndpoint(e);
                            if (ep.getDirection() == UsbConstants.USB_DIR_OUT && ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) { usbEp = ep; usbI = it; break; }
                        }
                    }
                    if (usbEp == null) return "ما لقيناش المخرج ديال الطابعة";
                    usbC = um.openDevice(dev);
                    if (usbC == null) return "ما قدرناش نحلو الطابعة";
                    usbC.claimInterface(usbI, true);
                    cur = id;
                    return "ok";
                }
                return "طابعة ما معروفاش";
            } catch (Exception e) {
                close();
                return "ما تربطاتش: " + e.getMessage();
            }
        }

        synchronized String rawWrite(byte[] b) throws Exception {
            if (btOut != null) { btOut.write(b); btOut.flush(); return "ok"; }
            if (usbC != null) {
                for (int i = 0; i < b.length; i += 16384) {
                    int n = Math.min(16384, b.length - i);
                    byte[] c = new byte[n];
                    System.arraycopy(b, i, c, 0, n);
                    if (usbC.bulkTransfer(usbEp, c, n, 8000) < 0) throw new Exception("USB write failed");
                }
                return "ok";
            }
            return "الطابعة ما مربوطاش";
        }

        @JavascriptInterface
        public String write(String b64) {
            byte[] b = Base64.decode(b64, Base64.DEFAULT);
            String id = cur;
            try { return rawWrite(b); } catch (Exception e) {
                if (id == null) return "الطابعة ما مربوطاش";
                close();
                String r = connect(id);
                if (!"ok".equals(r)) return r;
                try { return rawWrite(b); } catch (Exception e2) { close(); return "الطبع ما مشاش: " + e2.getMessage(); }
            }
        }

        @JavascriptInterface
        public String saveFile(String name, String mime, String b64) {
            try {
                byte[] b = Base64.decode(b64, Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues cv = new ContentValues();
                    cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                    cv.put(MediaStore.MediaColumns.MIME_TYPE, mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                    cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Morango");
                    Uri u = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    try (OutputStream o = getContentResolver().openOutputStream(u)) { o.write(b); }
                    return "ok:Download/Morango/" + name;
                }
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Morango");
                dir.mkdirs();
                File f = new File(dir, name);
                try (FileOutputStream o = new FileOutputStream(f)) { o.write(b); }
                return "ok:" + f.getAbsolutePath();
            } catch (Exception e) {
                return "ما تحفظش: " + e.getMessage();
            }
        }
    }
}
