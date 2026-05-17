package xyz.mulin.tvauto.player;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Build;
import android.util.Log;
import android.view.View;

import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import xyz.mulin.tvauto.model.UserScript;

public final class TvWebViewController {
    public interface CurrentUrlProvider {
        String getCurrentUrl();
    }

    public interface UserScriptProvider {
        UserScript findMatchingScript(String url);
    }

    private final WebView webView;
    private final CurrentUrlProvider currentUrlProvider;
    private final UserScriptProvider userScriptProvider;
    private boolean scriptInjectionEnabled = true;
    private boolean rawWebMode = false;

    public TvWebViewController(
            WebView webView,
            CurrentUrlProvider currentUrlProvider,
            UserScriptProvider userScriptProvider
    ) {
        this.webView = webView;
        this.currentUrlProvider = currentUrlProvider;
        this.userScriptProvider = userScriptProvider;
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void setup() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setDisplayZoomControls(false);
        settings.setBuiltInZoomControls(false);
        settings.setSupportZoom(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36");

        applyPlaybackInteractionPolicy();
        disableDefaultFocusHighlight(webView);
        disableDefaultFocusHighlight(webView.getView());
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                if (url.startsWith("https://test.ustc.edu.cn/")) {
                    String js =
                            "(function(){" +
                                    "var style=document.createElement('style');" +
                                    "style.innerHTML=`" +
                                    "body * { visibility:hidden !important; }" +
                                    "#test, #test * { visibility:visible !important; }" +
                                    "#test { position:absolute !important; top:0 !important;left: 0% !important;  }" +
                                    "html, body { overflow:hidden !important; }" +
                                    "`;" +
                                    "document.head.appendChild(style);" +
                                    "})();";

                    view.evaluateJavascript(js, null);
                }
            }



            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                injectPreferredScript(view, url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (rawWebMode) {
//                    applyRawWebPageZoom(view);
                    return;
                }
                if (!scriptInjectionEnabled) {
                    return;
                }
                view.evaluateJavascript(
                        "Boolean(window.__TVAUTO_USER_SCRIPT_INJECTED__ || window.__VIDEO_RESIZE_INJECTED__)",
                        value -> {
                    if ("true".equals(value)) {
                        Log.d("TJS", "onPageStarted 阶段注入成功");
                    } else {
                        Log.d("TJS", "onPageStarted 阶段注入失败，onPageFinished 二次注入");
                        injectPreferredScript(view, url);
                    }
                });
            }
        });
    }

    public void enterRawWebMode() {
        scriptInjectionEnabled = false;
        rawWebMode = true;
        WebSettings settings = webView.getSettings();
        settings.setSupportZoom(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        webView.setOnTouchListener(null);
        webView.setOnKeyListener(null);
        webView.setOnGenericMotionListener(null);
        webView.setFocusable(true);
        webView.setFocusableInTouchMode(true);
        disableDefaultFocusHighlight(webView);
        disableDefaultFocusHighlight(webView.getView());
        webView.requestFocus();
    }

    public void exitRawWebMode() {
        scriptInjectionEnabled = true;
        rawWebMode = false;
        WebSettings settings = webView.getSettings();
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        applyPlaybackInteractionPolicy();
    }

    private void applyPlaybackInteractionPolicy() {
        webView.setOnTouchListener((v, event) -> true);
        webView.setOnKeyListener((v, keyCode, event) -> true);
        webView.setOnGenericMotionListener((v, event) -> true);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
    }

    private void disableDefaultFocusHighlight(View view) {
        if (view != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            view.setDefaultFocusHighlightEnabled(false);
        }
    }

    private void applyRawWebPageZoom(WebView view) {
        String js =
                "(function(){" +
                        "document.documentElement.style.zoom='0.75';" +
                        "if(document.body){document.body.style.zoom='0.75';}" +
                        "})();";
        view.evaluateJavascript(js, null);
    }

    private void injectPreferredScript(WebView view, String pageUrl) {
        if (!scriptInjectionEnabled) {
            return;
        }
        UserScript userScript = userScriptProvider.findMatchingScript(pageUrl);
        if (userScript != null) {
            injectUserScript(view, userScript);
            return;
        }
        injectVideoResizeJs(view);
    }

    private void injectUserScript(WebView view, UserScript userScript) {
        String wrappedScript =
                "(function(){" +
                        "try{" +
                        userScript.getJavascript() +
                        "\n;window.__TVAUTO_USER_SCRIPT_INJECTED__=true;" +
                        "}catch(e){console.error('TVAuto user script error',e);}" +
                        "})();";
        view.evaluateJavascript(wrappedScript, null);
    }

    // ????????????
    private void injectVideoResizeJs(WebView view) {
        String currentUrl = currentUrlProvider.getCurrentUrl();
        if (!currentUrl.startsWith("file:///") && !currentUrl.startsWith("https://test.ustc.edu.cn/")) {
            try {
                view.evaluateJavascript(readAssetText("js/default_video_resize.js"), null);
            } catch (Exception e) {
                Log.e("TJS", "??????????", e);
            }
        }
    }

    private String readAssetText(String assetPath) throws Exception {
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                webView.getContext().getAssets().open(assetPath),
                StandardCharsets.UTF_8
        ))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
