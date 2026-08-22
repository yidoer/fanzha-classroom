package cn.fanzha.classroom;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements CaseAdapter.Listener {
    private final List<FraudCase> allCases = new ArrayList<>();
    private final Set<String> favorites = new LinkedHashSet<>();
    private CaseAdapter adapter;
    private EditText searchInput;
    private ImageButton clearSearch;
    private View filterContent;
    private MaterialButton filterToggle;
    private LinearLayout controls, categories, infoContent;
    private RecyclerView caseList;
    private ScrollView infoScroll;
    private TextView count, headerTitle, headerSubtitle, emptyView;
    private MaterialButton navCases, navFavorites, navEmergency;
    private final java.util.LinkedHashMap<String, MaterialButton> categoryChips = new java.util.LinkedHashMap<>();
    private String selectedCategory = "全部";
    private boolean favoritesOnly;
    private boolean filtersExpanded;
    private String activeNav = "stories";
    private SharedPreferences preferences;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = getSharedPreferences("fanzha", MODE_PRIVATE);
        favorites.addAll(preferences.getStringSet("favorites", new LinkedHashSet<>()));
        allCases.addAll(StoryRepository.playableCases(this, CaseRepository.load(this)));
        bindViews();
        setupLibrary();
        showLibrary(false);
    }

    private void bindViews() {
        searchInput = findViewById(R.id.searchInput);
        clearSearch = findViewById(R.id.clearSearch);
        clearSearch.setOnClickListener(v -> {
            searchInput.setText("");
            searchInput.requestFocus();
        });
        controls = findViewById(R.id.libraryControls);
        filterContent = findViewById(R.id.filterContent);
        filterToggle = findViewById(R.id.filterToggle);
        filterToggle.setOnClickListener(v -> setFiltersExpanded(!filtersExpanded));
        categories = findViewById(R.id.categoryContainer);
        caseList = findViewById(R.id.caseList);
        infoScroll = findViewById(R.id.infoScroll);
        infoContent = findViewById(R.id.infoContent);
        count = findViewById(R.id.resultCount);
        emptyView = findViewById(R.id.emptyView);
        headerTitle = findViewById(R.id.headerTitle);
        headerSubtitle = findViewById(R.id.headerSubtitle);
        navCases = findViewById(R.id.navCases);
        navFavorites = findViewById(R.id.navFavorites);
        navEmergency = findViewById(R.id.navEmergency);
        navCases.setOnClickListener(v -> showLibrary(false));
        navFavorites.setOnClickListener(v -> showLibrary(true));
        navEmergency.setOnClickListener(v -> showEmergency());
        findViewById(R.id.checkUpdates).setOnClickListener(v -> showUpdateCenter());
    }

    private void showUpdateCenter() {
        String message = "App 版本  " + BuildConfig.VERSION_NAME + "  ·  构建 " + BuildConfig.VERSION_CODE +
                "\n剧情包版本  v" + StoryPackUpdater.currentVersion(this) +
                "\n\n剧情包会在应用内完成下载、SHA-256 校验、结构检查与原子替换。任何一步失败，当前可用版本都会保留。";
        List<WashiDialog.Action> actions = new ArrayList<>();
        actions.add(WashiDialog.Action.asyncPrimary("检查剧情更新", this::checkStoryUpdates));
        actions.add(WashiDialog.Action.asyncSecondary("检查 App 更新", this::checkAppUpdates));
        if (StoryPackUpdater.canRollback(this)) {
            actions.add(WashiDialog.Action.danger("回退到上一剧情版本", () ->
                    StoryPackUpdater.rollback(this, result -> WashiDialog.message(
                            this,
                            "剧情回退",
                            result.updated ? "已恢复上一版本" : "未做任何改动",
                            result.message,
                            true,
                            WashiDialog.Action.primary("知道了", result.updated ? this::recreate : null)))));
        }
        actions.add(WashiDialog.Action.secondary("关闭", null));
        WashiDialog.message(this, "更新中心", "应用与剧情维护", message, true,
                actions.toArray(new WashiDialog.Action[0]));
    }

    private void checkStoryUpdates(WashiDialog.ActionHandle handle) {
        StoryPackUpdater.check(this, result -> {
            handle.dismiss();
            WashiDialog.message(this,
                    result.updated ? "更新完成" : "剧情更新",
                    result.updated ? "新剧情已通过完整性校验" : "当前剧情保持不变",
                    result.message,
                    true,
                    WashiDialog.Action.primary(result.updated ? "载入新剧情" : "知道了",
                            result.updated ? this::recreate : null));
        });
    }

    private void checkAppUpdates(WashiDialog.ActionHandle handle) {
        AppUpdateManager.check(this, result -> {
            handle.dismiss();
            if (result.updateAvailable) {
                WashiDialog.message(this, "发现 App " + result.versionName, "来自 GitHub Releases",
                        result.message, true,
                        WashiDialog.Action.primary("下载并校验安装包", () -> AppUpdateManager.download(this, result)),
                        WashiDialog.Action.secondary("稍后再说", null));
            } else {
                WashiDialog.message(this, "App 更新", "版本检查", result.message, true,
                        WashiDialog.Action.primary("知道了", null));
            }
        });
    }
    private void setupLibrary() {
        adapter = new CaseAdapter(this);
        caseList.setLayoutManager(new LinearLayoutManager(this));
        caseList.setAdapter(adapter);
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add("全部");
        for (FraudCase item : allCases) values.add(item.publicShelf());
        categoryChips.clear();
        for (String value : values) {
            MaterialButton chip = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            chip.setText(value);
            chip.setTextSize(13);
            chip.setCornerRadius(dp(22));
            chip.setInsetTop(0); chip.setInsetBottom(0);
            chip.setMinHeight(px(R.dimen.touch_min));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, px(R.dimen.touch_min));
            params.setMarginEnd(dp(8));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> { selectedCategory = value; paintChips(); filterCases(); });
            categoryChips.put(value, chip);
            categories.addView(chip);
        }
        paintChips();
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clearSearch.setVisibility(s.length() == 0 ? View.GONE : View.VISIBLE);
                filterCases();
            }
            public void afterTextChanged(Editable s) {}
        });
    }

    /** The selected shelf gets a filled chip so the active filter is never ambiguous. */
    private void paintChips() {
        int accent = c(R.color.brand_subtle);
        int surface = c(R.color.bg_surface);
        int muted = c(R.color.text_secondary);
        int line = c(R.color.border_subtle);
        int brand = c(R.color.brand);
        for (java.util.Map.Entry<String, MaterialButton> entry : categoryChips.entrySet()) {
            boolean active = entry.getKey().equals(selectedCategory);
            MaterialButton chip = entry.getValue();
            chip.setBackgroundColor(active ? accent : surface);
            chip.setTextColor(active ? brand : muted);
            chip.setStrokeColor(android.content.res.ColorStateList.valueOf(active ? brand : line));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (adapter != null && caseList.getVisibility() == View.VISIBLE) filterCases();
    }

    private void showLibrary(boolean onlyFavorites) {
        favoritesOnly = onlyFavorites;
        activeNav = onlyFavorites ? "favorites" : "stories";
        controls.setVisibility(View.VISIBLE);
        filterToggle.setVisibility(onlyFavorites ? View.GONE : View.VISIBLE);
        setFiltersExpanded(!onlyFavorites && filtersExpanded);
        caseList.setVisibility(View.VISIBLE);
        infoScroll.setVisibility(View.GONE);
        headerTitle.setText(onlyFavorites ? "我的收藏" : "防诈课堂");
        headerSubtitle.setText(onlyFavorites ? "反复复习最需要警惕的骗局" : "识破套路，守住钱袋");
        paintNav();
        filterCases();
    }

    private void setFiltersExpanded(boolean expanded) {
        filtersExpanded = expanded;
        boolean shouldShow = !favoritesOnly && expanded;
        caseList.setPadding(
                caseList.getPaddingLeft(),
                px(shouldShow ? R.dimen.library_filter_expanded_inset : R.dimen.library_overlay_inset),
                caseList.getPaddingRight(),
                caseList.getPaddingBottom());
        filterContent.animate().cancel();
        if (shouldShow) {
            filterContent.setAlpha(0f);
            filterContent.setVisibility(View.VISIBLE);
            filterContent.animate().alpha(1f).setDuration(180).start();
        } else if (filterContent.getVisibility() == View.VISIBLE) {
            filterContent.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                if (!filtersExpanded || favoritesOnly) {
                    filterContent.setVisibility(View.GONE);
                    filterContent.setAlpha(1f);
                }
            }).start();
        } else {
            filterContent.setVisibility(View.GONE);
            filterContent.setAlpha(1f);
        }
        updateFilterToggle();
    }

    private void updateFilterToggle() {
        int activeFilters = 0;
        if (!searchInput.getText().toString().trim().isEmpty()) activeFilters++;
        if (!"全部".equals(selectedCategory)) activeFilters++;
        if (filtersExpanded) {
            filterToggle.setText(R.string.filter_collapse);
            filterToggle.setContentDescription(getString(R.string.filter_collapse));
        } else if (activeFilters > 0) {
            filterToggle.setText(getString(R.string.filter_active_fmt, activeFilters));
            filterToggle.setContentDescription(getString(R.string.filter_expand));
        } else {
            filterToggle.setText(R.string.filter_label);
            filterToggle.setContentDescription(getString(R.string.filter_expand));
        }
    }
    private void filterCases() {
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<FraudCase> filtered = new ArrayList<>();
        for (FraudCase item : allCases) {
            if (favoritesOnly && !favorites.contains(item.id)) continue;
            if (!favoritesOnly && !"全部".equals(selectedCategory) && !selectedCategory.equals(item.publicShelf())) continue;
            if (!favoritesOnly && !query.isEmpty() && !item.searchableText().contains(query)) continue;
            filtered.add(item);
        }
        adapter.submit(filtered);
        count.setText(buildCountText(filtered));
        updateEmptyState(filtered, query);
        updateFilterToggle();
    }

    private void updateEmptyState(List<FraudCase> filtered, String query) {
        if (!filtered.isEmpty()) { emptyView.setVisibility(View.GONE); return; }
        emptyView.setVisibility(View.VISIBLE);
        emptyView.setCompoundDrawablesWithIntrinsicBounds(0, favoritesOnly ? R.drawable.ic_bookmark : (!query.isEmpty() ? R.drawable.ic_search : R.drawable.ic_library), 0, 0);
        emptyView.setCompoundDrawablePadding(dp(14));
        if (favoritesOnly) emptyView.setText(R.string.empty_favorites);
        else if (!query.isEmpty()) emptyView.setText(getString(R.string.empty_search_fmt, searchInput.getText().toString().trim()));
        else emptyView.setText(R.string.empty_category);
    }

    /** Shows both how much is on the shelf and how much of it the player has actually finished. */
    private String buildCountText(List<FraudCase> filtered) {
        if (favoritesOnly) return "已收藏 " + filtered.size() + " 篇";
        int endings = 0, unlocked = 0;
        for (FraudCase item : filtered) {
            endings += item.endingCount();
            unlocked += StoryProgress.unlockedCount(this, item.id);
        }
        if (endings == 0) return filtered.size() + " 篇互动故事";
        return filtered.size() + " 篇互动故事 · 共 " + endings + " 种结局 · 已解锁 " + unlocked + " 种";
    }

    /** The selected destination gets a small tinted capsule inside the floating dock. */
    private void paintNav() {
        MaterialButton[] buttons = { navCases, navFavorites, navEmergency };
        String[] keys = { "stories", "favorites", "emergency" };
        int activeColor = c(R.color.brand);
        int idleColor = c(R.color.text_secondary);
        for (int i = 0; i < buttons.length; i++) {
            boolean active = keys[i].equals(activeNav);
            buttons[i].setBackgroundResource(active ? R.drawable.bg_nav_active : R.drawable.bg_nav_item);
            buttons[i].setBackgroundTintList(null);
            buttons[i].setIconTint(android.content.res.ColorStateList.valueOf(active ? activeColor : idleColor));
            buttons[i].setSelected(active);
        }
    }
    private void startStory(FraudCase item) {
        Intent intent = new Intent(this, StoryActivity.class);
        intent.putExtra("case_id", item.id);
        startActivity(intent);
    }

    @Override public void onCaseClick(FraudCase item) { startStory(item); }

    @Override public void onFavoriteClick(FraudCase item) {
        if (favorites.contains(item.id)) favorites.remove(item.id); else favorites.add(item.id);
        preferences.edit().putStringSet("favorites", new LinkedHashSet<>(favorites)).apply();
        filterCases();
    }

    @Override public boolean isFavorite(FraudCase item) { return favorites.contains(item.id); }

    @Override public boolean isCleared(FraudCase item) { return StoryProgress.isCleared(this, item.id); }

    private void showEmergency() {
        activeNav = "emergency";
        paintNav();
        showInfo("紧急止损", "已经转账或泄露信息？现在按顺序做");
        addEmergencyBanner("先停手，再止损", "挂断与对方的联系，不再补交任何“保证金”“解冻费”。先保住剩余资金，再按下面的顺序处理。");
        addSection("01", "立即止付", "立刻联系银行或支付平台申请止付、冻结，并保存受理编号。若骗子仍在通话，先挂断，不要和他理论。", "110 报警指引", v -> showEmergencyCallDialog(), true);
        addSection("02", "尽快报警", "携带身份证、转账记录、聊天记录、对方账号、网址和安装包信息到就近公安机关报案。时间越早，追回机会越大。", "查看国家反诈中心", v -> openUrl("https://www.mps.gov.cn/n2253534/n2253543/c9257095/content.html"), false);
        addSection("03", "账户加固", "修改支付、网银、邮箱及社交账号密码；开启双重验证；若泄露银行卡或身份证信息，联系银行挂失并关注异常征信。", null, null, false);
        addSection("04", "固定证据", "截图并导出完整聊天，不要只保留局部；记录对方昵称、账号、电话、收款账户、订单号、域名和 App 名称。不要自行删除涉诈应用。", null, null, false);
        addSection("!", "重要提醒", "“网警远程办案”“内部关系追回”“黑客追款”通常是二次诈骗。公安机关不会要求你把钱转入所谓安全账户。", null, null, true);
    }

    private void addEmergencyBanner(String title, String body) {
        LinearLayout card = card();
        card.setBackgroundResource(R.drawable.bg_card_critical);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_emergency);
        mark.setImageTintList(android.content.res.ColorStateList.valueOf(c(R.color.state_critical)));
        mark.setLayoutParams(new LinearLayout.LayoutParams(dp(26), dp(26)));
        titleRow.addView(mark);
        TextView titleView = text(title, 18, c(R.color.state_critical), true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(dp(10));
        titleView.setLayoutParams(titleParams);
        titleRow.addView(titleView);
        card.addView(titleRow);
        TextView bodyView = text(body, 15, c(R.color.state_critical), false);
        bodyView.setLineSpacing(0, 1.3f);
        bodyView.setPadding(0, dp(8), 0, 0);
        card.addView(bodyView);
        infoContent.addView(card);
    }

    /** 110 guidance stays inside the app; the player dials from their own keypad. */
    private void showEmergencyCallDialog() {
        WashiDialog.message(this,
                getString(R.string.emergency_call_title),
                "止损指引 · 不会自动跳转拨号",
                getString(R.string.emergency_call_body),
                true,
                WashiDialog.Action.primary("知道了", null),
                WashiDialog.Action.secondary(getString(R.string.emergency_copy_number), () -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("110", "110"));
                    Toast.makeText(this, R.string.emergency_copied, Toast.LENGTH_SHORT).show();
                }));
    }

    private void showInfo(String title, String subtitle) {
        controls.setVisibility(View.GONE);
        caseList.setVisibility(View.GONE);
        emptyView.setVisibility(View.GONE);
        infoScroll.setVisibility(View.VISIBLE);
        infoContent.removeAllViews();
        headerTitle.setText(title);
        headerSubtitle.setText(subtitle);
    }

    private void addSection(String step, String title, String body, String action, View.OnClickListener listener, boolean danger) {
        LinearLayout card = card();
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView badge = new TextView(this);
        badge.setText(step);
        badge.setTextSize(13);
        badge.setTypeface(null, Typeface.BOLD);
        badge.setTextColor(danger ? c(R.color.state_critical) : c(R.color.brand));
        badge.setBackgroundResource(danger ? R.drawable.bg_chip_critical : R.drawable.bg_chip);
        badge.setGravity(android.view.Gravity.CENTER);
        badge.setPadding(dp(6), dp(3), dp(6), dp(3));
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(30)));
        titleRow.addView(badge);
        TextView titleView = text(title, 19, c(R.color.text_primary), true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        titleParams.setMarginStart(dp(10));
        titleView.setLayoutParams(titleParams);
        titleRow.addView(titleView);
        card.addView(titleRow);
        TextView bodyView = text(body, 15, c(R.color.text_secondary), false);
        bodyView.setLineSpacing(0, 1.25f);
        bodyView.setPadding(0, dp(8), 0, 0);
        card.addView(bodyView);
        if (action != null) {
            MaterialButton button = new MaterialButton(this);
            button.setText(action);
            button.setCornerRadius(dp(8));
            button.setMinHeight(dp(48));
            if (danger) {
                button.setBackgroundColor(c(R.color.danger));
                button.setTextColor(c(R.color.text_on_brand));
            }
            button.setOnClickListener(listener);
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            actionParams.topMargin = dp(10);
            button.setLayoutParams(actionParams);
            card.addView(button);
        }
        infoContent.addView(card);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(params);
        return card;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) {
            WashiDialog.message(this, "链接未打开", "当前设备没有可用的处理应用",
                    "你仍可稍后从更新中心重试。当前 App 和剧情不会受到影响。", true,
                    WashiDialog.Action.primary("知道了", null));
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int px(int dimenRes) { return getResources().getDimensionPixelSize(dimenRes); }
    private int c(int colorRes) { return ContextCompat.getColor(this, colorRes); }
}
