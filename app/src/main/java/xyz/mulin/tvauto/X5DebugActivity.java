package xyz.mulin.tvauto;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.tencent.smtt.sdk.WebView;

public class X5DebugActivity extends AppCompatActivity {
    private static final String DEBUG_URL = "https://debugtbs.qq.com";
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_x5_debug);
        webView = findViewById(R.id.webView);
        webView.loadUrl(DEBUG_URL);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
