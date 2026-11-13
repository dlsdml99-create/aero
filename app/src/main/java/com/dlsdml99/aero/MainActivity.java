package com.dlsdml99.aero;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.pdf.PdfRenderer;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;
import androidx.webkit.WebViewAssetLoader;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends Activity {
    private static final int REQ_CAMERA = 1, REQ_FILE = 2, REQ_NOTIFY = 9;
    private static final String HOME = "https://appassets.androidplatform.net/assets/index.html";
    private static final String RELEASE_API = "https://api.github.com/repos/dlsdml99-create/aero/releases/latest";

    private WebView web;
    private WebViewAssetLoader assets;
    private Uri photoUri;
    private boolean pageReady;
    private boolean working;
    private final List<String> pendingJs = new ArrayList<>();

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        if (saved != null) photoUri = saved.getParcelable("photoUri");
        assets = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();
        web = new WebView(this);
        web.setBackgroundColor(isNight() ? 0xFF0B121A : 0xFFE7EBF0);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        // 보안: 앱 화면은 앱 안 파일만 열고, 폰의 다른 파일·콘텐츠는 못 읽게
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        WebView.setWebContentsDebuggingEnabled(false);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        web.addJavascriptInterface(new Bridge(), "TicketNative");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                return assets.shouldInterceptRequest(req.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                pageReady = true;
                for (String c : pendingJs) web.evaluateJavascript(c, null);
                pendingJs.clear();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if ("appassets.androidplatform.net".equals(u.getHost())) return false;
                openOutside(u);
                return true;
            }
        });
        setContentView(web);
        web.loadUrl(HOME);
        checkUpdate(false);
        warmUp();
        takeShared(getIntent());
    }

    private boolean isNight() {
        return (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
            == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // 바깥 앱으로 여는 주소는 https + 정해 둔 곳만.
    // 누구나 페이지를 만들 수 있는 곳(github.io, github.com, google.com의 다른 서비스)은 정확한 주소·경로만 허용
    private static final String[] GOV_HOSTS = {"go.kr", "go.jp", "gov.tw", "gov.vn", "go.th", "gov.ph",
        "gov.sg", "gov.my", "go.id", "dhs.gov", "canada.ca", "gov.uk", "europa.eu", "gov.au", "govt.nz"};
    // 정확한 주소 + 경로 앞부분
    private static final String[][] EXACT = {
        {"www.google.com", "/maps"}, {"www.google.com", "/search"}, {"maps.google.com", "/"},
        {"play.google.com", "/store/apps/details"},
        {"github.com", "/dlsdml99-create/aero/"},
        {"dlsdml99-create.github.io", "/ieum/"}, {"dlsdml99-create.github.io", "/aero/"}};
    static boolean safeUrl(Uri u) {
        if (u == null || !"https".equals(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null) return false;
        if (u.getPort() != -1 && u.getPort() != 443) return false;
        String h = u.getHost().toLowerCase(java.util.Locale.ROOT), path = u.getPath() == null ? "/" : u.getPath();
        if (path.contains("..")) return false;
        for (String[] e : EXACT) {
            String pre = e[1];
            if (!h.equals(e[0])) continue;
            if (pre.endsWith("/") ? path.startsWith(pre) : (path.equals(pre) || path.startsWith(pre + "/"))) return true;
        }
        for (String d : GOV_HOSTS) if (h.equals(d) || h.endsWith("." + d)) return true;
        return false;
    }

    private void openOutside(Uri u) {
        if (!safeUrl(u)) { toast("열 수 없는 주소예요"); return; }
        try { startActivity(new Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (Exception e) { toast("열 수 있는 앱이 없어요"); }
    }

    // 다른 앱에서 "공유 → Aero"로 보낸 항공권 사진·PDF를 받음
    private void takeShared(Intent i) {
        if (i == null || i.getAction() == null) return;
        Uri u = null;
        if (Intent.ACTION_SEND.equals(i.getAction())) u = i.getParcelableExtra(Intent.EXTRA_STREAM);
        else if (Intent.ACTION_VIEW.equals(i.getAction())) u = i.getData();
        if (u == null) return;
        // 받은 파일은 다른 앱이 건넨 content:// 주소만 (file:// 등은 거부)
        if (!"content".equals(u.getScheme())) { i.setAction(Intent.ACTION_MAIN); toast("이 파일은 열 수 없어요"); return; }
        String type = i.getType();
        if (type == null) type = getContentResolver().getType(u);
        i.setAction(Intent.ACTION_MAIN);   // 화면을 돌려도 다시 처리하지 않게
        process(u, type);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        takeShared(intent);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.onBackKey?String(window.onBackKey()):'false'", v -> {
            if (v == null || !v.contains("true")) finish();
        });
    }

    // ── 앱 화면에서 부르는 기능 ──
    class Bridge {
        /** 백업 파일 저장: 사용자가 '파일' 창에서 고른 곳에만 써요 (권한 필요 없음, 인터넷으로 보내지 않음) */
        @JavascriptInterface
        public void backupSave(String name, String json) {
            runOnUiThread(() -> {
                bkData = json;
                try {
                    startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("application/json").putExtra(Intent.EXTRA_TITLE, name), REQ_BK_SAVE);
                } catch (Exception e) { bkData = null; js("window.AppInfo&&AppInfo.backupDone(false)"); }
            });
        }

        /** 백업 파일 불러오기: 사용자가 고른 파일 하나만 읽어요 */
        @JavascriptInterface
        public void backupOpen() {
            runOnUiThread(() -> {
                try {
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                            .setType("*/*").putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain", "application/octet-stream"}), REQ_BK_OPEN);
                } catch (Exception e) { js("window.AppInfo&&AppInfo.backupOpened(null)"); }
            });
        }

        @JavascriptInterface
        public void pick(String kind) {
            runOnUiThread(() -> { if ("camera".equals(kind)) openCamera(); else openFiles(); });
        }

        @JavascriptInterface
        public void scheduleAlerts(String json) {
            Alerts.save(MainActivity.this, json);
            Alerts.schedule(MainActivity.this);
            try { if (new JSONArray(json).length() > 0) runOnUiThread(MainActivity.this::askNotify); } catch (Exception ignored) {}
        }

        @JavascriptInterface
        public void updateWidget(String json) {
            getSharedPreferences(Alerts.PREF, MODE_PRIVATE).edit().putString("widget", json).apply();
            AeroWidget.updateAll(MainActivity.this);
        }

        @JavascriptInterface
        public void setBrightness(double v) {
            runOnUiThread(() -> {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.screenBrightness = v < 0 ? WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE : (float) Math.min(1, v);
                getWindow().setAttributes(lp);
                if (v < 0) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                else getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            });
        }

        @JavascriptInterface
        public void share(String text) {
            runOnUiThread(() -> {
                try {
                    Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, "도착 시각 보내기"));
                } catch (Exception e) { toast("보낼 앱을 찾지 못했어요"); }
            });
        }

        @JavascriptInterface
        public void setAlarm(double atMs, String label) {
            runOnUiThread(() -> {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis((long) atMs);
                try {
                    Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                        .putExtra(AlarmClock.EXTRA_HOUR, c.get(Calendar.HOUR_OF_DAY))
                        .putExtra(AlarmClock.EXTRA_MINUTES, c.get(Calendar.MINUTE))
                        .putExtra(AlarmClock.EXTRA_MESSAGE, label == null ? "공항 출발" : label)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                    startActivity(i);
                } catch (Exception e) { toast("시계 앱을 열지 못했어요"); }
            });
        }

        @JavascriptInterface
        public void openApp(String pkg, String fallback) {
            runOnUiThread(() -> {
                Intent i = pkg == null ? null : getPackageManager().getLaunchIntentForPackage(pkg);
                if (i != null) { try { startActivity(i); return; } catch (Exception ignored) {} }
                if (fallback != null && fallback.startsWith("https://")) openOutside(Uri.parse(fallback));
            });
        }

        // 이음(번역기)을 다음 여행 나라 말로 열기. 언어·나라 코드는 형식을 확인한 것만 보냄
        @JavascriptInterface
        public void openIeum(String lang, String cc) {
            if (lang == null || cc == null || !lang.matches("[a-z]{2,3}") || !cc.matches("[A-Z]{2}")) return;
            runOnUiThread(() -> {
                Intent i = getPackageManager().getLaunchIntentForPackage("com.ieum.app");
                if (i != null) {
                    i.setPackage("com.ieum.app");
                    i.putExtra("trip_lang", lang);
                    i.putExtra("trip_cc", cc);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try { startActivity(i); return; } catch (Exception ignored) {}
                }
                openOutside(Uri.parse("https://dlsdml99-create.github.io/ieum/"));
            });
        }

        // 인천공항 운항 정보만 받아옴 (다른 주소는 막음)
        @JavascriptInterface
        public void httpGet(String id, String url) {
            new Thread(() -> {
                int code = 0; String body = "";
                try {
                    Uri u = Uri.parse(url);
                    if (!"https".equals(u.getScheme()) || !"apis.data.go.kr".equals(u.getHost())) throw new Exception("blocked");
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(10000);
                    code = c.getResponseCode();
                    body = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream(), 2_000_000);
                } catch (Exception e) { code = 0; }
                js("window.onNativeHttp&&window.onNativeHttp(" + JSONObject.quote(id) + "," + code + "," + JSONObject.quote(body) + ")");
            }).start();
        }

        @JavascriptInterface
        public void checkUpdate() { MainActivity.this.checkUpdate(true); }
    }

    private void askNotify() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            android.content.SharedPreferences p = getSharedPreferences(Alerts.PREF, MODE_PRIVATE);
            if (p.getBoolean("askedNotify", false)) return;
            p.edit().putBoolean("askedNotify", true).apply();
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIFY);
        }
    }

    // ── 항공권 가져오기 ──
    private void openCamera() {
        try {
            File dir = new File(getCacheDir(), "shots");
            dir.mkdirs();
            photoUri = FileProvider.getUriForFile(this, getPackageName() + ".files", new File(dir, "ticket.jpg"));
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA);
        } catch (Exception e) {
            toast("카메라를 열 수 없어요. 캡처·PDF로 골라 주세요.");
        }
    }

    private void openFiles() {
        try {
            Intent i = new Intent(Intent.ACTION_GET_CONTENT);
            i.setType("*/*");
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
            startActivityForResult(Intent.createChooser(i, "항공권 캡처나 PDF 고르기"), REQ_FILE);
        } catch (Exception e) {
            toast("파일을 열 수 없어요.");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (backupResult(req, res, data)) return;
        if (req != REQ_CAMERA && req != REQ_FILE) return;
        if (res != RESULT_OK) return;
        Uri u = req == REQ_CAMERA ? photoUri : (data != null ? data.getData() : null);
        if (u == null) { toast("사진을 가져오지 못했어요."); return; }
        process(u, req == REQ_CAMERA ? "image/jpeg" : getContentResolver().getType(u));
    }

    // 사진·PDF → 바코드와 글자를 폰 안에서 읽음 (밖으로 보내지 않음)
    private void process(Uri u, String type) {
        if (working) { toast("앞의 항공권을 읽는 중이에요"); return; }
        working = true;
        js("window.onNativeScanStart&&window.onNativeScanStart()");
        step("prep", "active", null);
        new Thread(() -> {
            JSONObject r = new JSONObject();
            try {
                List<Bitmap> pages = new ArrayList<>();
                boolean pdf = (type != null && type.contains("pdf")) || String.valueOf(u.getPath()).toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
                try {
                    if (pdf) pages.addAll(renderPdf(u)); else pages.add(loadUpright(u));
                } catch (Exception e) {
                    r.put("ok", false).put("error", pdf ? "PDF를 열지 못했어요. 항공권 화면을 캡처해서 올려 주세요." : "사진을 열지 못했어요.");
                    done(r);
                    return;
                }
                if (pages.isEmpty()) { r.put("ok", false).put("error", "열 수 있는 쪽이 없어요."); done(r); return; }
                step("prep", "done", null);

                step("code", "active", null);
                JSONArray codes = new JSONArray();
                BarcodeScanner sc = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_PDF417, Barcode.FORMAT_AZTEC, Barcode.FORMAT_QR_CODE, Barcode.FORMAT_DATA_MATRIX).build());
                String codeError = null;
                for (Bitmap b : pages) {
                    try {
                        for (Barcode bc : Tasks.await(sc.process(InputImage.fromBitmap(b, 0)), 30, TimeUnit.SECONDS)) {
                            String raw = bc.getRawValue();
                            if ((raw == null || raw.isEmpty()) && bc.getRawBytes() != null) raw = new String(bc.getRawBytes(), StandardCharsets.ISO_8859_1);
                            if (raw == null || raw.isEmpty()) continue;
                            codes.put(new JSONObject().put("raw", raw).put("fmt", fmtName(bc.getFormat())));
                        }
                    } catch (Exception e) { codeError = downloading(e) ? "바코드 인식 기능을 처음 한 번 내려받는 중이에요" : null; }
                }
                sc.close();

                step("text", "active", null);
                JSONArray lines = new JSONArray();
                String textError = null;
                for (int k = 0; k < Math.min(2, pages.size()); k++) {
                    try {
                        Text t = Tasks.await(TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build())
                            .process(InputImage.fromBitmap(pages.get(k), 0)), 40, TimeUnit.SECONDS);
                        for (String row : rows(t)) lines.put(row);
                    } catch (Exception e) {
                        textError = downloading(e) ? "글자 인식 기능을 처음 한 번 내려받는 중이에요. 1~2분 뒤 다시 해 주세요." : "글자를 읽지 못했어요";
                    }
                }
                r.put("ok", true).put("barcodes", codes).put("lines", lines).put("image", jpeg(pages.get(0), 1600, 82));
                if (textError != null) r.put("textError", textError);
                if (codeError != null && codes.length() == 0) r.put("codeError", codeError);
                done(r);
            } catch (Exception e) {
                try { r.put("ok", false).put("error", "항공권을 읽지 못했어요."); } catch (Exception ignored) {}
                done(r);
            }
        }).start();
    }

    private void done(JSONObject r) {
        working = false;
        js("window.onNativeTicket&&window.onNativeTicket(" + r + ")");
    }

    private void step(String id, String st, String sub) {
        js("window.onNativeStep&&window.onNativeStep(" + JSONObject.quote(id) + "," + JSONObject.quote(st) + "," + (sub == null ? "null" : JSONObject.quote(sub)) + ")");
    }

    private static String fmtName(int f) {
        switch (f) {
            case Barcode.FORMAT_PDF417: return "pdf417";
            case Barcode.FORMAT_AZTEC: return "aztec";
            case Barcode.FORMAT_QR_CODE: return "qr_code";
            case Barcode.FORMAT_DATA_MATRIX: return "data_matrix";
            default: return "other";
        }
    }

    private List<Bitmap> renderPdf(Uri u) throws Exception {
        File f = new File(getCacheDir(), "ticket.pdf");
        try (InputStream in = getContentResolver().openInputStream(u); OutputStream out = new FileOutputStream(f)) {
            if (in == null) throw new Exception("open");
            byte[] buf = new byte[16384]; int n; long total = 0;
            while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); total += n; if (total > 40_000_000L) throw new Exception("big"); }
        }
        List<Bitmap> out = new ArrayList<>();
        try (ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer pr = new PdfRenderer(pfd)) {
            for (int i = 0; i < Math.min(2, pr.getPageCount()); i++) {
                try (PdfRenderer.Page p = pr.openPage(i)) {
                    float k = 1600f / Math.max(1, p.getWidth());
                    int w = Math.round(p.getWidth() * k), h = Math.round(p.getHeight() * k);
                    if ((long) w * h > 12_000_000L) { float s = (float) Math.sqrt(12_000_000.0 / ((long) w * h)); w = Math.round(w * s); h = Math.round(h * s); }
                    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    new Canvas(b).drawColor(Color.WHITE);
                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    out.add(b);
                }
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        return out;
    }

    // 큰 사진은 줄이고, 휴대폰 방향대로 똑바로 세운다
    private Bitmap loadUpright(Uri u) throws Exception {
        BitmapFactory.Options b = new BitmapFactory.Options();
        b.inJustDecodeBounds = true;
        try (InputStream in = getContentResolver().openInputStream(u)) { BitmapFactory.decodeStream(in, null, b); }
        int sample = 1;
        while (Math.max(b.outWidth, b.outHeight) / (sample * 2) >= 2000) sample *= 2;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap bmp;
        try (InputStream in = getContentResolver().openInputStream(u)) { bmp = BitmapFactory.decodeStream(in, null, o); }
        if (bmp == null) throw new Exception("decode");
        int rot = 0;
        try (InputStream in = getContentResolver().openInputStream(u)) {
            int ori = new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (ori == ExifInterface.ORIENTATION_ROTATE_90) rot = 90;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_180) rot = 180;
            else if (ori == ExifInterface.ORIENTATION_ROTATE_270) rot = 270;
        } catch (Exception ignored) {}
        if (rot != 0) {
            Matrix m = new Matrix();
            m.postRotate(rot);
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
        }
        return bmp;
    }

    private static String jpeg(Bitmap src, int max, int q) {
        int w = src.getWidth(), h = src.getHeight();
        float k = Math.min(1f, max / (float) Math.max(w, h));
        Bitmap b = k < 1f ? Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * k)), Math.max(1, Math.round(h * k)), true) : src;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, q, out);
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP);
    }

    // 같은 높이에 있는 글자 줄을 한 줄로 합친다 (표의 칸이 따로 읽혀도 한 줄로)
    private static class Ln {
        final String text; final int left, top, bottom;
        Ln(String t, Rect r) { text = t; left = r.left; top = r.top; bottom = r.bottom; }
        float cy() { return (top + bottom) / 2f; }
        int h() { return Math.max(1, bottom - top); }
    }

    private static List<String> rows(Text text) {
        List<Ln> ls = new ArrayList<>();
        for (Text.TextBlock b : text.getTextBlocks()) {
            for (Text.Line l : b.getLines()) {
                Rect r = l.getBoundingBox();
                ls.add(new Ln(l.getText(), r == null ? new Rect(0, ls.size() * 20, 0, ls.size() * 20 + 10) : r));
            }
        }
        Collections.sort(ls, (a, b) -> Float.compare(a.cy(), b.cy()));
        List<List<Ln>> groups = new ArrayList<>();
        for (Ln l : ls) {
            List<Ln> g = groups.isEmpty() ? null : groups.get(groups.size() - 1);
            if (g != null) {
                Ln f = g.get(0);
                if (Math.abs(f.cy() - l.cy()) <= Math.min(f.h(), l.h()) * 0.55f) { g.add(l); continue; }
            }
            List<Ln> n = new ArrayList<>();
            n.add(l);
            groups.add(n);
        }
        List<String> out = new ArrayList<>();
        for (List<Ln> g : groups) {
            Collections.sort(g, (a, b) -> Integer.compare(a.left, b.left));
            StringBuilder sb = new StringBuilder();
            for (Ln l : g) { if (sb.length() > 0) sb.append("  "); sb.append(l.text); }
            out.add(sb.toString());
        }
        return out;
    }

    // 글자·바코드 인식 기능을 미리 내려받아 두기 (처음 한 번, 뒤에서 조용히)
    private void warmUp() {
        new Thread(() -> {
            try {
                InputImage img = InputImage.fromBitmap(Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888), 0);
                TextRecognition.getClient(new KoreanTextRecognizerOptions.Builder().build()).process(img);
                BarcodeScanning.getClient().process(img);
            } catch (Throwable ignored) {}
        }).start();
    }

    private static boolean downloading(Exception e) {
        String m = String.valueOf(e == null ? "" : e.getMessage()).toLowerCase(java.util.Locale.ROOT);
        return m.contains("download") || m.contains("waiting") || m.contains("unavailable");
    }

    // ── 새 버전 확인 (GitHub 최신 릴리스와 비교) ──
    private void checkUpdate(boolean manual) {
        new Thread(() -> {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                if (c.getResponseCode() != 200) { if (manual) toast("새 버전을 확인하지 못했어요"); return; }
                JSONObject o = new JSONObject(readAll(c.getInputStream(), 500_000));
                String tag = o.optString("tag_name", "");
                String url = null;
                JSONArray as = o.optJSONArray("assets");
                if (as != null) for (int i = 0; i < as.length(); i++) {
                    JSONObject a = as.getJSONObject(i);
                    String du = a.optString("browser_download_url"); if (a.optString("name").endsWith(".apk") && du.startsWith("https://github.com/dlsdml99-create/aero/releases/download/")) { url = du; break; }
                }
                String mine = currentVersion();
                if (tag.matches("v?[0-9.]+")) js("window.AppInfo&&window.AppInfo.native('" + tag + "')");
                if (url == null || !newer(tag, mine)) { if (manual) toast("지금 최신 버전이에요 (v" + mine + ")"); return; }
                final String u = url, notes = o.optString("body", "");
                runOnUiThread(() -> showUpdate(tag, mine, notes, u));
            } catch (Exception e) {
                if (manual) toast("새 버전을 확인하지 못했어요");
            }
        }).start();
    }

    static boolean newer(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\."), y = b.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
            int q = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (p != q) return p > q;
        }
        return false;
    }

    private String currentVersion() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pi.versionName == null ? "0" : pi.versionName;
        } catch (Exception e) { return "999"; }
    }

    private void showUpdate(String tag, String mine, String notes, String url) {
        if (isFinishing()) return;
        String n = notes.replaceAll("[#*`>]", "").replaceAll("\n{3,}", "\n\n").trim();
        if (n.length() > 600) n = n.substring(0, 600) + "…";
        new AlertDialog.Builder(this)
            .setTitle("새 버전이 나왔어요")
            .setMessage("v" + mine + " → " + tag + "\n\n" + n + "\n\n'업데이트'를 누르면 파일이 받아져요. 받은 파일을 눌러 설치하면 끝! 저장한 항공권은 그대로 남아요.")
            .setPositiveButton("업데이트", (d, w) -> openOutside(Uri.parse(url)))
            .setNegativeButton("나중에", null)
            .show();
    }

    // ── 공통 ──
    private static String readAll(InputStream in, int max) throws Exception {
        if (in == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192]; int n;
            while ((n = r.read(buf)) > 0 && sb.length() < max) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private void toast(String m) {
        runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_SHORT).show());
    }

    // ── 내 데이터 백업 (파일 저장·불러오기) ──
    private static final int REQ_BK_SAVE = 7301, REQ_BK_OPEN = 7302;
    private String bkData;

    private boolean backupResult(int req, int res, Intent data) {
        if (req != REQ_BK_SAVE && req != REQ_BK_OPEN) return false;
        final android.net.Uri u = (res == RESULT_OK && data != null) ? data.getData() : null;
        if (req == REQ_BK_SAVE) {
            final String text = bkData; bkData = null;
            if (u == null || text == null) { js("window.AppInfo&&AppInfo.backupDone(false,'cancel')"); return true; }
            new Thread(() -> {
                boolean ok;
                try (java.io.OutputStream o = getContentResolver().openOutputStream(u, "wt")) {
                    o.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); ok = true;
                } catch (Exception e) { ok = false; }
                js("window.AppInfo&&AppInfo.backupDone(" + ok + ")");
            }).start();
            return true;
        }
        if (u == null) { js("window.AppInfo&&AppInfo.backupOpened(null)"); return true; }
        new Thread(() -> {
            String out;
            try (java.io.InputStream in = getContentResolver().openInputStream(u)) {
                java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[65536]; int n;
                while ((n = in.read(buf)) > 0) { b.write(buf, 0, n); if (b.size() > 80 * 1024 * 1024) throw new java.io.IOException("too big"); }
                out = new String(b.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception e) { out = null; }
            js("window.AppInfo&&AppInfo.backupOpened(" + (out == null ? "null" : org.json.JSONObject.quote(out)) + ")");
        }).start();
        return true;
    }

    private void js(String code) {
        web.post(() -> {
            if (pageReady) web.evaluateJavascript(code, null);
            else pendingJs.add(code);
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (photoUri != null) out.putParcelable("photoUri", photoUri);
    }
}
