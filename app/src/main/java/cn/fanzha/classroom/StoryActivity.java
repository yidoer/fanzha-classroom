package cn.fanzha.classroom;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StoryActivity extends AppCompatActivity {
    private FraudCase item; private StoryScript script; private StoryScript.Node node;
    private TextView title,stageLabel,status,scene,prompt,feedbackTitle,feedbackBody;
    private LinearLayout choices,feedback,gameArea,debriefContainer,stepBar;
    private ScrollView scrollView;
    private Button continueButton;
    private String currentNodeId;
    private Map<String,StoryScript.Node> nodeMap;
    private Map<String,Integer> stepsToEnding;
    private int relationship,evidence,exposure,loss;
    private int stepCount, projectedTotal;
    private final List<String> decisions=new ArrayList<>();
    private final List<TextView> stepDots=new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);setContentView(R.layout.activity_story);
        String id=getIntent().getStringExtra("case_id");
        for(FraudCase c:CaseRepository.load(this))if(c.id.equals(id))item=c;
        script=StoryRepository.load(this).get(id);
        if(item==null||script==null){finish();return;}
        nodeMap=new LinkedHashMap<>();
        for(StoryScript.Node n:script.nodes)nodeMap.put(n.id,n);
        computeStepsToEnding();
        bind();
        reset();
    }

    /** Backward BFS: shortest number of remaining decisions from each node to any ending. */
    private void computeStepsToEnding(){
        stepsToEnding=new HashMap<>();
        Map<String,List<String>> incoming=new HashMap<>();
        ArrayDeque<String> queue=new ArrayDeque<>();
        for(StoryScript.Node n:script.nodes){
            for(StoryScript.Choice c:n.choices){
                if(c.nextNode.isEmpty())continue;
                List<String> list=incoming.get(c.nextNode);
                if(list==null){list=new ArrayList<>();incoming.put(c.nextNode,list);}
                list.add(n.id);
            }
            if(n.choices.isEmpty()){stepsToEnding.put(n.id,0);queue.add(n.id);}
        }
        while(!queue.isEmpty()){
            String current=queue.poll();
            int depth=stepsToEnding.get(current);
            List<String> parents=incoming.get(current);
            if(parents==null)continue;
            for(String parent:parents){
                if(stepsToEnding.containsKey(parent))continue;
                stepsToEnding.put(parent,depth+1);
                queue.add(parent);
            }
        }
    }

    private void bind(){
        title=findViewById(R.id.storyTitle);stageLabel=findViewById(R.id.storyStage);
        status=findViewById(R.id.storyStatus);scene=findViewById(R.id.storyScene);
        prompt=findViewById(R.id.storyPrompt);choices=findViewById(R.id.storyChoices);
        feedback=findViewById(R.id.storyFeedback);feedbackTitle=findViewById(R.id.feedbackTitle);
        feedbackBody=findViewById(R.id.feedbackBody);continueButton=findViewById(R.id.storyContinue);
        stepBar=findViewById(R.id.storySteps);scrollView=findViewById(R.id.storyScroll);
        gameArea=findViewById(R.id.storyGameArea);debriefContainer=findViewById(R.id.storyDebrief);
        title.setText(script.title);
        findViewById(R.id.storyBack).setOnClickListener(v->finish());
    }

    /** Rebuilds the dot row. The projected length shrinks when a short path is taken. */
    private void renderStepBar(){
        stepBar.removeAllViews();stepDots.clear();
        int shown=Math.max(projectedTotal,stepCount);
        for(int i=0;i<shown;i++){
            TextView dot=new TextView(this);
            int size=dp(9);
            GradientDrawable bg=new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            if(i<stepCount-1)bg.setColor(Color.rgb(30,42,79));
            else if(i==stepCount-1)bg.setColor(Color.rgb(212,160,23));
            else bg.setColor(Color.rgb(217,211,200));
            dot.setBackground(bg);
            LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(size,size);
            dp2.gravity=Gravity.CENTER_VERTICAL;
            dot.setLayoutParams(dp2);stepDots.add(dot);
            stepBar.addView(dot);
            if(i<shown-1){
                View line=new View(this);
                boolean walked=i<stepCount-1;
                line.setBackgroundColor(walked?Color.rgb(30,42,79):Color.rgb(217,211,200));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(14),Math.max(1,dp(2)/2));
                lp.gravity=Gravity.CENTER_VERTICAL;
                lp.setMargins(dp(3),0,dp(3),0);
                line.setLayoutParams(lp);
                stepBar.addView(line);
            }
        }
    }

    private void render(){
        node=nodeMap.get(currentNodeId);
        if(node==null){debrief(null);return;}
        if(node.choices.isEmpty()){debrief(node);return;}

        gameArea.setVisibility(View.VISIBLE);
        debriefContainer.setVisibility(View.GONE);
        choices.removeAllViews();feedback.setVisibility(View.GONE);
        choices.setVisibility(View.VISIBLE);prompt.setVisibility(View.VISIBLE);
        stepCount++;
        Integer remaining=stepsToEnding.get(currentNodeId);
        projectedTotal=stepCount+(remaining==null?0:Math.max(0,remaining-1));
        renderStepBar();
        stageLabel.setText("第 "+stepCount+" 节 · "+node.chapter+"  ·  预计还有 "+
            Math.max(0,projectedTotal-stepCount)+" 步");
        status.setText("关系 "+relationship+"  ·  证据 "+evidence+"  ·  暴露 "+exposure+"  ·  ¥"+loss);
        scene.setText(node.scene);prompt.setText(node.prompt);
        for(StoryScript.Choice c:node.choices)addChoice(c);
        scrollView.smoothScrollTo(0,0);
    }

    private void addChoice(StoryScript.Choice c){
        TextView button=new TextView(this);button.setText(c.label);
        button.setTextSize(16);button.setTextColor(Color.rgb(24,32,42));
        button.setGravity(Gravity.CENTER_VERTICAL);button.setPadding(dp(16),dp(10),dp(16),dp(10));
        button.setBackgroundResource(R.drawable.bg_choice);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0,0,0,dp(10));button.setMinHeight(dp(62));button.setLayoutParams(p);
        button.setOnClickListener(v->choose(c));choices.addView(button);
    }

    private void choose(StoryScript.Choice c){
        relationship=clamp(relationship+c.relationship);evidence=clamp(evidence+c.evidence);
        exposure=clamp(exposure+c.exposure);loss=Math.max(0,loss+c.loss);
        decisions.add(node.chapter+"："+c.label);
        choices.setVisibility(View.GONE);prompt.setVisibility(View.GONE);
        feedback.setVisibility(View.VISIBLE);
        feedbackTitle.setText(c.feedbackTitle);feedbackBody.setText(c.feedback);

        StoryScript.Node target=c.nextNode.isEmpty()?null:nodeMap.get(c.nextNode);
        boolean endsHere=target==null||target.choices.isEmpty();
        continueButton.setText(endsHere?"查看结局总结":"继续故事");
        continueButton.setOnClickListener(v->{
            if(endsHere){
                if(target!=null){currentNodeId=c.nextNode;debrief(target);}
                else debrief(null);
            }else{currentNodeId=c.nextNode;render();}
        });
        scrollView.post(()->scrollView.smoothScrollTo(0,feedback.getTop()));
    }

    /** Summary page. Reached from every ending, including 1-step early exits. */
    private void debrief(StoryScript.Node endingNode){
        gameArea.setVisibility(View.GONE);
        debriefContainer.setVisibility(View.VISIBLE);
        debriefContainer.removeAllViews();
        projectedTotal=stepCount;renderStepBar();
        for(TextView dot:stepDots){
            GradientDrawable bg=(GradientDrawable)dot.getBackground();
            bg.setColor(Color.rgb(30,42,79));
        }
        String key=endingKey(endingNode);
        StoryScript.Ending ending=script.ending(key);
        stageLabel.setText("结局 · "+stepCount+" 步走完");

        LinearLayout banner=new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);banner.setGravity(Gravity.CENTER);
        banner.setPadding(0,dp(14),0,dp(20));
        TextView emoji=new TextView(this);
        emoji.setText(outcomeEmoji(key));emoji.setTextSize(46);emoji.setGravity(Gravity.CENTER);
        banner.addView(emoji);
        TextView ot=new TextView(this);
        ot.setText(ending.title);ot.setTextSize(22);
        ot.setTextColor(Color.rgb(30,42,79));ot.setTypeface(null,Typeface.BOLD);
        ot.setGravity(Gravity.CENTER);ot.setPadding(0,dp(8),0,dp(4));
        banner.addView(ot);
        TextView ob=new TextView(this);
        ob.setText(ending.body);ob.setTextSize(15);ob.setTextColor(Color.rgb(92,92,92));
        ob.setGravity(Gravity.CENTER);ob.setLineSpacing(0,1.3f);
        banner.addView(ob);
        debriefContainer.addView(banner);

        if(endingNode!=null&&!endingNode.scene.isEmpty())
            debriefContainer.addView(sectionCard("你的结局",endingNode.scene));
        debriefContainer.addView(sectionCard("你走过的路",buildPathText()));
        debriefContainer.addView(sectionCard("真相揭晓",script.reveal));
        debriefContainer.addView(sectionCard("伏笔回收",script.clueReveal));
        debriefContainer.addView(sectionCard("完整始末",buildTimelineText()));

        LinearLayout lessonCard=sectionCard("写在最后",script.lesson);
        lessonCard.setBackgroundResource(R.drawable.bg_card_accent);
        debriefContainer.addView(lessonCard);

        LinearLayout statsRow=new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);statsRow.setPadding(0,dp(8),0,dp(8));
        statsRow.addView(statChip("关系",relationship));
        statsRow.addView(statChip("证据",evidence));
        statsRow.addView(statChip("暴露",exposure));
        statsRow.addView(statChip("损失 ¥"+loss,loss>0?0:1));
        debriefContainer.addView(statsRow);

        TextView source=new TextView(this);
        source.setText("素材性质："+item.materialType+"\n参考来源："+item.sourceName+" · "+item.sourceDate);
        source.setTextSize(12);source.setTextColor(Color.rgb(150,150,150));
        source.setPadding(0,dp(10),0,dp(14));source.setLineSpacing(0,1.3f);
        debriefContainer.addView(source);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,0,0,dp(28));
        MaterialButton replayBtn=new MaterialButton(this);
        replayBtn.setText("换个选择再走一遍");replayBtn.setTextSize(14);
        replayBtn.setBackgroundColor(Color.rgb(30,42,79));
        replayBtn.setTextColor(Color.WHITE);replayBtn.setCornerRadius(dp(8));
        replayBtn.setOnClickListener(v->{reset();});
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(48),1);
        rp.setMargins(0,0,dp(8),0);replayBtn.setLayoutParams(rp);
        actions.addView(replayBtn);

        MaterialButton homeBtn=new MaterialButton(this);
        homeBtn.setText("返回首页");homeBtn.setTextSize(14);
        homeBtn.setBackgroundColor(Color.rgb(245,241,235));
        homeBtn.setTextColor(Color.rgb(30,42,79));homeBtn.setCornerRadius(dp(8));
        homeBtn.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.rgb(217,211,200)));
        homeBtn.setStrokeWidth(dp(1));
        homeBtn.setOnClickListener(v->finish());
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(0,dp(48),1);
        homeBtn.setLayoutParams(hp);
        actions.addView(homeBtn);
        debriefContainer.addView(actions);

        scrollView.smoothScrollTo(0,0);
        StoryContextCompressor.save(this,item,decisions,relationship,evidence,exposure,loss);
    }

    /** Ending node id decides the summary; stats are only the fallback. */
    private String endingKey(StoryScript.Node endingNode){
        if(endingNode!=null){
            String id=endingNode.id;
            if(id.contains("ending_good"))return "best";
            if(id.contains("ending_exposed"))return "exposed";
            if(id.contains("ending_partial"))return "partial_loss";
            if(id.contains("ending_major"))return "major_loss";
            if(id.contains("ending_estranged"))return "estranged";
            if(id.contains("ending_unguarded"))return "unguarded";
            if(id.contains("ending_mixed"))return "mixed";
        }
        if(script.scam){
            if(loss==0&&evidence>=6)return "best";
            if(loss==0)return "exposed";
            if(loss<10000)return "partial_loss";
            return "major_loss";
        }
        if(relationship>=7&&evidence>=5)return "best";
        if(relationship<=3)return "estranged";
        if(evidence<=2)return "unguarded";
        return "mixed";
    }

    private String outcomeEmoji(String key){
        switch(key){
            case "best": return script.scam?"\uD83D\uDEE1\uFE0F":"\uD83E\uDD1D";
            case "exposed": return "\u26A0\uFE0F";
            case "partial_loss": return "\uD83D\uDCB8";
            case "major_loss": return "\uD83D\uDC94";
            case "estranged": return "\uD83D\uDE14";
            case "unguarded": return "\uD83C\uDF40";
            default: return "\uD83D\uDD04";
        }
    }

    private String buildPathText(){
        if(decisions.isEmpty())return "（没有做出任何选择）";
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<decisions.size();i++)
            sb.append(i+1).append(". ").append(decisions.get(i)).append("\n");
        return sb.toString().trim();
    }

    private String buildTimelineText(){
        StringBuilder sb=new StringBuilder();
        for(int i=0;i<script.timeline.size();i++)
            sb.append(i+1).append(". ").append(script.timeline.get(i)).append("\n");
        return sb.toString().trim();
    }

    private LinearLayout sectionCard(String heading,String body){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16),dp(14),dp(16),dp(14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(10));card.setLayoutParams(lp);
        TextView h=new TextView(this);h.setText(heading);
        h.setTextSize(17);h.setTextColor(Color.rgb(30,42,79));
        h.setTypeface(null,Typeface.BOLD);h.setPadding(0,0,0,dp(8));
        card.addView(h);
        TextView b=new TextView(this);b.setText(body);
        b.setTextSize(14);b.setTextColor(Color.rgb(60,60,60));
        b.setLineSpacing(0,1.35f);
        card.addView(b);
        return card;
    }

    private TextView statChip(String label,int value){
        TextView chip=new TextView(this);
        chip.setText(label+" "+value);chip.setTextSize(12);
        chip.setTextColor(Color.rgb(92,92,92));
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setPadding(dp(10),dp(6),dp(10),dp(6));chip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0,0,dp(8),0);chip.setLayoutParams(cp);
        return chip;
    }

    private void reset(){
        currentNodeId=script.nodes.get(0).id;stepCount=0;
        relationship=script.initialRelationship;evidence=script.initialEvidence;
        exposure=script.initialExposure;loss=script.initialLoss;
        decisions.clear();
        Integer remaining=stepsToEnding.get(currentNodeId);
        projectedTotal=remaining==null?1:Math.max(1,remaining);
        render();
    }

    private int clamp(int v){return Math.max(0,Math.min(10,v));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}