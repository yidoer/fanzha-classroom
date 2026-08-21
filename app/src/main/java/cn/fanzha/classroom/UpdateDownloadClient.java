package cn.fanzha.classroom;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class UpdateDownloadClient {
    private UpdateDownloadClient() {}

    static String[] sources(JSONObject manifest, String listKey, String legacyKey) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        JSONArray list = manifest.optJSONArray(listKey);
        if (list != null) {
            for (int i = 0; i < list.length(); i++) addHttps(values, list.optString(i));
        }
        addHttps(values, manifest.optString(legacyKey));
        return values.toArray(new String[0]);
    }

    static byte[] downloadWithFallback(String[] sources, int maxBytes, String accept) throws Exception {
        if (sources == null || sources.length == 0) throw new IllegalStateException("尚未配置更新地址");
        Exception last = null;
        List<String> usable = new ArrayList<>();
        for (String source : sources) {
            if (source != null && source.startsWith("https://") && !usable.contains(source)) usable.add(source);
        }
        if (usable.isEmpty()) throw new IllegalStateException("更新地址必须使用 HTTPS");

        // Two rounds let a temporarily busy mirror recover while still switching sources quickly.
        for (int round = 0; round < 2; round++) {
            for (String source : usable) {
                try {
                    return download(source, maxBytes, accept);
                } catch (Exception error) {
                    last = error;
                    Thread.sleep(350L * (round + 1) + (long) (Math.random() * 250));
                }
            }
        }
        throw last == null ? new IllegalStateException("下载失败") : last;
    }

    private static void addHttps(LinkedHashSet<String> values, String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("https://")) values.add(normalized);
    }

    private static byte[] download(String source, int maxBytes, String accept) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(30000);
        connection.setInstanceFollowRedirects(true);
        connection.setRequestProperty("Accept", accept);
        connection.setRequestProperty("User-Agent", "FanZha-Classroom/" + BuildConfig.VERSION_NAME);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) throw new IllegalStateException("HTTP " + status);
        try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
                if (output.size() > maxBytes) throw new IllegalArgumentException("下载文件过大");
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }
}
