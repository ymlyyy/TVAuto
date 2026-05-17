package xyz.mulin.tvauto;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.WebView;

import xyz.mulin.tvauto.player.OfflineX5CoreInstaller;

public class X5ManagerActivity extends AppCompatActivity {
    private static final String TAG = "X5Manager";

    private TextView statusText;
    private TextView progressText;
    private WebView probeWebView;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createContentView());
        initOfflineX5Core();
        refreshStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (probeWebView != null) {
            probeWebView.destroy();
            probeWebView = null;
        }
        super.onDestroy();
    }

    private FrameLayout createContentView() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#101114"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(28), dp(28), dp(28), dp(28));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#202329"));
        background.setCornerRadius(dp(20));
        panel.setBackground(background);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                dp(560),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        root.addView(panel, panelLp);

        TextView title = new TextView(this);
        title.setText("X5 内核管理");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setPadding(0, 0, 0, dp(18));
        panel.addView(title);

        statusText = createBodyText();
        panel.addView(statusText);

        progressText = createBodyText();
        progressText.setPadding(0, dp(12), 0, dp(20));
        panel.addView(progressText);

        MaterialButton installButton = createButton("重新初始化", "#006CE0");
        MaterialButton debugButton = createButton("TBS 调试", "#5C6F82");
        MaterialButton refreshButton = createButton("刷新状态", "#47515D");
        MaterialButton closeButton = createButton("返回", "#333941");

        panel.addView(createButtonRow(installButton, debugButton));
        panel.addView(createButtonRow(refreshButton, closeButton));

        installButton.setOnClickListener(v -> initOfflineX5Core());
        debugButton.setOnClickListener(v -> startActivity(new Intent(this, X5DebugActivity.class)));
        refreshButton.setOnClickListener(v -> refreshStatus());
        closeButton.setOnClickListener(v -> finish());

        probeWebView = new WebView(this);
        probeWebView.setVisibility(WebView.INVISIBLE);
        root.addView(probeWebView, new FrameLayout.LayoutParams(1, 1));
        return root;
    }

    private TextView createBodyText() {
        TextView textView = new TextView(this);
        textView.setTextColor(Color.parseColor("#D7DADF"));
        textView.setTextSize(16);
        textView.setLineSpacing(dp(3), 1f);
        textView.setGravity(Gravity.START);
        textView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return textView;
    }

    private LinearLayout createButtonRow(MaterialButton left, MaterialButton right) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        rowLp.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowLp);

        LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        leftLp.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        rightLp.setMargins(dp(5), 0, 0, 0);
        left.setLayoutParams(leftLp);
        right.setLayoutParams(rightLp);
        row.addView(left);
        row.addView(right);
        return row;
    }

    private MaterialButton createButton(String text, String color) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setCornerRadius(dp(14));
        button.setBackgroundColor(Color.parseColor(color));
        return button;
    }

    private void initOfflineX5Core() {
        progressText.setText("正在初始化离线内核…");
        OfflineX5CoreInstaller.init(this, new OfflineX5CoreInstaller.Listener() {
            @Override
            public void onCoreInitFinished() {
                Log.i(TAG, "offline X5 core init finished");
            }

            @Override
            public void onViewInitFinished(boolean isX5Core) {
                Log.i(TAG, "offline X5 view init finished, usingX5Core=" + isX5Core);
                runOnUiThread(() -> {
                    progressText.setText(isX5Core
                            ? "X5 内核已就绪"
                            : "X5 内核尚未生效；若刚安装完成，请重新进入应用。");
                    refreshStatus();
                });
            }

            @Override
            public void onInstallFinish(int stateCode) {
                Log.i(TAG, "offline X5 install finish code=" + stateCode);
                runOnUiThread(() -> {
                    progressText.setText("离线内核安装完成，状态码：" + stateCode);
                    refreshStatus();
                    if (stateCode == 200) {
                        Toast.makeText(X5ManagerActivity.this, "离线 X5 内核安装成功", Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onUnsupportedAbi(String supportedAbis) {
                runOnUiThread(() -> progressText.setText("当前设备 ABI 不支持离线 X5：" + supportedAbis));
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "offline X5 init failed", error);
                runOnUiThread(() -> progressText.setText("离线内核初始化失败：" + error.getMessage()));
            }
        });
    }

    @SuppressLint("SetTextI18n")
    private void refreshStatus() {
        boolean usingX5Core = probeWebView != null && probeWebView.getIsX5Core();
        int sdkVersion = QbSdk.getTbsSdkVersion();
        int coreVersion = QbSdk.getTbsVersion(this);
        String help = QbSdk.getX5CoreLoadHelp(this);

        statusText.setText(
                "当前内核：" + (usingX5Core ? "X5" : "系统 WebView") + "\n"
                        + "SDK 版本：" + sdkVersion + "\n"
                        + "内核版本：" + coreVersion + "\n\n"
                        + "诊断：\n" + help
        );
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}
