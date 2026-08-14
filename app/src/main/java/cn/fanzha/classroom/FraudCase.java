package cn.fanzha.classroom;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

public class FraudCase {
    public final String id;
    public final String title;
    public final String category;
    public final String risk;
    public final String materialType;
    public final String summary;
    public final String story;
    public final String warningSigns;
    public final String response;
    public final String sourceName;
    public final String sourceDate;
    public final String sourceUrl;
    public final boolean isScam;

    /** Attached at load time so the shelf can show what the story itself declares. */
    private StoryScript script;

    private FraudCase(JSONObject json) {
        id = json.optString("id");
        title = json.optString("title");
        category = json.optString("category");
        risk = json.optString("risk", "高风险");
        materialType = json.optString("materialType", "权威归纳");
        summary = json.optString("summary");
        story = json.optString("story");
        warningSigns = join(json.optJSONArray("warningSigns"));
        response = join(json.optJSONArray("response"));
        sourceName = json.optString("sourceName");
        sourceDate = json.optString("sourceDate");
        sourceUrl = json.optString("sourceUrl");
        isScam = json.optBoolean("isScam", true);
    }

    public static FraudCase fromJson(JSONObject json) {
        return new FraudCase(json);
    }

    public void attachScript(StoryScript value) { script = value; }

    public String searchableText() {
        StringBuilder text = new StringBuilder();
        text.append(title).append(category).append(materialType).append(summary).append(story)
            .append(warningSigns).append(response).append(sourceName);
        if (script != null) text.append(script.title).append(script.teaser).append(script.shelf);
        return text.toString().toLowerCase(Locale.ROOT);
    }

    public String publicTitle() {
        if (script != null && !script.title.isEmpty()) return script.title;
        return title.isEmpty() ? "一次需要判断的相遇" : title;
    }

    public String publicTeaser() {
        if (script != null && !script.teaser.isEmpty()) return script.teaser;
        return summary.isEmpty() ? "信息看起来足够真实，但真正重要的细节还没有浮出水面。" : summary;
    }

    public String publicShelf() {
        if (script != null && !script.shelf.isEmpty()) return script.shelf;
        return category.isEmpty() ? "生活岔路" : category;
    }

    /** Endings are the nodes without choices, so the count is the real branch breadth. */
    public int endingCount() {
        if (script == null) return 0;
        int total = 0;
        for (StoryScript.Node node : script.nodes) if (node.choices.isEmpty()) total++;
        return total;
    }

    public int decisionCount() {
        if (script == null) return 0;
        return script.nodes.size() - endingCount();
    }

    /** Rough reading time:每个决策节点约一分半，加上结局总结页的阅读时间。 */
    public String durationLabel() {
        int decisions = decisionCount();
        if (decisions <= 0) return "互动故事";
        int minutes = Math.max(3, Math.min(15, 3 + (int) Math.round(decisions * 1.2)));
        return "约 " + minutes + " 分钟";
    }

    public String branchLabel() {
        int endings = endingCount();
        if (endings <= 0) return "真假不预告 · 你的判断会改变关系与结局";
        return endings + " 种结局 · 真假不预告，每个结局都有完整复盘";
    }

    private static String join(JSONArray array) {
        if (array == null) return "";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < array.length(); i++) {
            if (i > 0) text.append("\n");
            text.append("• ").append(array.optString(i));
        }
        return text.toString();
    }
}