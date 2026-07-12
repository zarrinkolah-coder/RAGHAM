package com.ragham.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public final class MainActivity extends Activity {
    private static final String PREFS = "ragham_android_preferences";
    private static final String SERVER_URL_KEY = "server_url";
    private static final int FILE_CHOOSER_REQUEST = 5101;
    private static final int MENU_REFRESH = 101;
    private static final int MENU_SERVER = 102;
    private static final int MENU_BROWSER = 103;
    private static final int MENU_LOGOUT = 104;

    private WebView webView;
    private ProgressBar progressBar;
    private TextView topTitle;
    private ValueCallback<Uri[]> filePathCallback;
    private String serverUrl = "";
    private Uri trustedServerUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(13, 47, 131));
        buildInterface();
        configureWebView();

        serverUrl = getPreferencesStore().getString(SERVER_URL_KEY, "");
        if (savedInstanceState != null && !serverUrl.isEmpty()) {
            webView.restoreState(savedInstanceState);
        } else if (serverUrl.isEmpty()) {
            showServerDialog(false);
        } else {
            setTrustedServer(serverUrl);
            loadServer();
        }
    }

    private void buildInterface() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(14), dp(8), dp(10), dp(8));
        toolbar.setBackgroundColor(Color.rgb(29, 59, 153));

        topTitle = new TextView(this);
        topTitle.setText("نرم افزار رقم");
        topTitle.setTextColor(Color.WHITE);
        topTitle.setTextSize(17);
        topTitle.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        topTitle.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView menuButton = new TextView(this);
        menuButton.setText("⋮");
        menuButton.setTextColor(Color.WHITE);
        menuButton.setTextSize(30);
        menuButton.setGravity(Gravity.CENTER);
        menuButton.setContentDescription("منوی برنامه");
        menuButton.setLayoutParams(new LinearLayout.LayoutParams(dp(48), dp(48)));
        menuButton.setOnClickListener(this::showToolbarMenu);

        toolbar.addView(topTitle);
        toolbar.addView(menuButton);
        root.addView(toolbar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout webContainer = new FrameLayout(this);
        webContainer.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        webContainer.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        progressParams.gravity = Gravity.TOP;
        webContainer.addView(progressBar, progressParams);

        root.addView(webContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        setContentView(root);
    }

    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUserAgentString(settings.getUserAgentString() + " RaghamAndroid/1.0");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, false);
        }

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidRagham");
        webView.setWebViewClient(new RaghamWebViewClient());
        webView.setWebChromeClient(new RaghamWebChromeClient());
        webView.setDownloadListener(new RaghamDownloadListener());
    }

    private void showToolbarMenu(View anchor) {
        android.widget.PopupMenu popup = new android.widget.PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.add(Menu.NONE, MENU_REFRESH, 1, getString(R.string.refresh));
        menu.add(Menu.NONE, MENU_SERVER, 2, getString(R.string.change_server));
        menu.add(Menu.NONE, MENU_BROWSER, 3, getString(R.string.open_browser));
        menu.add(Menu.NONE, MENU_LOGOUT, 4, getString(R.string.logout));
        popup.setOnMenuItemClickListener(this::handleMenuItem);
        popup.show();
    }

    private boolean handleMenuItem(MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_REFRESH) {
            if (!serverUrl.isEmpty()) webView.reload();
            return true;
        }
        if (id == MENU_SERVER) {
            showServerDialog(true);
            return true;
        }
        if (id == MENU_BROWSER) {
            if (!serverUrl.isEmpty()) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(serverUrl)));
            }
            return true;
        }
        if (id == MENU_LOGOUT) {
            webView.evaluateJavascript(
                    "if(typeof logout==='function'){logout();}else{" +
                            "localStorage.clear();sessionStorage.clear();location.reload();}",
                    null
            );
            return true;
        }
        return false;
    }

    private void showServerDialog(boolean cancellable) {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(serverUrl.isEmpty() ? "http://192.168.1.20:8080" : serverUrl);
        input.setSelection(input.getText().length());
        input.setTextDirection(View.TEXT_DIRECTION_LTR);
        input.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
        input.setHint("https://server.example.com");
        int padding = dp(20);
        FrameLayout holder = new FrameLayout(this);
        holder.setPadding(padding, 0, padding, 0);
        holder.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.server_dialog_title)
                .setMessage(R.string.server_dialog_message)
                .setView(holder)
                .setCancelable(cancellable)
                .setPositiveButton(R.string.save_connect, null);
        if (cancellable) builder.setNegativeButton(R.string.cancel, null);
        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String candidate = normalizeServerUrl(input.getText().toString());
                    if (!isValidServerUrl(candidate)) {
                        input.setError("آدرس سرور معتبر نیست");
                        return;
                    }
                    serverUrl = candidate;
                    setTrustedServer(serverUrl);
                    getPreferencesStore().edit().putString(SERVER_URL_KEY, serverUrl).apply();
                    dialog.dismiss();
                    loadServer();
                }));
        dialog.show();
    }

    private String normalizeServerUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "http://" + value;
        }
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private boolean isValidServerUrl(String value) {
        try {
            URI uri = new URI(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (URISyntaxException error) {
            return false;
        }
    }

    private void setTrustedServer(String value) {
        trustedServerUri = Uri.parse(value);
        String display = trustedServerUri.getHost();
        if (display == null || display.isEmpty()) display = "نرم افزار رقم";
        topTitle.setText("رقم · " + display);
    }

    private void loadServer() {
        // حتی شبکه داخلی بدون اینترنت عمومی باید قابل استفاده باشد؛ خطای واقعی اتصال را WebView گزارش می‌کند.
        progressBar.setVisibility(View.VISIBLE);
        webView.loadUrl(serverUrl);
    }

    private boolean isTrustedUrl(String value) {
        if (trustedServerUri == null || value == null) return false;
        Uri target = Uri.parse(value);
        if (!safeEquals(trustedServerUri.getScheme(), target.getScheme())) return false;
        if (!safeEquals(trustedServerUri.getHost(), target.getHost())) return false;
        return effectivePort(trustedServerUri) == effectivePort(target);
    }

    private int effectivePort(Uri uri) {
        if (uri.getPort() > 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private boolean safeEquals(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }

    private void showConnectionError(String message) {
        progressBar.setVisibility(View.GONE);
        String safeMessage = escapeHtml(message);
        String safeUrl = escapeHtml(serverUrl);
        String html = "<!doctype html><html lang='fa' dir='rtl'><head>" +
                "<meta name='viewport' content='width=device-width,initial-scale=1'>" +
                "<style>body{font-family:sans-serif;background:#f4f6fb;margin:0;display:grid;place-items:center;min-height:100vh;color:#182033}" +
                ".box{width:min(420px,86%);background:white;border:1px solid #e2e7f0;border-radius:24px;padding:24px;text-align:center;box-shadow:0 16px 42px rgba(15,37,90,.12)}" +
                "h2{color:#1d3b99}code{display:block;direction:ltr;background:#f3f6ff;padding:10px;border-radius:12px;margin:14px 0;word-break:break-all}" +
                "button{border:0;border-radius:14px;padding:12px 18px;background:#1d3b99;color:white;font-weight:bold}</style></head><body>" +
                "<div class='box'><h2>اتصال به سرور انجام نشد</h2><p>" + safeMessage + "</p><code>" + safeUrl +
                "</code><button onclick='location.reload()'>تلاش دوباره</button><p>از منوی بالای برنامه می‌توانید آدرس سرور را تغییر دهید.</p></div></body></html>";
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null);
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void injectAndroidHelpers() {
        if (!isTrustedUrl(webView.getUrl())) return;
        String script = "(function(){" +
                "if(window.__raghamAndroidInjected)return;" +
                "window.__raghamAndroidInjected=true;" +
                "document.addEventListener('click',async function(e){" +
                "var a=e.target&&e.target.closest?e.target.closest('a[download]'):null;" +
                "if(!a||!a.href||!a.href.startsWith('blob:'))return;" +
                "e.preventDefault();try{" +
                "var blob=await fetch(a.href).then(function(r){return r.blob()});" +
                "var reader=new FileReader();" +
                "reader.onloadend=function(){AndroidRagham.saveBase64File(String(reader.result||''),blob.type||'application/octet-stream',a.download||'ragham-file');};" +
                "reader.readAsDataURL(blob);" +
                "}catch(err){console.error(err);}" +
                "},true);" +
                "window.print=function(){AndroidRagham.printPage(document.title||'گزارش نرم افزار رقم');};" +
                "})();";
        webView.evaluateJavascript(script, null);
    }

    private void startHttpDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        if (!isTrustedUrl(url)) {
            Toast.makeText(this, "دانلود فقط از سرور برنامه مجاز است", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            String filename = URLUtil.guessFileName(url, contentDisposition, mimeType);
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(filename);
            request.setDescription("دانلود از نرم افزار رقم");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.addRequestHeader("User-Agent", userAgent == null ? "" : userAgent);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Ragham/" + filename);
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager != null) manager.enqueue(request);
            Toast.makeText(this, "دانلود آغاز شد", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            Toast.makeText(this, "دانلود انجام نشد", Toast.LENGTH_LONG).show();
        }
    }

    private void printCurrentPage(String title) {
        PrintManager printManager = (PrintManager) getSystemService(Context.PRINT_SERVICE);
        if (printManager == null) {
            Toast.makeText(this, "سرویس چاپ در دسترس نیست", Toast.LENGTH_LONG).show();
            return;
        }
        String safeTitle = (title == null || title.trim().isEmpty()) ? "گزارش نرم افزار رقم" : title.trim();
        PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter(safeTitle);
        printManager.print(safeTitle, adapter, new PrintAttributes.Builder().build());
    }

    private void saveBase64File(String dataUrl, String mimeType, String requestedName) {
        try {
            int comma = dataUrl.indexOf(',');
            String payload = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
            byte[] bytes = Base64.decode(payload, Base64.DEFAULT);
            String filename = sanitizeFilename(requestedName, mimeType);
            Uri resultUri;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, filename);
                values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
                values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ragham");
                resultUri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (resultUri == null) throw new IllegalStateException("Cannot create download file");
                try (OutputStream output = getContentResolver().openOutputStream(resultUri)) {
                    if (output == null) throw new IllegalStateException("Cannot open download file");
                    output.write(bytes);
                }
            } else {
                File directory = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Ragham");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Cannot create download directory");
                }
                File file = new File(directory, filename);
                try (OutputStream output = new FileOutputStream(file)) {
                    output.write(bytes);
                }
                resultUri = Uri.fromFile(file);
            }

            runOnUiThread(() -> Toast.makeText(
                    MainActivity.this,
                    "فایل در پوشه Downloads/Ragham ذخیره شد",
                    Toast.LENGTH_LONG
            ).show());
        } catch (Exception error) {
            runOnUiThread(() -> Toast.makeText(
                    MainActivity.this,
                    "ذخیره فایل انجام نشد",
                    Toast.LENGTH_LONG
            ).show());
        }
    }

    private String sanitizeFilename(String value, String mimeType) {
        String filename = value == null ? "" : value.trim();
        if (filename.isEmpty()) {
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            filename = "ragham-" + stamp + extensionForMime(mimeType);
        }
        filename = filename.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!filename.contains(".")) filename += extensionForMime(mimeType);
        return filename;
    }

    private String extensionForMime(String mimeType) {
        if ("application/json".equalsIgnoreCase(mimeType)) return ".json";
        if ("application/pdf".equalsIgnoreCase(mimeType)) return ".pdf";
        if (mimeType != null && mimeType.startsWith("image/")) return ".png";
        return ".bin";
    }

    private SharedPreferences getPreferencesStore() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack() && isTrustedUrl(webView.getUrl())) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.removeJavascriptInterface("AndroidRagham");
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || filePathCallback == null) return;
        Uri[] results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
    }

    private final class RaghamWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (isTrustedUrl(url)) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, request.getUrl()));
            } catch (Exception ignored) {
                Toast.makeText(MainActivity.this, "امکان بازکردن این پیوند وجود ندارد", Toast.LENGTH_LONG).show();
            }
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);
            injectAndroidHelpers();
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                String description = error == null ? "خطای ارتباط" : String.valueOf(error.getDescription());
                showConnectionError(description);
            }
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, android.net.http.SslError error) {
            handler.cancel();
            Toast.makeText(MainActivity.this, "گواهی امنیتی سرور معتبر نیست", Toast.LENGTH_LONG).show();
        }
    }

    private final class RaghamWebChromeClient extends WebChromeClient {
        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
            progressBar.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
        }

        @Override
        public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallbackValue,
                FileChooserParams fileChooserParams
        ) {
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = filePathCallbackValue;
            Intent intent;
            try {
                intent = fileChooserParams.createIntent();
            } catch (Exception error) {
                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
            }
            try {
                startActivityForResult(intent, FILE_CHOOSER_REQUEST);
                return true;
            } catch (Exception error) {
                filePathCallback = null;
                Toast.makeText(MainActivity.this, "انتخاب فایل در دسترس نیست", Toast.LENGTH_LONG).show();
                return false;
            }
        }
    }

    private final class RaghamDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(
                String url,
                String userAgent,
                String contentDisposition,
                String mimeType,
                long contentLength
        ) {
            if (url != null && url.startsWith("blob:")) return;
            startHttpDownload(url, userAgent, contentDisposition, mimeType);
        }
    }

    public final class AndroidBridge {
        @JavascriptInterface
        public void saveBase64File(String dataUrl, String mimeType, String filename) {
            if (!isTrustedUrl(webView.getUrl())) return;
            MainActivity.this.saveBase64File(dataUrl, mimeType, filename);
        }

        @JavascriptInterface
        public void printPage(String title) {
            if (!isTrustedUrl(webView.getUrl())) return;
            runOnUiThread(() -> printCurrentPage(title));
        }
    }
}
