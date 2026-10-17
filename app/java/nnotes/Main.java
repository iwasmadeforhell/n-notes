package nnotes;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;

import org.json.JSONArray;
import org.json.JSONObject;

public class Main extends Activity {
  static final String REPO = "iwasmadeforhell/n-notes";
  static final String DL_PREFIX = "https://github.com/" + REPO + "/releases/download/";
  static final String ACTION = "nnotes.INSTALL_RESULT";
  WebView w;
  String pApk, pSha, pTag;

  @Override protected void onCreate(Bundle b) {
    super.onCreate(b);
    getWindow().setStatusBarColor(Color.parseColor("#0b0d12"));
    getWindow().setNavigationBarColor(Color.parseColor("#0b0d12"));
    w = new WebView(this);
    w.setBackgroundColor(Color.parseColor("#0b0d12"));
    w.getSettings().setJavaScriptEnabled(true);
    w.getSettings().setDomStorageEnabled(true);
    w.getSettings().setAllowFileAccess(true);
    w.setWebChromeClient(new WebChromeClient());
    w.setWebViewClient(new WebViewClient() {
      @Override public boolean shouldOverrideUrlLoading(WebView v, String u) { return !u.startsWith("file:///android_asset/"); }
      // a dead renderer leaves a blank (or killed) app; rebuild the activity instead
      @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
        try { ((android.view.ViewGroup) v.getParent()).removeView(v); } catch (Exception e) {}
        v.destroy(); recreate(); return true;
      }
    });
    setContentView(w);
    w.addJavascriptInterface(new Bridge(), "Android");
    w.loadUrl("file:///android_asset/index.html");

