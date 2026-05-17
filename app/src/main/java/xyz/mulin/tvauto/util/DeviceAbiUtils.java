package xyz.mulin.tvauto.util;

import android.os.Build;

import java.util.ArrayList;
import java.util.List;

public final class DeviceAbiUtils {
    private DeviceAbiUtils() {
    }

    public static String[] getSupportedAbis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Build.SUPPORTED_ABIS;
        }

        List<String> abis = new ArrayList<>(2);
        addIfUsable(abis, Build.CPU_ABI);
        addIfUsable(abis, Build.CPU_ABI2);
        return abis.toArray(new String[0]);
    }

    private static void addIfUsable(List<String> abis, String abi) {
        if (abi == null || abi.isEmpty() || "unknown".equalsIgnoreCase(abi) || abis.contains(abi)) {
            return;
        }
        abis.add(abi);
    }
}
