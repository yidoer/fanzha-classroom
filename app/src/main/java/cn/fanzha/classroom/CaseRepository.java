package cn.fanzha.classroom;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public final class CaseRepository {
    private CaseRepository() {}

    public static List<FraudCase> load(Context context) {
        List<FraudCase> cases = new ArrayList<>();
        File downloaded = StoryPackUpdater.activePackFile(context);
        try (InputStream input = downloaded.isFile() ? new FileInputStream(downloaded) : context.getAssets().open("fraud_cases.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
            JSONObject root = new JSONObject(json.toString());
            JSONArray array = root.getJSONArray("cases");
            if (array.length() < 1) throw new IllegalStateException("案例库为空");
            for (int i = 0; i < array.length(); i++) cases.add(FraudCase.fromJson(array.getJSONObject(i)));
        } catch (Exception e) {
            if (downloaded.isFile()) {
                downloaded.delete();
                return load(context);
            }
            throw new IllegalStateException("案例库加载失败", e);
        }
        return cases;
    }
}
