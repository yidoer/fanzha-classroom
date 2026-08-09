package cn.fanzha.classroom;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
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
    private LinearLayout controls, categories, infoContent;
    private RecyclerView caseList;
    private ScrollView infoScroll;
    private TextView count, headerTitle, headerSubtitle;
    private String selectedCategory = "全部";
    private boolean favoritesOnly;
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
        controls = findViewById(R.id.libraryControls);
        categories = findViewById(R.id.categoryContainer);
        caseList = findViewById(R.id.caseList);
        infoScroll = findViewById(R.id.infoScroll);
        infoContent = findViewById(R.id.infoContent);
        count = findViewById(R.id.resultCount);
        headerTitle = findViewById(R.id.headerTitle);
        headerSubtitle = findViewById(R.id.headerSubtitle);
        findViewById(R.id.navCases).setOnClickListener(v -> showLibrary(false));
        findViewById(R.id.navFavorites).setOnClickListener(v -> showLibrary(true));
        findViewById(R.id.navEmergency).setOnClickListener(v -> showEmergency());
        findViewById(R.id.checkUpdates).setOnClickListener(v -> showUpdateCenter());
    }

    private void showUpdateCenter() {
        String message = "App 版本：" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")" +
                "\n剧情包：v" + StoryPackUpdater.currentVersion(this) +
                "\n\n剧情更新会在应用内完成下载、SHA-256 校验、结构检查和原子替换。更新失败不会影响当前版本。";
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("更新中心")
                .setMessage(message)
                .setPositiveButton("检查剧情更新", null)
                .setNeutralButton("检查 App 更新", null)
                .setNegativeButton(StoryPackUpdater.canRollback(this) ? "回退剧情" : "关闭", null)
                .create();
        dialog.setOnShowListener(v -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> { dialog.dismiss(); checkStoryUpdates(); });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x -> { dialog.dismiss(); checkAppUpdates(); });
            if (StoryPackUpdater.canRollback(this)) dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(x -> {
                dialog.dismiss();
                StoryPackUpdater.rollback(this, result -> new AlertDialog.Builder(this).setTitle("剧情回退").setMessage(result.message).setPositiveButton("知道了", null).show());
            });
        });
        dialog.show();
    }

    private void checkStoryUpdates() {
        Button button = findViewById(R.id.checkUpdates);
        button.setEnabled(false);
        button.setContentDescription("正在检查更新");
        StoryPackUpdater.check(this, result -> {
            button.setEnabled(true);
            button.setContentDescription("更新中心");
            new AlertDialog.Builder(this).setTitle(result.updated ? "更新完成" : "剧情更新")
                    .setMessage(result.message).setPositiveButton("知道了", null).show();
            if (result.updated) recreate();
        });
    }

    private void checkAppUpdates() {
        AppUpdateManager.check(this, result -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle("App 更新").setMessage(result.message).setNegativeButton("稍后", null);
            if (result.updateAvailable) builder.setPositiveButton("在应用内下载", (d, w) -> AppUpdateManager.download(this, result));
            else builder.setPositiveButton("知道了", null);
            builder.show();
        });
    }

    private void setupLibrary() {
        adapter = new CaseAdapter(this);
        caseList.setLayoutManager(new LinearLayoutManager(this));
        caseList.setAdapter(adapter);
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add("全部");
        for (FraudCase item : allCases) values.add(item.publicShelf());
        for (String value : values) {
            MaterialButton chip = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            chip.setText(value);
            chip.setTextSize(13);
            chip.setCornerRadius(dp(18));
            chip.setInsetTop(0); chip.setInsetBottom(0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40));
            params.setMarginEnd(dp(8));
            chip.setLayoutParams(params);
            chip.setOnClickListener(v -> { selectedCategory = value; filterCases(); });
            categories.addView(chip);
        }
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { filterCases(); }
            public void afterTextChanged(Editable s) {}
        });
    }

    private void showLibrary(boolean onlyFavorites) {
        favoritesOnly = onlyFavorites;
        controls.setVisibility(View.VISIBLE);
        caseList.setVisibility(View.VISIBLE);
        infoScroll.setVisibility(View.GONE);
        headerTitle.setText(onlyFavorites ? "我的收藏" : "防诈课堂");
        headerSubtitle.setText(onlyFavorites ? "反复复习最需要警惕的骗局" : "识破套路，守住钱袋");
        filterCases();
    }

    private void filterCases() {
        String query = searchInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<FraudCase> filtered = new ArrayList<>();
        for (FraudCase item : allCases) {
            if (favoritesOnly && !favorites.contains(item.id)) continue;
            if (!"全部".equals(selectedCategory) && !selectedCategory.equals(item.publicShelf())) continue;
            if (!query.isEmpty() && !item.searchableText().contains(query)) continue;
            filtered.add(item);
        }
        adapter.submit(filtered);
        count.setText(favoritesOnly ? "已收藏 " + filtered.size() + " 篇" : "首批开放 " + filtered.size() + " 篇逐篇编写的互动故事");
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

    private void showEmergency() {
        showInfo("紧急止损", "已经转账或泄露信息？现在按顺序做");
        addSection("01 立即止付", "停止与对方联系，不再补交任何“保证金”“解冻费”。立刻联系银行或支付平台申请止付、冻结，并保存受理编号。", "拨打 110", v -> dial("110"));
        addSection("02 尽快报警", "携带身份证、转账记录、聊天记录、对方账号、网址和安装包信息到就近公安机关报案。时间越早，追回机会越大。", "打开国家反诈中心来源", v -> openUrl("https://www.mps.gov.cn/n2253534/n2253543/c9257095/content.html"));
        addSection("03 账户加固", "修改支付、网银、邮箱及社交账号密码；开启双重验证；若泄露银行卡或身份证信息，联系银行挂失并关注异常征信。", null, null);
        addSection("04 固定证据", "截图并导出完整聊天，不要只保留局部；记录对方昵称、账号、电话、收款账户、订单号、域名和 App 名称。不要自行删除涉诈应用。", null, null);
        addSection("重要提醒", "“网警远程办案”“内部关系追回”“黑客追款”通常是二次诈骗。公安机关不会要求把钱转入所谓安全账户。", null, null);
    }

    private void showInfo(String title, String subtitle) {
        controls.setVisibility(View.GONE);
        caseList.setVisibility(View.GONE);
        infoScroll.setVisibility(View.VISIBLE);
        infoContent.removeAllViews();
        headerTitle.setText(title);
        headerSubtitle.setText(subtitle);
    }

    private void addSection(String title, String body, String action, View.OnClickListener listener) {
        LinearLayout card = card();
        card.addView(text(title, 19, Color.rgb(24, 32, 42), true));
        TextView bodyView = text(body, 15, Color.rgb(78, 88, 102), false);
        bodyView.setLineSpacing(0, 1.25f);
        bodyView.setPadding(0, dp(8), 0, 0);
        card.addView(bodyView);
        if (action != null) {
            Button button = new MaterialButton(this);
            button.setText(action);
            button.setOnClickListener(listener);
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
        catch (Exception e) { new AlertDialog.Builder(this).setMessage("无法打开该链接").setPositiveButton("知道了", null).show(); }
    }

    private void dial(String number) { startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number))); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
