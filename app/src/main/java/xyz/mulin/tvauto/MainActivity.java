package xyz.mulin.tvauto;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tencent.smtt.sdk.WebView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import xyz.mulin.tvauto.data.ChannelRepository;
import xyz.mulin.tvauto.data.UserScriptRepository;
import xyz.mulin.tvauto.model.Channel;
import xyz.mulin.tvauto.player.TvWebViewController;
import xyz.mulin.tvauto.remote.RemoteManagementServer;
import xyz.mulin.tvauto.ui.ChannelAdapter;
import xyz.mulin.tvauto.ui.ChannelManagerDialog;
import xyz.mulin.tvauto.util.NetworkUtils;

/**
 * TVAuto 主活动类
 * 功能：电视直播源播放器，支持 Web 源、自定义频道管理、遥控器交互及触摸手势。
 */
public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_SHOW_X5_READY_OSD = "show_x5_ready_osd";

    // =============================================================================================
    // 1. 变量声明区域
    // =============================================================================================

    // --- UI 组件 ---
    private DrawerLayout drawerLayout;      // 侧边栏容器
    private WebView webView;                // 核心播放器容器
    private View touchLayer;                // 覆盖在WebView上的透明触控层（用于手势）
    private RecyclerView rvChannels;        // 侧边栏频道列表
    private LinearLayout btnSettings;       // 侧边栏顶部的“频道管理”按钮
    private TextView tvOsd;                 // 屏幕左上角的 OSD (On-Screen Display) 提示

    // --- 数据与存储 ---
    private SharedPreferences configPrefs;  // 保存配置（如上次播放位置）
    private ChannelRepository channelRepository;
    private UserScriptRepository userScriptRepository;
    private final LinkedHashMap<String, String> channelsMap = new LinkedHashMap<>(); // 内存中的频道数据 (URL -> Name)
    private final List<Channel> channelItems = new ArrayList<>();
    private String[] channels;              // 频道 URL 数组（用于通过索引快速访问）
    private int currentChannelIndex = 0;    // 当前播放的频道索引

    // --- 逻辑工具 ---
    private final Handler handler = new Handler(Looper.getMainLooper());
    private RemoteManagementServer remoteManagementServer;
    private ChannelAdapter adapter;         // 列表适配器
    private GestureDetector gestureDetector;// 手势识别器
    private TvWebViewController playerController;
    private AlertDialog channelManagerDialog;
    private boolean rawWebMode = false;

    // --- 防抖与延迟任务配置 ---
    private int pendingChannelIndex = -1;           // 待切换的频道索引（防抖用）
    private static final long AUTO_CLOSE_DELAY = 5000; // 侧边栏自动关闭时间 (ms)
    private static final long SWITCH_DELAY = 1000;     // 换台防抖延迟 (ms)

    // --- Runnable 任务定义 ---
    // 任务：自动关闭侧边栏
    private final Runnable autoCloseRunnable = () -> {
        if (drawerLayout != null && drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
        }
    };

    // 任务：隐藏 OSD 提示
    private final Runnable hideOsdRunnable = () -> {
        if (tvOsd != null) tvOsd.setVisibility(View.GONE);
    };

    // 任务：【核心防抖】确认切换频道 (延迟加载 WebView)
    private final Runnable confirmChannelSwitchRunnable = () -> {
        if (channels != null && pendingChannelIndex >= 0 && pendingChannelIndex < channels.length) {
            Log.d("ChannelSwitch", "Loading URL for index: " + pendingChannelIndex);
            webView.loadUrl(channels[pendingChannelIndex]);
        }
    };

    // 数字键选台缓存
    private int digitBuffer = -1;      // 缓存输入的数字
    private long lastDigitTime = 0;    // 上次按键时间
    // 任务：确认数字选台
    private final Runnable digitConfirmRunnable = this::confirmDigitInput;


    // =============================================================================================
    // 2. 生命周期 (Lifecycle)
    // =============================================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 强制横屏 & 深色模式
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        enableImmersiveMode(); // 开启沉浸式全屏
        setContentView(R.layout.activity_main);

        // 初始化存储
        configPrefs = getSharedPreferences("TVAuto_Config", MODE_PRIVATE);
        SharedPreferences programPrefs = getSharedPreferences("TVAuto_Program", MODE_PRIVATE);
        channelRepository = new ChannelRepository(programPrefs);
        userScriptRepository = new UserScriptRepository(programPrefs);
        channelRepository.initializeDefaultsIfNeeded(getString(R.string.DefaultChannel));

        // 加载数据与恢复状态
        loadUserChannels();
        currentChannelIndex = configPrefs.getInt("lastChannel", 0);
        // 索引越界保护
        if (channels != null && channels.length > 0) {
            if (currentChannelIndex >= channels.length) currentChannelIndex = 0;
        }

        // 初始化各模块
        initViews();
        Log.i("X5Runtime", "Main WebView usingX5Core=" + webView.getIsX5Core());
        playerController = new TvWebViewController(
                webView,
                () -> channels[currentChannelIndex],
                userScriptRepository::findBestMatch
        );
        playerController.setup();
        setupGestures();

        // 首次加载直接播放，无需防抖
        if (channels != null && channels.length > 0) {
            loadChannelDirectly(currentChannelIndex);
        }
        if (getIntent().getBooleanExtra(EXTRA_SHOW_X5_READY_OSD, false)) {
            showTransientOsd("X5 内核已就绪");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 确保应用失去焦点再回来时（如弹窗关闭后），依然保持沉浸全屏
        if (hasFocus) enableImmersiveMode();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 清理所有未执行的 Handler 任务，防止内存泄漏
        handler.removeCallbacksAndMessages(null);
        stopRemoteManagementServer();
    }

    /**
     * 开启沉浸模式（隐藏状态栏、导航栏）
     */
    @SuppressWarnings("deprecation")
    private void enableImmersiveMode() {
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        getWindow().getDecorView().setSystemUiVisibility(uiOptions);
    }


    // =============================================================================================
    // 3. 初始化与视图配置 (Initialization)
    // =============================================================================================

    @SuppressLint("ClickableViewAccessibility")
    private void initViews() {
        drawerLayout = findViewById(R.id.drawerLayout);
        drawerLayout.setScrimColor(Color.TRANSPARENT); // 【视觉优化】去掉侧边栏打开时的阴影遮罩

        webView = findViewById(R.id.webView);
        touchLayer = findViewById(R.id.touchLayer);
        rvChannels = findViewById(R.id.rvChannels);
        btnSettings = findViewById(R.id.btnSettings);
        tvOsd = findViewById(R.id.tvOsd);

        // 配置列表
        rvChannels.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ChannelAdapter(new ChannelAdapter.Listener() {
            @Override
            public void onChannelClicked(int position) {
                resetAutoTimer();
                currentChannelIndex = position;
                loadChannelDirectly(position);
                saveChannelIndex();
                drawerLayout.closeDrawer(GravityCompat.END);
            }

            @Override
            public void onRequestSettingsFocus() {
                btnSettings.requestFocus();
            }
        });
        rvChannels.setAdapter(adapter);
        adapter.submitChannels(channelItems, currentChannelIndex);
        rvChannels.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if ((bottom - top) != (oldBottom - oldTop)) {
                adaptChannelListDensity();
            }
        });
        rvChannels.post(this::adaptChannelListDensity);

        // 1. 设置按钮点击事件
        btnSettings.setOnClickListener(v -> {
            resetAutoTimer();
            drawerLayout.closeDrawer(GravityCompat.END);
            manageTvChannels();
        });
        rvChannels.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    // 手指按在屏幕上时，移除自动关闭任务，保持常亮
                    handler.removeCallbacks(autoCloseRunnable);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    // 手指离开屏幕后，重新开始 5 秒倒计时
                    resetAutoTimer();
                    break;
            }
            // 返回 false，表示我不拦截事件，继续交给 RecyclerView 去处理滑动
            return false;
        });
        // 2. 设置按钮焦点样式监听
        // 解决按钮选中时文字看不清的问题：选中时变黑底白字并放大，未选中时恢复透明
        btnSettings.setOnFocusChangeListener((v, hasFocus) -> {
            // 根据XML结构获取子控件：Child 1=Badge("设置"), Child 2=Title("频道管理")
            TextView tvBadge = (TextView) btnSettings.getChildAt(1);
            TextView tvTitle = (TextView) btnSettings.getChildAt(2);

            if (hasFocus) {
                tvTitle.setTextColor(Color.BLACK);
                tvBadge.setTextColor(Color.DKGRAY);
                v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(150).start();
                v.setBackgroundResource(R.drawable.selector_channel_card);
            } else {
                tvTitle.setTextColor(Color.WHITE);
                tvBadge.setTextColor(Color.parseColor("#AAFFFFFF"));
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
            }
        });

        // 3. 抽屉状态监听
        drawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View d) {
                resetAutoTimer();
            } // 打开时重置计时

            @Override
            public void onDrawerClosed(@NonNull View d) {
                handler.removeCallbacks(autoCloseRunnable);
            } // 关闭时取消计时
        });
    }

    private void setupGestures() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
                openSidebar(); // 单击呼出菜单
                return true;
            }

            @Override
            public boolean onFling(
                    @NonNull MotionEvent e1,
                    @NonNull MotionEvent e2,
                    float velocityX,
                    float velocityY
            ) {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                // 判定为垂直滑动
                if (Math.abs(diffY) > Math.abs(diffX)) {
                    if (Math.abs(diffY) > 100 && Math.abs(velocityY) > 100) {
                        if (diffY > 0) switchToPrevChannel(); // 下滑 -> 上一台
                        else switchToNextChannel();           // 上滑 -> 下一台
                        return true;
                    }
                }
                return false;
            }
        });
        // 将触摸层的事件委托给手势识别器
        touchLayer.setOnTouchListener((v, event) -> {
            boolean handled = gestureDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP) {
                v.performClick();
            }
            return handled;
        });
    }


    // =============================================================================================
    // 4. 输入控制与按键逻辑 (Input Controller)
    // =============================================================================================
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (handleAppKeyDown(event.getKeyCode())) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }
    /**
     * 处理物理按键事件
     * 实现了【双模兼容】：同时支持 电视遥控器按键 和 电脑键盘映射
     */
    private long lastBackPressTime = 0; // 记录上一次返回键按下时间
    private static final int BACK_PRESS_INTERVAL = 2000; // 2 秒内算双击
    private long lastRawWebEntryPressTime = 0;
    private static final int RAW_WEB_ENTRY_INTERVAL = 2000;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (handleAppKeyDown(keyCode)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private boolean handleAppKeyDown(int keyCode) {
        Log.d("keyCode",":"+keyCode);
        if (rawWebMode) {
            if (keyCode == KeyEvent.KEYCODE_BACK) {
                exitRawWebMode();
                return true;
            }
            return false;
        }
        switch (keyCode) {
            // 数字键选台
            case KeyEvent.KEYCODE_0:
            case KeyEvent.KEYCODE_1:
            case KeyEvent.KEYCODE_2:
            case KeyEvent.KEYCODE_3:
            case KeyEvent.KEYCODE_4:
            case KeyEvent.KEYCODE_5:
            case KeyEvent.KEYCODE_6:
            case KeyEvent.KEYCODE_7:
            case KeyEvent.KEYCODE_8:
            case KeyEvent.KEYCODE_9:
                handleDigitInput(keyCode - KeyEvent.KEYCODE_0);
                return true;
        }
        // 场景 A: 侧边栏已打开 (Drawer Opened)
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            resetAutoTimer(); // 操作重置倒计时
            switch (keyCode) {
                // 1. 关闭菜单 (返回/左/A)
                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_A:
                    drawerLayout.closeDrawer(GravityCompat.END);
                    return true;

                // 2. 聚焦设置按钮 (菜单/M)
                case KeyEvent.KEYCODE_MENU:
                case KeyEvent.KEYCODE_M:
                    btnSettings.requestFocus();
                    return true;

                // 3. 确认/点击 (OK/空格/回车)
                // 兼容逻辑：强制触发当前焦点的点击事件
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    View focus = getCurrentFocus();
                    if (focus != null) focus.performClick();
                    return true;

                // 4. 向上移动 (上/W)
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_W:
                    // 循环逻辑：在设置按钮按上 -> 跳到列表底部
                    if (btnSettings.hasFocus()) {
                        int max = adapter.getItemCount() - 1;
                        if (max >= 0) jumpToPosition(max);
                        return true;
                    }
                    if (getFocusedChannelPosition() == 0) {
                        btnSettings.requestFocus();
                        return true;
                    }
                    simulateFocusMove(View.FOCUS_UP);
                    return true;

                // 5. 向下移动 (下/S)
                case KeyEvent.KEYCODE_DPAD_DOWN:
                case KeyEvent.KEYCODE_S:
                    // 在设置按钮按下 -> 强制跳到列表顶部(0)
                    // 防止系统 FocusSearch 错误地跳到屏幕中间可见的 Item
                    if (btnSettings.hasFocus()) {
                        jumpToPosition(0);
                        return true;
                    }
                    if (getFocusedChannelPosition() == adapter.getItemCount() - 1) {
                        btnSettings.requestFocus();
                        return true;
                    }
                    simulateFocusMove(View.FOCUS_DOWN);
                    return true;
            }
            return false;
        }

        // 场景 B: 全屏播放时 (Fullscreen Player)
        switch (keyCode) {
            // 呼出菜单 (菜单/M)
            case KeyEvent.KEYCODE_MENU:
            case KeyEvent.KEYCODE_M:
                manageTvChannels();
                return true;

            // 呼出侧边栏 (OK/右/D/空格/回车)
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                openSidebar();
                return true;

            // 上一台 (上/W)
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
                switchToPrevChannel();
                return true;

            // 下一台 (下/S)
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_S:
                switchToNextChannel();
                return true;

            // 全屏播放时左键没有业务动作，但也必须拦截，避免事件落入 X5 WebView。
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
                confirmEnterRawWebMode();
                return true;

            case KeyEvent.KEYCODE_BACK:
                long now = System.currentTimeMillis();
                if (now - lastBackPressTime < BACK_PRESS_INTERVAL) {
                    // 双击确认退出
                    finish();
                } else {
                    Toast.makeText(this, "再按一次返回退出", Toast.LENGTH_SHORT).show();
                    lastBackPressTime = now;
                }
                return true;
        }
        return false;
    }

    /**
     * 辅助方法：手动控制焦点移动
     * 作用：解决 W/S 等键盘映射键无法被系统 View 识别为方向键的问题
     */
    private void simulateFocusMove(int direction) {
        View currentFocus = getCurrentFocus();
        if (currentFocus == null) return;
        View nextFocus = currentFocus.focusSearch(direction);
        if (nextFocus != null) nextFocus.requestFocus();
    }

    private int getFocusedChannelPosition() {
        View currentFocus = getCurrentFocus();
        if (currentFocus == null) return RecyclerView.NO_POSITION;
        RecyclerView.ViewHolder holder = rvChannels.findContainingViewHolder(currentFocus);
        return holder != null ? holder.getAdapterPosition() : RecyclerView.NO_POSITION;
    }


    // =============================================================================================
    // 5. 频道切换与播放逻辑 (Channel Player Logic)
    // =============================================================================================

    // 切换到上一台
    private void switchToPrevChannel() {
        if (channels == null || channels.length == 0) return;
        // 计算索引（防止负数）
        currentChannelIndex = (currentChannelIndex - 1 + channels.length) % channels.length;
        loadChannelWithThrottling(currentChannelIndex); // 使用防抖加载
        saveChannelIndex();
    }

    // 切换到下一台
    private void switchToNextChannel() {
        if (channels == null || channels.length == 0) return;
        currentChannelIndex = (currentChannelIndex + 1) % channels.length;
        loadChannelWithThrottling(currentChannelIndex); // 使用防抖加载
        saveChannelIndex();
    }

    // 【核心功能】带防抖机制的频道加载
    // 作用：快速换台时只更新 OSD 文字，停止按键后再加载视频，防止卡顿
    private void loadChannelWithThrottling(int index) {
        if (channels == null || channels.length == 0) return;

        // 1. 立即更新 OSD (UI反馈必须快)
        String name = channelsMap.get(channels[index]);
        tvOsd.setText(getString(R.string.channel_osd_format, index + 1, name));
        tvOsd.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdRunnable);
        handler.postDelayed(hideOsdRunnable, 3000);

        // 2. 更新待加载索引
        pendingChannelIndex = index;

        // 3. 重置并延迟执行 WebView 加载
        handler.removeCallbacks(confirmChannelSwitchRunnable);
        handler.postDelayed(confirmChannelSwitchRunnable, SWITCH_DELAY);

        Log.d("ChannelSwitch", "Scheduled channel: " + index);
    }

    // 直接加载频道 (用于列表点击、APP启动时)
    private void loadChannelDirectly(int index) {
        if (channels == null || channels.length == 0) return;

        // 立即更新 OSD
        String name = channelsMap.get(channels[index]);
        tvOsd.setText(getString(R.string.channel_osd_format, index + 1, name));
        tvOsd.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdRunnable);
        handler.postDelayed(hideOsdRunnable, 3000);

        // 立即加载网页
        webView.loadUrl(channels[index]);
    }

    // 保存当前频道索引到 SharedPreferences
    private void confirmEnterRawWebMode() {
        long now = System.currentTimeMillis();
        if (now - lastRawWebEntryPressTime <= RAW_WEB_ENTRY_INTERVAL) {
            lastRawWebEntryPressTime = 0;
            enterRawWebMode();
        } else {
            lastRawWebEntryPressTime = now;
            showTransientOsd("再次按左键进入原始网页");
        }
    }

    private void enterRawWebMode() {
        if (channels == null || channels.length == 0) return;

        rawWebMode = true;
        handler.removeCallbacks(confirmChannelSwitchRunnable);
        pendingChannelIndex = -1;
        if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
            drawerLayout.closeDrawer(GravityCompat.END);
        }
        if (channelManagerDialog != null && channelManagerDialog.isShowing()) {
            channelManagerDialog.dismiss();
        }
        touchLayer.setVisibility(View.GONE);
        playerController.enterRawWebMode();
        showPersistentOsd("当前为原始网页，按返回回到播放");
        webView.loadUrl(channels[currentChannelIndex]);
    }

    private void exitRawWebMode() {
        if (!rawWebMode || channels == null || channels.length == 0) return;

        rawWebMode = false;
        touchLayer.setVisibility(View.VISIBLE);
        playerController.exitRawWebMode();
        webView.loadUrl(channels[currentChannelIndex]);
        showTransientOsd("已返回播放模式");
    }

    private void saveChannelIndex() {
        configPrefs.edit().putInt("lastChannel", currentChannelIndex).apply();
    }

    // 数字键输入处理 (带超时判断)
    private void handleDigitInput(int d) {
        long now = System.currentTimeMillis();
        // 如果间隔超过1秒，视为新输入，否则追加数字
        if (now - lastDigitTime > 1000) digitBuffer = d;
        else digitBuffer = digitBuffer * 10 + d;

        lastDigitTime = now;

        if (digitBuffer > 0) {
            tvOsd.setText(getString(R.string.digit_osd_format, digitBuffer));
            tvOsd.setVisibility(View.VISIBLE);
            handler.removeCallbacks(hideOsdRunnable);
            handler.postDelayed(hideOsdRunnable, 3000);
        }
        // 1秒后确认输入
        handler.removeCallbacks(digitConfirmRunnable);
        handler.postDelayed(digitConfirmRunnable, 1000);
    }

    // 确认数字跳转
    private void confirmDigitInput() {
        int idx = digitBuffer - 1;
        if (channels != null && idx >= 0 && idx < channels.length) {
            currentChannelIndex = idx;
            loadChannelWithThrottling(idx);
            saveChannelIndex();
        } else {
            tvOsd.setText("无此频道");
        }
        digitBuffer = -1;
    }


    // =============================================================================================
    // 6. 侧边栏与 UI 交互逻辑 (Sidebar UI Logic)
    // =============================================================================================

    private void openSidebar() {
        adapter.setCurrentChannelIndex(currentChannelIndex);
        drawerLayout.openDrawer(GravityCompat.END);
        rvChannels.post(() -> {
            adaptChannelListDensity();
            LinearLayoutManager layoutManager = (LinearLayoutManager) rvChannels.getLayoutManager();
            if (layoutManager != null) {
                int listHeight = rvChannels.getHeight();
                int itemHeight;
                View firstChild = rvChannels.getChildAt(0);
                if (firstChild != null) {
                    itemHeight = firstChild.getHeight();
                } else {
                    itemHeight = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP, 60, getResources().getDisplayMetrics());
                }
                int offset = (listHeight / 2) - (itemHeight / 2);
                layoutManager.scrollToPositionWithOffset(currentChannelIndex, offset);
            }
        });
        // 延时聚焦保持不变（等待抽屉动画和滚动完成）
        rvChannels.postDelayed(() -> {
            rvChannels.requestFocus();
            RecyclerView.ViewHolder holder = rvChannels.findViewHolderForAdapterPosition(currentChannelIndex);
            if (holder != null) holder.itemView.requestFocus();
        }, 200);
    }

    private void adaptChannelListDensity() {
        int listHeightPx = rvChannels.getHeight();
        if (listHeightPx <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float listHeightDp = listHeightPx / density;

        int targetVisibleRows = Math.round(listHeightDp / 60f);
        targetVisibleRows = Math.max(5, Math.min(7, targetVisibleRows));

        int adaptiveItemHeightPx = (listHeightPx / targetVisibleRows) - dp(4);
        adaptiveItemHeightPx = Math.max(dp(40), Math.min(dp(76), adaptiveItemHeightPx));
        adapter.setItemHeightPx(adaptiveItemHeightPx);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    // 列表滚动辅助方法 (带越界修正)
    private void jumpToPosition(int index) {
        if (channels == null || channels.length == 0) return;
        if (index < 0) index = 0;
        if (index >= channels.length) index = channels.length - 1;

        rvChannels.scrollToPosition(index);
        int finalIndex = index;
        // 延时聚焦，确保 RecyclerView 滚动到位
        rvChannels.postDelayed(() -> {
            RecyclerView.ViewHolder holder = rvChannels.findViewHolderForAdapterPosition(finalIndex);
            if (holder != null) holder.itemView.requestFocus();
            else rvChannels.requestFocus();
        }, 50);
    }

    // 重置侧边栏自动关闭计时器
    private void resetAutoTimer() {
        handler.removeCallbacks(autoCloseRunnable);
        handler.postDelayed(autoCloseRunnable, AUTO_CLOSE_DELAY);
    }


    // =============================================================================================
    // 7. 数据管理与弹窗 (Data Management)
    // =============================================================================================

    // 从 Prefs 加载用户频道数据 (JSON)
    private void loadUserChannels() {
        channelsMap.clear();
        channelItems.clear();

        List<Channel> userChannels = channelRepository.loadUserChannels();
        if (userChannels.isEmpty()) {
            channelItems.add(new Channel("频道添加指南", "file:///android_asset/add_channel_help.html"));
        } else {
            channelItems.addAll(userChannels);
        }

        for (Channel channel : channelItems) {
            channelsMap.put(channel.getUrl(), channel.getName());
        }
        channels = channelsMap.keySet().toArray(new String[0]);
    }

    private void reloadChannelsKeepingCurrentUrl() {
        String currentUrl = channels != null && channels.length > 0 && currentChannelIndex < channels.length
                ? channels[currentChannelIndex]
                : null;
        loadUserChannels();

        if (currentUrl != null) {
            int sameUrlIndex = indexOfChannelUrl(currentUrl);
            if (sameUrlIndex >= 0) {
                currentChannelIndex = sameUrlIndex;
            } else if (channels.length > 0) {
                currentChannelIndex = Math.min(currentChannelIndex, channels.length - 1);
                loadChannelDirectly(currentChannelIndex);
                saveChannelIndex();
            }
        } else {
            currentChannelIndex = 0;
        }

        adapter.submitChannels(channelItems, currentChannelIndex);
    }

    private int indexOfChannelUrl(String url) {
        for (int i = 0; i < channels.length; i++) {
            if (channels[i].equals(url)) return i;
        }
        return -1;
    }

    private void manageTvChannels() {
        String localIp = NetworkUtils.findLocalIpv4Address();
        String remoteUrl = null;
        try {
            if (localIp != null) {
                startRemoteManagementServer();
                remoteUrl = "http://" + localIp + ":" + remoteManagementServer.getPort() + "/";
            }
            channelManagerDialog = ChannelManagerDialog.show(this, remoteUrl, createChannelManagerListener());
            channelManagerDialog.setOnDismissListener(d -> {
                stopRemoteManagementServer();
                channelManagerDialog = null;
            });
        } catch (Exception e) {
            Log.e("RemoteManagement", "Unable to start phone management", e);
            stopRemoteManagementServer();
            ChannelManagerDialog.show(this, null, createChannelManagerListener());
            showToast("手机管理启动失败，已保留电视端管理");
        }
    }

    private ChannelManagerDialog.Listener createChannelManagerListener() {
        return new ChannelManagerDialog.Listener() {
            @Override
            public void onAddChannel(String name, String url) {
                addOneChannel(name, url);
            }

            @Override
            public void onDeleteCurrentChannel() {
                deleteCurrentChannel();
            }

            @Override
            public void onCheckUpdates() {
                Intent intent = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://pan.baidu.com/s/1ma_jq-9wbR4IQ5_lQO_Eng?pwd=5555"));
                startActivity(intent);
            }

            @Override
            public void onOpenX5Manager() {
                startActivity(new Intent(MainActivity.this, X5ManagerActivity.class));
            }

            @Override
            public void onOpenRawWebPage() {
                enterRawWebMode();
            }
        };
    }

    private void startRemoteManagementServer() throws Exception {
        if (remoteManagementServer != null) return;
        remoteManagementServer = new RemoteManagementServer(
                getAssets(),
                channelRepository,
                userScriptRepository,
                getString(R.string.DefaultChannel),
                () -> handler.post(this::reloadChannelsKeepingCurrentUrl)
        );
        remoteManagementServer.start();
    }

    private void stopRemoteManagementServer() {
        if (remoteManagementServer != null) {
            remoteManagementServer.shutdown();
            remoteManagementServer = null;
        }
    }

    private void addOneChannel(String name, String url) {
        if (name.isEmpty() || url.isEmpty()) {
            showToast("信息不完整");
            return;
        }
        boolean added = channelRepository.addChannel(new Channel(name, url));
        showToast(added ? "已添加" : "频道已存在");
        if (added) reloadChannelsKeepingCurrentUrl();
    }

    private void deleteCurrentChannel() {
        if (channels == null || channels.length == 0) return;
        String currentUrl = channels[currentChannelIndex];
        if (currentUrl.startsWith("file:///")) {
            showToast("内置无法删除");
            return;
        }
        boolean deleted = channelRepository.deleteByUrl(currentUrl);
        if (deleted) {
            showToast("已删除");
            reloadChannelsKeepingCurrentUrl();
        }
    }

    private void showToast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private void showTransientOsd(String message) {
        tvOsd.setText(message);
        tvOsd.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdRunnable);
        handler.postDelayed(hideOsdRunnable, 1800);
    }

    private void showPersistentOsd(String message) {
        tvOsd.setText(message);
        tvOsd.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOsdRunnable);
    }



    // 内部类：频道列表适配器
}
