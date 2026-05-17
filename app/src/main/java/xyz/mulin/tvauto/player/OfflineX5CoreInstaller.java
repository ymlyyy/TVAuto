package xyz.mulin.tvauto.player;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.TbsListener;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import xyz.mulin.tvauto.util.DeviceAbiUtils;

public final class OfflineX5CoreInstaller {
    public interface Listener {
        void onCoreInitFinished();

        void onViewInitFinished(boolean isX5Core);

        void onInstallFinish(int stateCode);

        void onUnsupportedAbi(String supportedAbis);

        void onError(Exception error);
    }

    private static final String TAG = "OfflineX5Installer";
    private static final int LOCAL_CORE_VERSION = 46110;
    private static final String CORE_FILE_NAME = "tbs_core_release.tbs.apk";

    private OfflineX5CoreInstaller() {
    }

    public static void init(Context context, Listener listener) {
        Context appContext = context.getApplicationContext();
        Map<String, Object> settings = new HashMap<>();
        settings.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
        settings.put(TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE, true);
        QbSdk.initTbsSettings(settings);
        QbSdk.setTbsListener(new TbsListener() {
            @Override
            public void onDownloadFinish(int errCode) {
                Log.i(TAG, "offline flow download callback, code=" + errCode);
            }

            @Override
            public void onInstallFinish(int errCode) {
                Log.i(TAG, "offline install finished, code=" + errCode);
                listener.onInstallFinish(errCode);
            }

            @Override
            public void onDownloadProgress(int progress) {
                Log.d(TAG, "offline flow download progress=" + progress);
            }
        });

        QbSdk.initX5Environment(appContext, new QbSdk.PreInitCallback() {
            @Override
            public void onCoreInitFinished() {
                listener.onCoreInitFinished();
            }

            @Override
            public void onViewInitFinished(boolean isX5Core) {
                listener.onViewInitFinished(isX5Core);
                if (!isX5Core && QbSdk.getTbsVersion(appContext) <= 0) {
                    installBundledCore(appContext, listener);
                }
            }
        });
    }

    private static void installBundledCore(Context context, Listener listener) {
        String abiDir = resolveAbiDir();
        if (abiDir == null) {
            listener.onUnsupportedAbi(TextUtils.join(", ", DeviceAbiUtils.getSupportedAbis()));
            return;
        }

        try {
            String assetPath = "x5core/" + abiDir + "/" + CORE_FILE_NAME;
            File outputDir = new File(context.getCacheDir(), "x5core");
            if (!outputDir.exists() && !outputDir.mkdirs()) {
                throw new IllegalStateException("Unable to create X5 cache dir");
            }
            File outputFile = new File(outputDir, abiDir + "_" + CORE_FILE_NAME);
            copyAsset(context, assetPath, outputFile);
            Log.i(TAG, "installing bundled core from " + assetPath);
            QbSdk.installLocalTbsCore(context, LOCAL_CORE_VERSION, outputFile.getAbsolutePath());
        } catch (Exception e) {
            listener.onError(e);
        }
    }

    private static String resolveAbiDir() {
        for (String abi : DeviceAbiUtils.getSupportedAbis()) {
            if ("arm64-v8a".equals(abi)) return "arm64_v8a";
            if ("armeabi-v7a".equals(abi) || "armeabi".equals(abi)) return "armeabi_v7a";
        }
        return null;
    }

    private static void copyAsset(Context context, String assetPath, File outputFile) throws Exception {
        try (InputStream inputStream = context.getAssets().open(assetPath);
             FileOutputStream outputStream = new FileOutputStream(outputFile, false)) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
        }
    }
}
