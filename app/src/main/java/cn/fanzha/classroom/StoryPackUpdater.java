package cn.fanzha.classroom;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class StoryPackUpdater {
    public interface Callback { void onResult(Result result); }
    public static final class Result {
        public final boolean updated;
        public final String message;
        Result(boolean updated, String message) { this.updated = updated; this.message = message; }
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final int PACK_SCHEMA = 1; // 兼容 v1；v2 允许 cases+stories 双结构
    private StoryPackUpdater() {}

    public static File activePackFile(Context context) {
        return new File(new File(context.getFilesDir(), "story-packs"), "active.json");
    }

    public static File activeStoriesFile(Context context) {
        return new File(new File(context.getFilesDir(), "story-packs"), "stories.json");
    }

    private static File previousPackFile(Context context) {
        return new File(new File(context.getFilesDir(), "story-packs"), "previous.json");
    }

    private static File previousStoriesFile(Context context) {
        return new File(new File(context.getFilesDir(), "story-packs"), "previous-stories.json");
    }

    public static int currentVersion(Context context) {
        return context.getSharedPreferences("story_pack", Context.MODE_PRIVATE).getInt("version", 1);
    }

    public static boolean canRollback(Context context) { return previousPackFile(context).isFile(); }

    public static void rollback(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            Result result;
            try {
                File active = activePackFile(app), previous = previousPackFile(app);
                if (!previous.isFile()) result = new Result(false, "没有可回退的剧情版本");
                else {
                    File swap = new File(previous.getParentFile(), "rollback-swap.json");
                    if (active.isFile() && !active.renameTo(swap)) throw new IllegalStateException("无法暂存当前版本");
                    if (!previous.renameTo(active)) { if (swap.isFile()) swap.renameTo(active); throw new IllegalStateException("无法恢复上一版本"); }
                    if (swap.isFile()) swap.renameTo(previous);
                    byte[] activeBytes;
                    try (java.io.FileInputStream input = new java.io.FileInputStream(active); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[8192]; int read;
                        while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
                        activeBytes = output.toByteArray();
                    }
                    JSONObject root = new JSONObject(new String(activeBytes, StandardCharsets.UTF_8));
                    int version = root.getJSONObject("meta").getInt("packVersion");
                    app.getSharedPreferences("story_pack", Context.MODE_PRIVATE).edit().putInt("version", version).apply();
                    result = new Result(true, "已回退到剧情包 v" + version);
                }
            } catch (Exception e) { result = new Result(false, "回退失败，当前剧情未被清除"); }
            Result finalResult = result;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> callback.onResult(finalResult));
        });
    }

    public static void check(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            Result result;
            if (BuildConfig.STORY_MANIFEST_URL.trim().isEmpty() && BuildConfig.STORY_FALLBACK_MANIFEST_URL.trim().isEmpty()) {
                result = new Result(false, "尚未配置 GitHub 剧情仓库地址");
            } else {
                try { result = updateWithRetry(app); }
                catch (Exception e) { result = new Result(false, "检查失败，已继续使用本地剧情"); }
            }
            Result finalResult = result;
            android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            main.post(() -> callback.onResult(finalResult));
        });
    }

    private static Result updateWithRetry(Context context) throws Exception {
        Exception last = null;
        for (int attempt = 0; attempt < 4; attempt++) {
            try { return updateOnce(context); }
            catch (Exception e) {
                last = e;
                if (attempt < 3) Thread.sleep((long) (700 * Math.pow(2, attempt)) + (long) (Math.random() * 350));
            }
        }
        throw last == null ? new IllegalStateException("更新失败") : last;
    }

    private static Result updateOnce(Context context) throws Exception {
        byte[] manifestBytes;
        try { manifestBytes = download(BuildConfig.STORY_MANIFEST_URL); }
        catch (Exception primary) {
            if (BuildConfig.STORY_FALLBACK_MANIFEST_URL.trim().isEmpty()) throw primary;
            manifestBytes = download(BuildConfig.STORY_FALLBACK_MANIFEST_URL);
        }
        JSONObject manifest = new JSONObject(new String(manifestBytes, StandardCharsets.UTF_8));
        if (manifest.getInt("schemaVersion") != PACK_SCHEMA) throw new IllegalArgumentException("不支持的剧情包格式");
        int minAppVersion = manifest.optInt("minAppVersionCode", 1);
        if (minAppVersion > BuildConfig.VERSION_CODE) return new Result(false, "新剧情需要先升级 App");
        int remoteVersion = manifest.getInt("packVersion");
        int localVersion = context.getSharedPreferences("story_pack", Context.MODE_PRIVATE).getInt("version", 1);
        if (remoteVersion <= localVersion) return new Result(false, "当前已是最新剧情包 v" + localVersion);

        byte[] bytes = download(manifest.getString("downloadUrl"));
        String expected = manifest.getString("sha256").toLowerCase(Locale.ROOT);
        if (!expected.equals(sha256(bytes))) throw new SecurityException("剧情包校验失败");
        validatePack(bytes, remoteVersion);

        File target = activePackFile(context);
        File directory = target.getParentFile();
        if (directory == null || (!directory.exists() && !directory.mkdirs())) throw new IllegalStateException("无法创建剧情目录");
        File pending = new File(directory, "pending.json");
        try (FileOutputStream output = new FileOutputStream(pending)) {
            output.write(bytes); output.getFD().sync();
        }
        File previous = previousPackFile(context);
        if (previous.exists() && !previous.delete()) throw new IllegalStateException("无法清理历史剧情");
        if (target.exists() && !target.renameTo(previous)) throw new IllegalStateException("无法备份旧剧情");
        if (!pending.renameTo(target)) throw new IllegalStateException("无法启用新剧情");

        // 如果剧情包包含互动剧本，将其拆存到独立文件供 StoryRepository 热加载。
        JSONObject downloadedRoot = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        if (downloadedRoot.has("stories")) {
            JSONObject storiesRoot = new JSONObject();
            storiesRoot.put("stories", downloadedRoot.getJSONArray("stories"));
            File storiesPending = new File(directory, "stories-pending.json");
            try (FileOutputStream output = new FileOutputStream(storiesPending)) {
                output.write(storiesRoot.toString().getBytes(StandardCharsets.UTF_8)); output.getFD().sync();
            }
            File storiesTarget = activeStoriesFile(context);
            File storiesPrevious = previousStoriesFile(context);
            if (storiesPrevious.exists() && !storiesPrevious.delete()) throw new IllegalStateException("无法清理历史互动剧情");
            if (storiesTarget.exists() && !storiesTarget.renameTo(storiesPrevious)) throw new IllegalStateException("无法备份旧互动剧情");
            if (!storiesPending.renameTo(storiesTarget)) throw new IllegalStateException("无法启用新互动剧情");
        }
        context.getSharedPreferences("story_pack", Context.MODE_PRIVATE).edit().putInt("version", remoteVersion).apply();
        return new Result(true, "剧情已更新至 v" + remoteVersion + "，重新进入故事即可体验");
    }

    private static byte[] download(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "FanZha-Classroom/" + BuildConfig.VERSION_NAME);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                if (output.size() > 5 * 1024 * 1024) throw new IllegalArgumentException("剧情包过大");
            }
            return output.toByteArray();
        } finally { connection.disconnect(); }
    }

    private static void validatePack(byte[] bytes, int expectedVersion) throws Exception {
        JSONObject root = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        JSONObject meta = root.getJSONObject("meta");
        int schema = meta.getInt("schemaVersion");
        if (schema != PACK_SCHEMA && schema != 2) throw new IllegalArgumentException("不支持的剧情包格式");
        if (meta.getInt("packVersion") != expectedVersion)
            throw new IllegalArgumentException("剧情包版本不一致");
        JSONArray cases = root.getJSONArray("cases");
        if (cases.length() < 1 || cases.length() > 1000) throw new IllegalArgumentException("剧情数量异常");
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (int i = 0; i < cases.length(); i++) {
            JSONObject item = cases.getJSONObject(i);
            String id = item.getString("id");
            if (id.isEmpty() || !ids.add(id) || item.getString("title").isEmpty()) throw new IllegalArgumentException("剧情条目无效");
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format(Locale.ROOT, "%02x", value));
        return result.toString();
    }
}


