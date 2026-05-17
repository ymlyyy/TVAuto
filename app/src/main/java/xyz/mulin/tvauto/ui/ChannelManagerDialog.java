package xyz.mulin.tvauto.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import xyz.mulin.tvauto.R;
import xyz.mulin.tvauto.util.QrCodeGenerator;

public final class ChannelManagerDialog {
    public interface Listener {
        void onAddChannel(String name, String url);

        void onDeleteCurrentChannel();

        void onCheckUpdates();

        void onOpenX5Manager();

        void onOpenRawWebPage();
    }

    private ChannelManagerDialog() {
    }

    public static AlertDialog show(
            Context context,
            String remoteUrl,
            Listener listener
    ) {
        AlertDialog.Builder builder =
                new AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.TOP);
        content.setPadding(dp(context, 24), dp(context, 24), dp(context, 24), dp(context, 24));

        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(dp(context, 20));
        background.setColor(Color.parseColor("#B3000000"));
        content.setBackground(background);

        if (remoteUrl != null) {
            content.addView(
                    createRemotePanel(context, remoteUrl),
                    new LinearLayout.LayoutParams(dp(context, 250), LinearLayout.LayoutParams.WRAP_CONTENT)
            );

            View verticalDivider = new View(context);
            LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                    dp(context, 1),
                    LinearLayout.LayoutParams.MATCH_PARENT
            );
            dividerLp.setMargins(dp(context, 20), 0, dp(context, 22), 0);
            verticalDivider.setLayoutParams(dividerLp);
            verticalDivider.setBackgroundColor(Color.parseColor("#444444"));
            content.addView(verticalDivider);
        }

        ScrollView managementScrollView = new ScrollView(context);
        managementScrollView.addView(createManagementPanel(context, listener));
        content.addView(
                managementScrollView,
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        );

        builder.setView(content);
        AlertDialog dialog = builder.create();
        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
            lp.dimAmount = 0.3f;
            dialog.getWindow().setAttributes(lp);

            float widthRatio = remoteUrl != null ? 0.82f : 0.68f;
            int width = (int) (context.getResources().getDisplayMetrics().widthPixels * widthRatio);
            dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }

        return dialog;
    }

    private static LinearLayout createRemotePanel(Context context, String remoteUrl) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView hint = new TextView(context);
        hint.setText("手机与电视连接同一 Wi‑Fi 后扫码");
        hint.setTextColor(Color.LTGRAY);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, dp(context, 16));
        panel.addView(hint);

        try {
            ImageView qr = new ImageView(context);
            qr.setImageBitmap(QrCodeGenerator.create(remoteUrl, dp(context, 220)));
            panel.addView(qr, new LinearLayout.LayoutParams(dp(context, 220), dp(context, 220)));
        } catch (Exception e) {
            TextView fallback = new TextView(context);
            fallback.setText("二维码生成失败");
            fallback.setTextColor(Color.LTGRAY);
            fallback.setGravity(Gravity.CENTER);
            panel.addView(fallback);
        }

        TextView url = new TextView(context);
        url.setText(remoteUrl);
        url.setTextColor(Color.parseColor("#8CC8FF"));
        url.setGravity(Gravity.CENTER);
        url.setTextSize(13);
        url.setPadding(0, dp(context, 16), 0, 0);
        panel.addView(url);

        TextView availability = new TextView(context);
        availability.setText("仅当前窗口打开期间可用");
        availability.setTextColor(Color.parseColor("#88FFFFFF"));
        availability.setGravity(Gravity.CENTER);
        availability.setTextSize(12);
        availability.setPadding(0, dp(context, 10), 0, 0);
        panel.addView(availability);

        return panel;
    }

    private static LinearLayout createManagementPanel(Context context, Listener listener) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.START);

        int textColor = Color.WHITE;
        int hintColor = Color.parseColor("#777777");
        int inputBgColor = Color.parseColor("#22FFFFFF");

        EditText name = createCompactEditText(context, "频道名称", textColor, hintColor, inputBgColor);
        EditText url = createCompactEditText(context, "直播 URL", textColor, hintColor, inputBgColor);
        MaterialButton addBtn = createFullWidthButton(context, "添加频道", "#006CE0");
        panel.addView(name);
        panel.addView(url);
        panel.addView(addBtn);

        addSectionDivider(context, panel);

        MaterialButton deleteBtn = createCompactButton(context, "删除当前", "#7A3333");
        MaterialButton updateBtn = createCompactButton(context, "更新检测", "#5C6F82");
        panel.addView(createButtonPairRow(context, deleteBtn, updateBtn));

        MaterialButton rawWebBtn = createCompactButton(context, "原始网页", "#4A5D72");
        MaterialButton x5Btn = createCompactButton(context, "X5 管理", "#445C82");
        panel.addView(createButtonPairRow(context, rawWebBtn, x5Btn));

        TextView info = new TextView(context);
        info.setText(R.string.tvauto_v);
        info.setTextColor(Color.LTGRAY);
        info.setPadding(dp(context, 4), dp(context, 20), 0, 0);
        panel.addView(info);

        addBtn.setOnClickListener(v -> listener.onAddChannel(
                name.getText().toString().trim(),
                url.getText().toString().trim()
        ));
        deleteBtn.setOnClickListener(v -> listener.onDeleteCurrentChannel());
        updateBtn.setOnClickListener(v -> listener.onCheckUpdates());
        rawWebBtn.setOnClickListener(v -> listener.onOpenRawWebPage());
        x5Btn.setOnClickListener(v -> listener.onOpenX5Manager());

        return panel;
    }

    private static void addSectionTitle(Context context, LinearLayout parent, String text) {
        TextView title = new TextView(context);
        title.setText(text);
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setPadding(dp(context, 4), 0, 0, dp(context, 10));
        parent.addView(title);
    }

    private static MaterialButton createCompactButton(Context context, String text, String colorHex) {
        MaterialButton button = new MaterialButton(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setCornerRadius(dp(context, 14));
        button.setGravity(Gravity.CENTER);
        button.setAlpha(0.83f);
        button.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(colorHex)));
        return button;
    }

    private static MaterialButton createFullWidthButton(Context context, String text, String colorHex) {
        MaterialButton button = createCompactButton(context, text, colorHex);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 48)
        );
        lp.setMargins(0, dp(context, 4), 0, 0);
        button.setLayoutParams(lp);
        return button;
    }

    private static EditText createCompactEditText(
            Context context,
            String hint,
            int textColor,
            int hintColor,
            int bgColor
    ) {
        EditText editText = new EditText(context);
        editText.setHint(hint);
        editText.setHintTextColor(hintColor);
        editText.setTextColor(textColor);
        editText.setPadding(dp(context, 20), dp(context, 16), dp(context, 20), dp(context, 16));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(context, 14));
        editText.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 0, 0, dp(context, 10));
        editText.setLayoutParams(lp);
        return editText;
    }

    private static LinearLayout createButtonPairRow(Context context, MaterialButton left, MaterialButton right) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int heightPx = dp(context, 48);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, heightPx, 1f);
        lp.setMargins(dp(context, 4), 0, dp(context, 4), 0);
        left.setLayoutParams(lp);
        right.setLayoutParams(lp);
        row.addView(left);
        row.addView(right);
        return row;
    }

    private static void addSectionDivider(Context context, LinearLayout parent) {
        View divider = new View(context);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(context, 1)
        );
        lp.setMargins(0, dp(context, 18), 0, dp(context, 18));
        divider.setLayoutParams(lp);
        divider.setBackgroundColor(Color.parseColor("#444444"));
        parent.addView(divider);
    }

    private static int dp(Context context, int value) {
        float scale = context.getResources().getDisplayMetrics().density;
        return (int) (value * scale + 0.5f);
    }
}