    IntentFilter f = new IntentFilter(ACTION);
    if (Build.VERSION.SDK_INT >= 33) registerReceiver(result, f, 4); // RECEIVER_NOT_EXPORTED
    else registerReceiver(result, f);
    new Thread(new Runnable() { public void run() { checkUpdate(); } }).start();
  }

  @Override protected void onDestroy() {
    super.onDestroy();
    try { unregisterReceiver(result); } catch (Exception e) {}
  }

  @Override public void onBackPressed() { moveTaskToBack(true); }

  // ---- auto update from GitHub releases ----

  static int[] parse(String v) {
    if (v.startsWith("v")) v = v.substring(1);
    String[] p = v.split("[^0-9]+");
    int[] r = new int[3];
    for (int i = 0; i < 3 && i < p.length; i++) { try { r[i] = Integer.parseInt(p[i]); } catch (Exception e) {} }
    return r;
  }

  static boolean newer(String a, String b) {
    int[] x = parse(a), y = parse(b);
    for (int i = 0; i < 3; i++) { if (x[i] != y[i]) return x[i] > y[i]; }
    return false;
  }

  static String get(String u) throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
    c.setConnectTimeout(8000); c.setReadTimeout(15000);
    c.setRequestProperty("Accept", "application/vnd.github+json");
    InputStream in = c.getInputStream();
    java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
    byte[] buf = new byte[4096]; int n;
    while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
    in.close();
    return o.toString("UTF-8");
  }

  // status: latest | available | error
  JSONObject lookup() {
    JSONObject r = new JSONObject();
    try {
      String cur = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
      r.put("current", cur);
      JSONObject rel = new JSONObject(get("https://api.github.com/repos/" + REPO + "/releases/latest"));
      String tag = rel.getString("tag_name");
      r.put("tag", tag);
      if (!newer(tag, cur)) { r.put("status", "latest"); return r; }
      String apk = null, sha = null;
      JSONArray as = rel.getJSONArray("assets");
      for (int i = 0; i < as.length(); i++) {
        String u = as.getJSONObject(i).getString("browser_download_url");
        if (!u.startsWith(DL_PREFIX)) continue;
        if (u.endsWith(".apk")) apk = u;
        if (u.endsWith(".apk.sha256")) sha = u;
      }
      if (apk == null || sha == null) { r.put("status", "error"); r.put("msg", "Release " + tag + " has no APK/checksum"); return r; }
      pApk = apk; pSha = sha; pTag = tag;
      r.put("status", "available");
    } catch (Exception e) {
      try { r.put("status", "error"); r.put("msg", String.valueOf(e)); } catch (Exception x) {}
    }
    return r;
  }

  void checkUpdate() {
    JSONObject r = lookup();
    if (!"available".equals(r.optString("status"))) return;
    final String fa = pApk, fs = pSha, tag = pTag;
    runOnUiThread(new Runnable() { public void run() {
      new AlertDialog.Builder(Main.this).setTitle("Update available")
        .setMessage("N.Notes " + tag + " is ready. Your data stays.")
        .setNegativeButton("Later", null)
        .setPositiveButton("Update", new android.content.DialogInterface.OnClickListener() {
          public void onClick(android.content.DialogInterface d, int x) {
            toast("Downloading update...");
            new Thread(new Runnable() { public void run() { install(fa, fs); } }).start();
          } }).show();
    } });
  }

  // exposed to the page as window.Android
  class Bridge {
    @JavascriptInterface public String info() {
      try {
        android.content.pm.PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
        JSONObject o = new JSONObject();
        o.put("version", pi.versionName);
        o.put("code", Build.VERSION.SDK_INT >= 28 ? pi.getLongVersionCode() : pi.versionCode);
        o.put("pkg", getPackageName());
        o.put("android", Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        o.put("device", Build.MANUFACTURER + " " + Build.MODEL);
        o.put("repo", REPO);
        o.put("installed", pi.lastUpdateTime);
        return o.toString();
      } catch (Exception e) { return "{}"; }
    }
    @JavascriptInterface public void check() {
      new Thread(new Runnable() { public void run() {
        final JSONObject r = lookup();
        runOnUiThread(new Runnable() { public void run() { w.evaluateJavascript("onUpd(" + r.toString() + ")", null); } });
      } }).start();
    }
    @JavascriptInterface public void install() {
      if (pApk == null) return;
      toast("Downloading update...");
      final String a = pApk, h = pSha;
      new Thread(new Runnable() { public void run() { Main.this.install(a, h); } }).start();
    }
    @JavascriptInterface public void copy(final String t) {
      runOnUiThread(new Runnable() { public void run() {
        ((ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("N.Notes", t));
        toast("Copied");
      } });
    }
  }

  void toast(final String s) {
    runOnUiThread(new Runnable() { public void run() { Toast.makeText(Main.this, s, Toast.LENGTH_LONG).show(); } });
  }

  void install(String apkUrl, String shaUrl) {
    PackageInstaller.Session s = null;
    try {
      String want = get(shaUrl).trim().split("\\s+")[0].toLowerCase();
      PackageInstaller pi = getPackageManager().getPackageInstaller();
      int id = pi.createSession(new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL));
      s = pi.openSession(id);
      HttpURLConnection c = (HttpURLConnection) new URL(apkUrl).openConnection();
      c.setConnectTimeout(8000); c.setReadTimeout(30000);
      InputStream in = c.getInputStream();
      OutputStream out = s.openWrite("N.Notes", 0, -1);
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] buf = new byte[16384]; int n;
      while ((n = in.read(buf)) > 0) { md.update(buf, 0, n); out.write(buf, 0, n); }
      s.fsync(out); out.close(); in.close();
      StringBuilder hex = new StringBuilder();
      for (byte x : md.digest()) hex.append(String.format("%02x", x));
      if (!hex.toString().equals(want)) { s.abandon(); toast("Update failed: checksum mismatch"); return; }
      Intent it = new Intent(ACTION).setPackage(getPackageName());
      PendingIntent pend = PendingIntent.getBroadcast(this, id, it, PendingIntent.FLAG_UPDATE_CURRENT | 0x02000000); // FLAG_MUTABLE
      s.commit(pend.getIntentSender());
    } catch (Exception e) {
      try { if (s != null) s.abandon(); } catch (Exception x) {}
      toast("Update failed: " + e.getMessage());
    }
  }

  final BroadcastReceiver result = new BroadcastReceiver() {
    @Override public void onReceive(Context c, Intent i) {
      int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
      if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
        Intent confirm = (Intent) i.getParcelableExtra(Intent.EXTRA_INTENT);
        if (confirm != null) { confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(confirm); }
      } else if (st != PackageInstaller.STATUS_SUCCESS) {
        toast("Update not installed: " + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
      }
    }
  };
}
