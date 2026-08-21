package cn.fanzha.classroom;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class StoryScript {
    public final String id, title, teaser, shelf, reveal, clueReveal, lesson;
    public final boolean scam;
    public final int initialRelationship, initialEvidence, initialExposure, initialLoss;
    public final List<Node> nodes;
    public final List<String> timeline;
    public final JSONObject endings;

    private StoryScript(JSONObject json) {
        id=json.optString("id"); title=json.optString("title"); teaser=json.optString("teaser"); shelf=json.optString("shelf");
        reveal=json.optString("reveal"); clueReveal=json.optString("clueReveal"); lesson=json.optString("lesson"); scam=json.optBoolean("isScam",true);
        JSONObject state=json.optJSONObject("initialState");
        initialRelationship=state==null?5:state.optInt("relationship",5); initialEvidence=state==null?0:state.optInt("evidence",0);
        initialExposure=state==null?0:state.optInt("exposure",0); initialLoss=state==null?0:state.optInt("loss",0);
        nodes=new ArrayList<>(); JSONArray nodeArray=json.optJSONArray("nodes");
        if(nodeArray!=null)for(int i=0;i<nodeArray.length();i++)nodes.add(new Node(nodeArray.optJSONObject(i)));
        timeline=strings(json.optJSONArray("timeline")); endings=json.optJSONObject("endings")==null?new JSONObject():json.optJSONObject("endings");
    }

    public static StoryScript fromJson(JSONObject json){return new StoryScript(json);}

    public Ending ending(String key){JSONObject value=endings.optJSONObject(key);if(value==null)value=endings.optJSONObject("default");return new Ending(value==null?new JSONObject():value);}

    public static final class Node {
        public final String id, chapter, scene, prompt, endingKey; public final List<Choice> choices;
        Node(JSONObject json){id=json.optString("id");chapter=json.optString("chapter");scene=json.optString("scene");prompt=json.optString("prompt","你准备怎么做？");endingKey=json.optString("endingKey");choices=new ArrayList<>();JSONArray array=json.optJSONArray("choices");if(array!=null)for(int i=0;i<array.length();i++)choices.add(new Choice(array.optJSONObject(i)));}
    }

    public static final class Choice {
        public final String id,label,feedbackTitle,feedback,nextNode; public final int relationship,evidence,exposure,loss;
        Choice(JSONObject json){id=json.optString("id");label=json.optString("label");feedbackTitle=json.optString("feedbackTitle");feedback=json.optString("feedback");nextNode=json.optString("nextNode","");JSONObject e=json.optJSONObject("effects");relationship=e==null?0:e.optInt("relationship",0);evidence=e==null?0:e.optInt("evidence",0);exposure=e==null?0:e.optInt("exposure",0);loss=e==null?0:e.optInt("loss",0);}
    }

    public static final class Ending {
        public final String title,body,tone; Ending(JSONObject json){title=json.optString("title","故事结束");body=json.optString("body");tone=json.optString("tone");}
    }

    private static List<String> strings(JSONArray array){List<String> result=new ArrayList<>();if(array!=null)for(int i=0;i<array.length();i++)result.add(array.optString(i));return result;}
}
