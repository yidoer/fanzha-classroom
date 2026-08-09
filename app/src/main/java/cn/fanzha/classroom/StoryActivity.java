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
import java.util.ArrayList;
import java.util.List;

public class StoryActivity extends AppCompatActivity {
    private FraudCase item; private StoryScript script; private StoryScript.Node node;
    private TextView title,stageLabel,status,scene,prompt,feedbackTitle,feedbackBody;
    private LinearLayout choices,feedback,gameArea,debriefContainer,storyContent,stepBar;
    private ScrollView scrollView;
    private Button continueButton;
    private String currentNodeId; private java.util.Map<String,StoryScript.Node> nodeMap;
    private int relationship,evidence,exposure,loss; private int stepCount, totalPlayable;
    private final List<String> decisions=new ArrayList<>();
    private final List<TextView> stepDots=new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);setContentView(R.layout.activity_story);
        String id=getIntent().getStringExtra("case_id");
        for(FraudCase c:CaseRepository.load(this))if(c.id.equals(id))item=c;
        script=StoryRepository.load(this).get(id);
        if(item==null||script==null){finish();return;}
        nodeMap=new java.util.LinkedHashMap<>();
        for(StoryScript.Node n:script.nodes)nodeMap.put(n.id,n);
        totalPlayable=0;for(StoryScript.Node n:script.nodes)if(!n.choices.isEmpty())totalPlayable++;
        relationship=script.initialRelationship;evidence=script.initialEvidence;
        exposure=script.initialExposure;loss=script.initialLoss;
        currentNodeId=script.nodes.get(0).id;stepCount=0;bind();render();
    }

    private void bind(){
        title=findViewById(R.id.storyTitle);stageLabel=findViewById(R.id.storyStage);
        status=findViewById(R.id.storyStatus);scene=findViewById(R.id.storyScene);
        prompt=findViewById(R.id.storyPrompt);choices=findViewById(R.id.storyChoices);
        feedback=findViewById(R.id.storyFeedback);feedbackTitle=findViewById(R.id.feedbackTitle);
        feedbackBody=findViewById(R.id.feedbackBody);continueButton=findViewById(R.id.storyContinue);
        stepBar=findViewById(R.id.storySteps);scrollView=findViewById(R.id.storyScroll);
        gameArea=findViewById(R.id.storyGameArea);debriefContainer=findViewById(R.id.storyDebrief);
        storyContent=findViewById(R.id.storyContent);
        title.setText(script.title);
        findViewById(R.id.storyBack).setOnClickListener(v->finish());
        buildStepDots();
    }

    private void buildStepDots(){
        stepBar.removeAllViews();stepDots.clear();
        for(int i=0;i<totalPlayable;i++){
            LinearLayout dotWrap=new LinearLayout(this);
            dotWrap.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
            wp.weight=1;dotWrap.setLayoutParams(wp);

            TextView dot=new TextView(this);
            int size=dp(10);
            LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(size,size);
            dot.setLayoutParams(dp2);

            GradientDrawable bg=new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(Color.rgb(217,211,200));
            dot.setBackground(bg);
            dotWrap.addView(dot);
            stepDots.add(dot);
            stepBar.addView(dotWrap);

            if(i<totalPlayable-1){
                View line=new View(this);
                line.setBackgroundColor(Color.rgb(217,211,200));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(16),(int)(1.5f * getResources().getDisplayMetrics().density));
                lp.gravity=Gravity.CENTER_VERTICAL;line.setLayoutParams(lp);
                stepBar.addView(line);
            }
        }
    }

    private void updateStepDots(){
        for(int i=0;i<stepDots.size();i++){
            GradientDrawable bg=(GradientDrawable)stepDots.get(i).getBackground();
            if(i<stepCount-1)bg.setColor(Color.rgb(30,42,79));
            else if(i==stepCount-1)bg.setColor(Color.rgb(212,160,23));
            else bg.setColor(Color.rgb(217,211,200));
        }
    }

    private void render(){
        node=nodeMap.get(currentNodeId);
        if(node==null){debrief();return;}
        gameArea.setVisibility(View.VISIBLE);
        debriefContainer.setVisibility(View.GONE);
        choices.removeAllViews();feedback.setVisibility(View.GONE);
        choices.setVisibility(View.VISIBLE);prompt.setVisibility(View.VISIBLE);
        stepCount++;updateStepDots();
        stageLabel.setText("第 "+stepCount+" 节 · "+node.chapter);
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
        boolean isEnding=node.choices.isEmpty();
        continueButton.setText(isEnding?"查看结局总结":"继续故事");
        continueButton.setOnClickListener(v->{
            if(isEnding||c.nextNode.isEmpty())debrief();
            else{currentNodeId=c.nextNode;render();}
        });
    }

    private void debrief(){
        gameArea.setVisibility(View.GONE);
        debriefContainer.setVisibility(View.VISIBLE);
        debriefContainer.removeAllViews();
        for(int i=0;i<stepDots.size();i++){
            GradientDrawable bg=(GradientDrawable)stepDots.get(i).getBackground();
            bg.setColor(Color.rgb(30,42,79));
        }
        StoryScript.Ending ending=script.ending(endingKey());
        stageLabel.setText("结局");

        LinearLayout banner=new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);banner.setGravity(Gravity.CENTER);
        banner.setPadding(0,dp(16),0,dp(24));
        TextView emoji=new TextView(this);
        emoji.setText(outcomeEmoji());emoji.setTextSize(48);emoji.setGravity(Gravity.CENTER);
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
        source.setPadding(0,dp(12),0,dp(16));source.setLineSpacing(0,1.3f);
        debriefContainer.addView(source);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,0,0,dp(32));
        MaterialButton replayBtn=new MaterialButton(this);
        replayBtn.setText("重新体验");replayBtn.setTextSize(15);
        replayBtn.setBackgroundColor(Color.rgb(30,42,79));
        replayBtn.setTextColor(Color.WHITE);replayBtn.setCornerRadius(dp(8));
        replayBtn.setOnClickListener(v->reset());
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(48),1);
        rp.setMargins(0,0,dp(8),0);replayBtn.setLayoutParams(rp);
        actions.addView(replayBtn);

        MaterialButton homeBtn=new MaterialButton(this);
        homeBtn.setText("返回首页");homeBtn.setTextSize(15);
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

    private String outcomeEmoji(){
        if(script.scam){
            if(loss==0&&evidence>=6)return "\uD83D\uDEE1\uFE0F";
            if(loss==0)return "\u26A0\uFE0F";
            if(loss<10000)return "\uD83D\uDCB8";
            return "\uD83D\uDC94";
        }
        if(relationship>=7&&evidence>=5)return "\uD83E\uDD1D";
        if(relationship<=3)return "\uD83D\uDE14";
        if(evidence<=2)return "\uD83C\uDF40";
        return "\uD83D\uDD04";
    }

    private String buildPathText(){
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

    private LinearLayout sectionCard(String title,String body){
        LinearLayout card=new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding(dp(16),dp(14),dp(16),dp(14));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0,0,0,dp(10));card.setLayoutParams(lp);
        TextView h=new TextView(this);h.setText(title);
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

    private String endingKey(){
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

    private void reset(){
        currentNodeId=script.nodes.get(0).id;stepCount=0;
        relationship=script.initialRelationship;evidence=script.initialEvidence;
        exposure=script.initialExposure;loss=script.initialLoss;
        decisions.clear();
        buildStepDots();
        render();
    }

    private int clamp(int v){return Math.max(0,Math.min(10,v));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
}
