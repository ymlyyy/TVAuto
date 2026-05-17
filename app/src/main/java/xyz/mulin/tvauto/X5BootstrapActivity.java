package xyz.mulin.tvauto;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.WebView;

import xyz.mulin.tvauto.player.OfflineX5CoreInstaller;

public class X5BootstrapActivity extends AppCompatActivity {
    private static final String TAG = "X5Bootstrap";
    private static final String PREFS = "TVAuto_X5_Bootstrap";
    private static final String KEY_CONFIGURED = "configured";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView titleText;
    private TextView statusText;
    private TextView detailText;
    private WebView probeWebView;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_CONFIGURED, false)) {
            enterPlayer(true);
            return;
        }

        setContentView(createContentView());
        probeWebView = new WebView(this);
        probeWebView.setVisibility(WebView.INVISIBLE);
        ((FrameLayout) findViewById(android.R.id.content)).addView(
                probeWebView,
                new FrameLayout.LayoutParams(1, 1)
        );
        handler.postDelayed(this::continueBootstrap, 450);
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
        root.setBackgroundColor(Color.parseColor("#0E1014"));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.START);
        panel.setPadding(dp(34), dp(32), dp(34), dp(32));

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.parseColor("#1E222A"));
        background.setCornerRadius(dp(24));
        panel.setBackground(background);

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(
                dp(620),
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
        );
        root.addView(panel, panelLp);

        titleText = new TextView(this);
        titleText.setText("正在准备 X5 内核");
        titleText.setTextColor(Color.WHITE);
        titleText.setTextSize(26);
        titleText.setPadding(0, 0, 0, dp(18));
        panel.addView(titleText);

        statusText = new TextView(this);
        statusText.setTextColor(Color.parseColor("#E7EBF1"));
        statusText.setTextSize(18);
        statusText.setLineSpacing(dp(4), 1f);
        panel.addView(statusText);

        detailText = new TextView(this);
        detailText.setTextColor(Color.parseColor("#AEB7C4"));
        detailText.setTextSize(15);
        detailText.setLineSpacing(dp(3), 1f);
        detailText.setPadding(0, dp(18), 0, 0);
        panel.addView(detailText);

        return root;
    }

    private void continueBootstrap() {
        boolean usingX5Core = probeWebView != null && probeWebView.getIsX5Core();
        int coreVersion = QbSdk.getTbsVersion(this);
        int sdkVersion = QbSdk.getTbsSdkVersion();
        String help = QbSdk.getX5CoreLoadHelp(this);

        updateDetail(sdkVersion, coreVersion, help);

        if (usingX5Core) {
            prefs.edit().putBoolean(KEY_CONFIGURED, true).apply();
            titleText.setText("X5 内核已就绪");
            statusText.setText("当前内核：X5\n正在进入 TV Auto…");
            handler.postDelayed(() -> enterPlayer(false), 650);
            return;
        }

        if (coreVersion > 0) {
            statusText.setText("离线内核已安装\n正在进入 TV Auto 激活 X5");
            handler.postDelayed(() -> enterPlayer(false), 650);
            return;
        }

        statusText.setText("正在安装离线 X5 内核\n完成后会自动进入 TV Auto");
        initOfflineX5Core();
        handler.postDelayed(this::continueBootstrap, 2500);
    }

    private void initOfflineX5Core() {
        OfflineX5CoreInstaller.init(this, new OfflineX5CoreInstaller.Listener() {
            @Override
            public void onCoreInitFinished() {
                Log.i(TAG, "offline core init finished");
            }

            @Override
            public void onViewInitFinished(boolean isX5Core) {
                Log.i(TAG, "offline view init finished, usingX5Core=" + isX5Core);
                runOnUiThread(() -> {
                    if (isX5Core) {
                        continueBootstrap();
                    }
                });
            }

            @Override
            public void onInstallFinish(int stateCode) {
                Log.i(TAG, "offline install finish code=" + stateCode);
                runOnUiThread(() -> {
                    if (stateCode == 200) {
                        statusText.setText("离线内核安装成功\n正在进入 TV Auto 激活 X5");
                        handler.postDelayed(() -> enterPlayer(false), 650);
                    } else {
                        statusText.setText("离线内核安装完成，状态码：" + stateCode + "\n正在重新检测…");
                        handler.postDelayed(X5BootstrapActivity.this::continueBootstrap, 800);
                    }
                });
            }

            @Override
            public void onUnsupportedAbi(String supportedAbis) {
                runOnUiThread(() -> {
                    titleText.setText("当前设备无法使用离线 X5");
                    statusText.setText("设备 ABI：" + supportedAbis + "\n离线内核仅支持 ARM 设备。");
                });
            }

            @Override
            public void onError(Exception error) {
                Log.e(TAG, "offline install failed", error);
                runOnUiThread(() -> {
                    titleText.setText("X5 初始化失败");
                    statusText.setText("离线内核安装异常：\n" + error.getMessage());
                });
            }
        });
    }

    private void enterPlayer(boolean showReadyOsd) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(MainActivity.EXTRA_SHOW_X5_READY_OSD, showReadyOsd);
        startActivity(intent);
        finish();
    }

    private void updateDetail(int sdkVersion, int coreVersion, String help) {
        detailText.setText(
                "SDK 版本：" + sdkVersion + "\n"
                        + "内核版本：" + coreVersion + "\n"
                        + "设备 ABI：" + TextUtils.join(", ", Build.SUPPORTED_ABIS) + "\n\n"
                        + "诊断：\n" + help
        );
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}
