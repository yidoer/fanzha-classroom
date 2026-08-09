package cn.fanzha.classroom;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StoryRepository {
    private StoryRepository(){}
    public static Map<String,StoryScript> load(Context context){
        Map<String,StoryScript> result=new LinkedHashMap<>();
        try(BufferedReader reader=new BufferedReader(new InputStreamReader(context.getAssets().open("interactive_stories.json")))){
            StringBuilder text=new StringBuilder();String line;while((line=reader.readLine())!=null)text.append(line);
            JSONArray stories=new JSONObject(text.toString()).getJSONArray("stories");
            for(int i=0;i<stories.length();i++){StoryScript story=StoryScript.fromJson(stories.getJSONObject(i));result.put(story.id,story);}
        }catch(Exception e){throw new IllegalStateException("互动剧情加载失败",e);}
        return result;
    }
    public static List<FraudCase> playableCases(Context context,List<FraudCase> cases){Map<String,StoryScript> scripts=load(context);List<FraudCase> result=new ArrayList<>();for(FraudCase item:cases)if(scripts.containsKey(item.id))result.add(item);return result;}
}
