package cn.fanzha.classroom;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists two things per story: which endings the player unlocked, and a checkpoint
 * for every decision node they have stood on. A checkpoint stores the exact state at
 * first arrival, so jumping back replays the story from that point without inventing
 * stats the player never earned.
 */
public final class StoryProgress {
    private static final String PREFS = "story_progress";
    private StoryProgress() {}

    public static final class Checkpoint {
        public final String nodeId, chapter;
        public final int step, relationship, evidence, exposure, loss;
        public final List<String> decisions;
        Checkpoint(JSONObject json) {
            nodeId = json.optString("node"); chapter = json.optString("chapter");
            step = json.optInt("step", 1); relationship = json.optInt("relationship");
            evidence = json.optInt("evidence"); exposure = json.optInt("exposure");
            loss = json.optInt("loss");
            decisions = new ArrayList<>();
            JSONArray array = json.optJSONArray("decisions");
            if (array != null) for (int i = 0; i < array.length(); i++) decisions.add(array.optString(i));
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Set<String> unlocked(Context context, String storyId) {
        return new LinkedHashSet<>(prefs(context).getStringSet("endings_" + storyId, new LinkedHashSet<>()));
    }

    public static void markUnlocked(Context context, String storyId, String endingNodeId) {
        if (endingNodeId == null || endingNodeId.isEmpty()) return;
        Set<String> current = unlocked(context, storyId);
        if (!current.add(endingNodeId)) return;
        prefs(context).edit().putStringSet("endings_" + storyId, current).apply();
    }

    public static int unlockedCount(Context context, String storyId) {
        return unlocked(context, storyId).size();
    }

    public static boolean isCleared(Context context, String storyId) {
        return unlockedCount(context, storyId) > 0;
    }

    public static List<Checkpoint> checkpoints(Context context, String storyId) {
        List<Checkpoint> result = new ArrayList<>();
        try {
            String raw = prefs(context).getString("ckpt_" + storyId, "[]");
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) result.add(new Checkpoint(array.getJSONObject(i)));
        } catch (Exception ignored) { }
        return result;
    }

    /** First visit wins: a checkpoint is never overwritten by a later, differently-scored visit. */
    public static void saveCheckpoint(Context context, String storyId, String nodeId, String chapter,
                                      int step, int relationship, int evidence, int exposure, int loss,
                                      List<String> decisions) {
        try {
            String raw = prefs(context).getString("ckpt_" + storyId, "[]");
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++)
                if (nodeId.equals(array.getJSONObject(i).optString("node"))) return;
            JSONObject item = new JSONObject();
            item.put("node", nodeId); item.put("chapter", chapter); item.put("step", step);
            item.put("relationship", relationship); item.put("evidence", evidence);
            item.put("exposure", exposure); item.put("loss", loss);
            JSONArray list = new JSONArray();
            for (String value : decisions) list.put(value);
            item.put("decisions", list);
            array.put(item);
            prefs(context).edit().putString("ckpt_" + storyId, array.toString()).apply();
        } catch (Exception ignored) { }
    }
}