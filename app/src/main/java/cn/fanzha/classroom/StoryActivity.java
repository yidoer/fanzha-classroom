package cn.fanzha.classroom;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class StoryActivity extends AppCompatActivity {
    private FraudCase item; private StoryScript script; private StoryScript.Node node;
    private TextView title,stageLabel,status,scene,prompt,feedbackTitle,feedbackBody;
    private LinearLayout choices,feedback,gameArea,debriefContainer,stepBar;
    private ScrollView scrollView;
    private Button continueButton,jumpButton;
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
        item.attachScript(script);
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

    private int endingTotal(){
        int total=0;
        for(StoryScript.Node n:script.nodes)if(n.choices.isEmpty())total++;
        return total;
    }

    private void bind(){
        title=findViewById(R.id.storyTitle);stageLabel=findViewById(R.id.storyStage);
        status=findViewById(R.id.storyStatus);scene=findViewById(R.id.storyScene);
        prompt=findViewById(R.id.storyPrompt);choices=findViewById(R.id.storyChoices);
        feedback=findViewById(R.id.storyFeedback);feedbackTitle=findViewById(R.id.feedbackTitle);
        feedbackBody=findViewById(R.id.feedbackBody);continueButton=findViewById(R.id.storyContinue);
        stepBar=findViewById(R.id.storySteps);scrollView=findViewById(R.id.storyScroll);
        gameArea=findViewById(R.id.storyGameArea);debriefContainer=findViewById(R.id.storyDebrief);
        jumpButton=findViewById(R.id.storyJump);
        title.setText(script.title);
        findViewById(R.id.storyBack).setOnClickListener(v->finish());
        jumpButton.setOnClickListener(v->showJumpDialog());
    }

    /** Lets the player hop back to any decision node they have already stood on. */
    private void showJumpDialog(){
        List<StoryProgress.Checkpoint> saved=StoryProgress.checkpoints(this,script.id);
        if(saved.isEmpty()){
            new AlertDialog.Builder(this).setTitle("进度跳转")
                .setMessage("你还没有走过任何节点。先做出一次选择，之后就可以随时跳回来重走。")
                .setPositiveButton("知道了",null).show();
            return;
        }
        final List<StoryProgress.Checkpoint> options=new ArrayList<>(saved);
        java.util.Collections.sort(options,(a,b)->Integer.compare(a.step,b.step));
        String[] labels=new String[options.size()];
        for(int i=0;i<options.size();i++){
            StoryProgress.Checkpoint c=options.get(i);
            labels[i]="第 "+c.step+" 节 · "+c.chapter;
        }
        new AlertDialog.Builder(this)
            .setTitle("跳到走过的节点")
            .setItems(labels,(d,which)->jumpTo(options.get(which)))
            .setNegativeButton("取消",null).show();
    }

    private void jumpTo(StoryProgress.Checkpoint c){
        if(!nodeMap.containsKey(c.nodeId)){
            new AlertDialog.Builder(this).setMessage("这个节点在新版剧情里已经改写，无法跳转。")
                .setPositiveButton("知道了",null).show();
            return;
        }
        currentNodeId=c.nodeId;
        relationship=c.relationship;evidence=c.evidence;exposure=c.exposure;loss=c.loss;
        decisions.clear();decisions.addAll(c.decisions);
        stepCount=Math.max(0,c.step-1);
        render();
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
            if(i<stepCount-1)bg.setColor(c(R.color.progress_done));
            else if(i==stepCount-1)bg.setColor(c(R.color.progress_current));
            else bg.setColor(c(R.color.progress_todo));
            dot.setBackground(bg);
            LinearLayout.LayoutParams dp2=new LinearLayout.LayoutParams(size,size);
            dp2.gravity=Gravity.CENTER_VERTICAL;
            dot.setLayoutParams(dp2);stepDots.add(dot);
            stepBar.addView(dot);
            if(i<shown-1){
                View line=new View(this);
                boolean walked=i<stepCount-1;
                line.setBackgroundColor(walked?c(R.color.progress_done):c(R.color.progress_todo));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(16),dp(2));
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
        StoryProgress.saveCheckpoint(this,script.id,node.id,node.chapter,stepCount,
            relationship,evidence,exposure,loss,decisions);
        Integer remaining=stepsToEnding.get(currentNodeId);
        projectedTotal=stepCount+(remaining==null?0:Math.max(0,remaining-1));
        renderStepBar();
        stageLabel.setText(getString(R.string.story_stage_fmt,stepCount,node.chapter,
            Math.max(0,projectedTotal-stepCount)));
        status.setText(getString(R.string.story_status_fmt,relationship,evidence,exposure,loss));
        paintStatusChip();
        scene.setText(node.scene);prompt.setText(node.prompt);
        for(StoryScript.Choice c:node.choices)addChoice(c);
        scrollView.smoothScrollTo(0,0);
    }

    private void addChoice(StoryScript.Choice c){
        TextView button=new TextView(this);button.setText(c.label);
        button.setTextSize(16);button.setTextColor(c(R.color.text_primary));
        button.setGravity(Gravity.CENTER_VERTICAL);button.setPadding(dp(16),dp(10),dp(16),dp(10));
        button.setBackgroundResource(R.drawable.bg_choice);
        button.setLineSpacing(0,1.2f);
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
            bg.setColor(c(R.color.progress_done));
        }
        if(endingNode!=null)StoryProgress.markUnlocked(this,script.id,endingNode.id);
        String key=endingKey(endingNode);
        StoryScript.Ending ending=script.ending(key);
        stageLabel.setText(getString(R.string.story_stage_ending_fmt,stepCount));

        LinearLayout banner=new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);banner.setGravity(Gravity.CENTER);
        banner.setPadding(0,dp(14),0,dp(20));
        ImageView mark=new ImageView(this);
        mark.setImageResource(outcomeIcon(key));
        mark.setImageTintList(android.content.res.ColorStateList.valueOf(c(outcomeColor(key))));
        GradientDrawable disc=new GradientDrawable();
        disc.setShape(GradientDrawable.OVAL);disc.setColor(c(outcomeContainer(key)));
        mark.setBackground(disc);mark.setPadding(dp(16),dp(16),dp(16),dp(16));
        LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(dp(72),dp(72));
        mark.setLayoutParams(mp);
        mark.setContentDescription(outcomeLabel(key));
        banner.addView(mark);
        TextView badge=new TextView(this);
        badge.setText(outcomeLabel(key));badge.setTextSize(13);
        badge.setTypeface(null,Typeface.BOLD);
        badge.setTextColor(c(outcomeColor(key)));
        GradientDrawable pill=new GradientDrawable();
        pill.setCornerRadius(dp(999));pill.setColor(c(outcomeContainer(key)));
        badge.setBackground(pill);badge.setPadding(dp(12),dp(5),dp(12),dp(5));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(0,dp(10),0,0);badge.setLayoutParams(bp);
        banner.addView(badge);
        TextView ot=new TextView(this);
        ot.setText(ending.title);ot.setTextSize(22);
        ot.setTextColor(c(R.color.text_primary));ot.setTypeface(null,Typeface.BOLD);
        ot.setGravity(Gravity.CENTER);ot.setPadding(0,dp(8),0,dp(4));
        banner.addView(ot);
        TextView ob=new TextView(this);
        ob.setText(ending.body);ob.setTextSize(15);ob.setTextColor(c(R.color.text_secondary));
        ob.setGravity(Gravity.CENTER);ob.setLineSpacing(0,1.3f);
        banner.addView(ob);
        debriefContainer.addView(banner);

        int unlocked=StoryProgress.unlockedCount(this,script.id);
        int total=endingTotal();
        LinearLayout collect=sectionCard("结局收集",
            "本篇共有 "+total+" 种结局，你已经解锁 "+unlocked+" 种。"+
            (unlocked<total?"用右上角的进度跳转回到任意走过的节点，可以换一条路看看另一个结果。"
                           :"你已经把这篇故事的每一种可能都走过一遍了。"));
        collect.setBackgroundResource(R.drawable.bg_card);
        debriefContainer.addView(collect);

        if(endingNode!=null&&!endingNode.scene.isEmpty())
            debriefContainer.addView(sectionCard("你的结局",endingNode.scene));
        debriefContainer.addView(sectionCard("你走过的路",buildPathText()));
        debriefContainer.addView(sectionCard("真相揭晓",script.reveal));
        debriefContainer.addView(sectionCard("伏笔回收",script.clueReveal));
        debriefContainer.addView(sectionCard("完整始末",buildTimelineText()));

        LinearLayout lessonCard=sectionCard("写在最后",script.lesson);
        lessonCard.setBackgroundResource(R.drawable.bg_card_accent);
        debriefContainer.addView(lessonCard);

        LinearLayout creed=sectionCard("为什么要学这些",creedText());
        creed.setBackgroundResource(R.drawable.bg_card_accent);
        debriefContainer.addView(creed);

        LinearLayout statsRow=new LinearLayout(this);
        statsRow.setOrientation(LinearLayout.HORIZONTAL);statsRow.setPadding(0,dp(8),0,dp(8));
        statsRow.addView(statChip("关系",relationship));
        statsRow.addView(statChip("证据",evidence));
        statsRow.addView(statChip("暴露",exposure));
        statsRow.addView(statChip("损失 ¥"+loss,-1));
        debriefContainer.addView(statsRow);

        TextView source=new TextView(this);
        source.setText(getString(R.string.story_source_fmt,item.materialType,item.sourceName,item.sourceDate));
        source.setTextSize(12);source.setTextColor(c(R.color.text_tertiary));
        source.setPadding(0,dp(10),0,dp(14));source.setLineSpacing(0,1.3f);
        debriefContainer.addView(source);

        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);actions.setPadding(0,0,0,dp(12));
        MaterialButton replayBtn=new MaterialButton(this);
        replayBtn.setText("从头再走一遍");replayBtn.setTextSize(14);
        replayBtn.setBackgroundColor(c(R.color.brand));
        replayBtn.setTextColor(c(R.color.text_on_brand));replayBtn.setCornerRadius(dp(8));
        replayBtn.setOnClickListener(v->reset());
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,dp(48),1);
        rp.setMargins(0,0,dp(8),0);replayBtn.setLayoutParams(rp);
        actions.addView(replayBtn);

        MaterialButton jumpBtn=new MaterialButton(this);
        jumpBtn.setText("跳到某个节点");jumpBtn.setTextSize(14);
        jumpBtn.setBackgroundColor(c(R.color.bg_elevated));
        jumpBtn.setTextColor(c(R.color.brand));jumpBtn.setCornerRadius(dp(8));
        jumpBtn.setStrokeColor(android.content.res.ColorStateList.valueOf(c(R.color.border_subtle)));
        jumpBtn.setStrokeWidth(dp(1));
        jumpBtn.setOnClickListener(v->showJumpDialog());
        LinearLayout.LayoutParams jp=new LinearLayout.LayoutParams(0,dp(48),1);
        jumpBtn.setLayoutParams(jp);
        actions.addView(jumpBtn);
        debriefContainer.addView(actions);

        MaterialButton homeBtn=new MaterialButton(this);
        homeBtn.setText("返回首页");homeBtn.setTextSize(14);
        homeBtn.setBackgroundColor(c(R.color.bg_elevated));
        homeBtn.setTextColor(c(R.color.brand));homeBtn.setCornerRadius(dp(8));
        homeBtn.setStrokeColor(android.content.res.ColorStateList.valueOf(c(R.color.border_subtle)));
        homeBtn.setStrokeWidth(dp(1));
        homeBtn.setOnClickListener(v->finish());
        LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,dp(48));
        hp.setMargins(0,0,0,dp(28));
        homeBtn.setLayoutParams(hp);
        debriefContainer.addView(homeBtn);

        scrollView.smoothScrollTo(0,0);
        StoryContextCompressor.save(this,item,decisions,relationship,evidence,exposure,loss);
    }

    /** The closing note: vigilance exists so that sincerity keeps its value. */
    private String creedText(){
        if(script.scam)
            return "学会识破，不是为了从此谁都不信。骗子之所以能得手，正是因为他们借用了真诚、孝顺、体面和热心——"+
                   "这些本该被珍惜的东西，被他们当成了撬开门的把手。"+
                   "我们练习验证，是为了让骗子拿不走这些美德的名义，让真诚在这个世界上继续值钱。";
        return "这一篇里没有骗子，只有一次可能被误伤的信任。反诈的目标从来不是把心门关上，"+
               "而是学会用不伤人的方式确认——把核实做成流程，而不是做成质问。"+
               "能一边保护自己、一边不让身边的人寒心，才是这门功课真正的满分。";
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

    /** Outcome mark: vector icon + semantic colour + text label (never colour alone). */
    private int outcomeIcon(String key){
        switch(key){
            case "best": return script.scam?R.drawable.ic_outcome_shield_check
                                           :R.drawable.ic_outcome_handshake;
            case "exposed": return R.drawable.ic_outcome_alert;
            case "partial_loss": return R.drawable.ic_outcome_partial;
            case "major_loss": return R.drawable.ic_outcome_major;
            case "estranged": return R.drawable.ic_outcome_rift;
            case "unguarded": return R.drawable.ic_outcome_naive;
            default: return R.drawable.ic_outcome_mixed;
        }
    }

    private int outcomeColor(String key){
        switch(key){
            case "best": return R.color.state_safe;
            case "exposed": return R.color.state_caution;
            case "partial_loss": return R.color.state_loss;
            case "major_loss": return R.color.state_critical;
            case "estranged": return R.color.state_rift;
            case "unguarded": return R.color.state_naive;
            default: return R.color.brand_muted;
        }
    }

    private int outcomeContainer(String key){
        switch(key){
            case "best": return R.color.state_safe_container;
            case "exposed": return R.color.state_caution_container;
            case "partial_loss": return R.color.state_loss_container;
            case "major_loss": return R.color.state_critical_container;
            case "estranged": return R.color.state_rift_container;
            case "unguarded": return R.color.state_naive_container;
            default: return R.color.brand_subtle;
        }
    }

    private String outcomeLabel(String key){
        switch(key){
            case "best": return script.scam?"安全脱身":"信任守住";
            case "exposed": return "信息外泄";
            case "partial_loss": return "部分损失";
            case "major_loss": return "重大损失";
            case "estranged": return "关系受损";
            case "unguarded": return "毫无防备";
            default: return "结果参半";
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
        h.setTextSize(17);h.setTextColor(c(R.color.brand));
        h.setTypeface(null,Typeface.BOLD);h.setPadding(0,0,0,dp(8));
        card.addView(h);
        TextView b=new TextView(this);b.setText(body);
        b.setTextSize(15);b.setTextColor(c(R.color.text_secondary));
        b.setLineSpacing(0,1.35f);
        card.addView(b);
        return card;
    }

    /** value<0 means the label already carries the number (e.g. "损失 ¥1000"). */
    private TextView statChip(String label,int value){
        TextView chip=new TextView(this);
        chip.setText(value<0?label:getString(R.string.stat_chip_fmt,label,value));chip.setTextSize(12);
        chip.setTextColor(c(R.color.text_secondary));
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

    private int c(int res){return ContextCompat.getColor(this,res);}
    private int clamp(int v){return Math.max(0,Math.min(10,v));}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    /** The situation bar escalates from brand blue to amber to red as the player bleeds out. */
    private void paintStatusChip(){
        int background,color;
        if(loss>0){background=R.drawable.bg_chip_critical;color=R.color.state_critical;}
        else if(exposure>=6){background=R.drawable.bg_chip_caution;color=R.color.state_caution;}
        else{background=R.drawable.bg_chip;color=R.color.brand;}
        status.setBackgroundResource(background);
        status.setTextColor(c(color));
    }
}