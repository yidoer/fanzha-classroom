package cn.fanzha.classroom;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StoryRepository {
    private StoryRepository(){}

    public static Map<String,StoryScript> load(Context context){
        Map<String,StoryScript> result=new LinkedHashMap<>();
        File downloaded = StoryPackUpdater.activeStoriesFile(context);
        try (InputStream input = downloaded.isFile() ? new FileInputStream(downloaded) : context.getAssets().open("interactive_stories.json");
             BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line);
            JSONArray stories=new JSONObject(text.toString()).getJSONArray("stories");
            for(int i=0;i<stories.length();i++){StoryScript story=StoryScript.fromJson(stories.getJSONObject(i));result.put(story.id,story);}
        }catch(Exception e){
            if (downloaded.isFile()) {
                downloaded.delete();
                return load(context);
            }
            throw new IllegalStateException("互动剧情加载失败",e);
        }
        return result;
    }

    /** Only cases with a matching script are playable; the script also supplies the shelf copy. */
    public static List<FraudCase> playableCases(Context context,List<FraudCase> cases){
        Map<String,StoryScript> scripts=load(context);
        List<FraudCase> result=new ArrayList<>();
        for(FraudCase item:cases){
            StoryScript script=scripts.get(item.id);
            if(script==null)continue;
            item.attachScript(script);
            result.add(item);
        }
        return result;
    }
}
