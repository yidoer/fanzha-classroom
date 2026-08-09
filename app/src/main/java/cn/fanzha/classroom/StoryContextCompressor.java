package cn.fanzha.classroom;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.List;

public final class StoryContextCompressor {
    private StoryContextCompressor() {}

    public static void save(Context context, FraudCase item, List<String> decisions, int relationship, int evidence, int exposure, int loss) {
        try {
            JSONObject summary = new JSONObject();
            summary.put("schemaVersion", 1); summary.put("storyId", item.id);
            summary.put("truth", item.isScam ? "scam" : "legitimate");
            summary.put("relationship", relationship); summary.put("evidence", evidence);
            summary.put("exposure", exposure); summary.put("loss", loss);
            JSONArray keyDecisions = new JSONArray();
            int start = Math.max(0, decisions.size() - 6);
            for (int i = start; i < decisions.size(); i++) keyDecisions.put(decisions.get(i));
            summary.put("recentDecisions", keyDecisions);
            summary.put("milestones", new JSONArray().put("identity").put("independent_evidence").put("final_boundary"));
            context.getSharedPreferences("story_context", Context.MODE_PRIVATE).edit()
                    .putString("last_summary", summary.toString()).apply();
        } catch (Exception ignored) { }
    }
}
