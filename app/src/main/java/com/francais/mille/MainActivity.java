package com.francais.mille;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Native, local-only vocabulary trainer. No permissions, network or external dependencies. */
public final class MainActivity extends Activity {
    private static final int BLUE=Color.rgb(20,20,20), INK=Color.rgb(24,24,24), MUTED=Color.rgb(108,108,108),
        BACK=Color.WHITE, WHITE=Color.WHITE, PALE=Color.rgb(245,245,245), GREEN=Color.rgb(18,110,68), RED=Color.rgb(175,38,38);
    private static final String[] LESSON_NAMES={
        "はじめての会話", "数字・毎日の動詞", "人・家族・身体", "家と暮らし", "食べ物・買い物",
        "日時・天気・自然", "街・旅行", "色・形容詞・副詞", "学校・仕事・趣味", "発展する動詞・表現"};
    private final ArrayList<Word> words=new ArrayList<>();
    private final Set<Integer> seen=new HashSet<>(), known=new HashSet<>(), review=new HashSet<>();
    private final Map<Integer,Integer> wrongCounts=new HashMap<>();
    private JSONArray history=new JSONArray();
    private final Map<Integer,Integer> legacyIds=new HashMap<>();
    private boolean migrateOldProgress=false;
    private SharedPreferences prefs;
    private LinearLayout shell, body;
    private MediaPlayer player;
    private int screen=0, cardIndex=0, questionIndex=0, correctCount=0, startLesson=1, endLesson=1;
    private boolean revealed=false, reviewing=false, answered=false;
    private final ArrayList<String> testAnswers=new ArrayList<>();
    private final ArrayList<Integer> lastWrongIds=new ArrayList<>();
    private boolean wrongOnlyTest=false;
    private ArrayList<Integer> flashIds=new ArrayList<>(), testIds=new ArrayList<>();
    private int[] choices=new int[4];
    private final Random random=new Random();
    private Spinner startSpinner,endSpinner;
    private SeekBar countSeek;
    private CheckBox allCheck;
    private long quizStart;

    private static class Word {
        int id, lesson;
        String fr,ja;
        Word(JSONObject obj) throws Exception {id=obj.getInt("id");lesson=obj.getInt("lesson");fr=obj.getString("fr");ja=obj.getString("ja");}
    }

    @Override public void onCreate(Bundle bundle){
        super.onCreate(bundle);
        getWindow().setStatusBarColor(Color.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        getWindow().setNavigationBarColor(BACK);
        prefs=getSharedPreferences("learning_v1",MODE_PRIVATE);
        try {
            InputStream in=getAssets().open("words.json");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)bytes.write(buf,0,n);in.close();
            JSONArray arr=new JSONArray(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
            for(int i=0;i<arr.length();i++)words.add(new Word(arr.getJSONObject(i)));
            if(words.size()!=1000)throw new IllegalStateException("Expected 1000 words");
            readProgress();if(migrateOldProgress)saveProgress();showHome();
        }catch(Exception e){TextView t=new TextView(this);t.setPadding(dp(24),dp(30),dp(24),dp(30));t.setText("単語データを読み込めませんでした。\n"+e.getMessage());setContentView(t);}
    }

    private void readProgress(){
        try {
            JSONObject obj=new JSONObject(prefs.getString("state","{}"));
            migrateOldProgress=obj.optInt("catalog_version",1)<2;
            if(migrateOldProgress){
                try{
                    InputStream f=getAssets().open("migration.json");
                    ByteArrayOutputStream buff=new ByteArrayOutputStream();byte[] b=new byte[4096];int count;
                    while((count=f.read(b))!=-1)buff.write(b,0,count);f.close();
                    JSONObject mapping=new JSONObject(new String(buff.toByteArray(),StandardCharsets.UTF_8));
                    for(int i=1;i<=1000;i++)legacyIds.put(i,mapping.optInt(String.valueOf(i),0));
                }catch(Exception ignored){migrateOldProgress=false;}
            }
            readSet(obj.optJSONArray("seen"),seen);readSet(obj.optJSONArray("known"),known);readSet(obj.optJSONArray("review"),review);
            JSONObject errors=obj.optJSONObject("mistakes");
            if(errors!=null){java.util.Iterator<String> it=errors.keys();while(it.hasNext()){String k=it.next();{int old=Integer.parseInt(k),id=upgradeId(old);if(id>0)wrongCounts.put(id,errors.optInt(k));}}}
            history=obj.optJSONArray("history");if(history==null)history=new JSONArray();
        }catch(Exception ignored) { /* A damaged local record must not prevent study. */ }
    }
    private int upgradeId(int id){return migrateOldProgress?legacyIds.getOrDefault(id,0):id;}
    private void readSet(JSONArray a,Set<Integer> target){if(a==null)return;for(int i=0;i<a.length();i++){int id=upgradeId(a.optInt(i));if(id>=1&&id<=1000)target.add(id);}}
    private JSONArray toArray(Set<Integer> ids){JSONArray a=new JSONArray();ArrayList<Integer> sorted=new ArrayList<>(ids);Collections.sort(sorted);for(int id:sorted)a.put(id);return a;}
    private void saveProgress(){
        try{
            JSONObject out=new JSONObject();out.put("seen",toArray(seen));out.put("known",toArray(known));out.put("review",toArray(review));
            JSONObject mistakes=new JSONObject();for(Map.Entry<Integer,Integer> e:wrongCounts.entrySet())mistakes.put(String.valueOf(e.getKey()),e.getValue());
            out.put("mistakes",mistakes);out.put("history",history);out.put("catalog_version",2);
            prefs.edit().putString("state",out.toString()).apply();
        }catch(Exception ignored){}
    }

    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private GradientDrawable outline(int color,int border){GradientDrawable d=bg(color,18);d.setStroke(dp(1),border);return d;}
    private TextView text(String content,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(content);t.setTextColor(color);t.setTextSize(size);t.setIncludeFontPadding(true);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private void pad(View v,int x,int y){v.setPadding(dp(x),dp(y),dp(x),dp(y));}
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout row(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);return r;}
    private void gap(LinearLayout p,int h){View space=new View(this);p.addView(space,new LinearLayout.LayoutParams(1,dp(h)));}
    private LinearLayout.LayoutParams weight(){return new LinearLayout.LayoutParams(0,-2,1);}
    private void spaceEnd(View v){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)v.getLayoutParams();lp.bottomMargin=dp(10);v.setLayoutParams(lp);}
    private void add(LinearLayout p,View child){p.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    private Button button(String label,boolean primary,Runnable action){
        Button b=new Button(this);b.setAllCaps(false);b.setText(label);b.setTextSize(15);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(primary?WHITE:INK);b.setBackground(outline(primary?INK:PALE,primary?INK:Color.rgb(227,227,227)));
        b.setGravity(Gravity.CENTER);b.setPadding(dp(16),dp(11),dp(16),dp(11));b.setMinHeight(dp(52));
        b.setOnClickListener(v->action.run());return b;
    }
    private void heading(String title,String subtitle){
        TextView a=text(title,22,INK,true);add(body,a);
        if(subtitle!=null){TextView b=text(subtitle,13,MUTED,false);add(body,b);}gap(body,14);
    }
    private LinearLayout panel(){LinearLayout v=vertical();pad(v,18,18);v.setBackground(bg(WHITE,19));add(body,v);return v;}
    private void mount(String title,int destination){
        screen=destination;
        shell=vertical();shell.setBackgroundColor(BACK);
        LinearLayout top=row();top.setBackgroundColor(WHITE);pad(top,18,13);
        TextView titleText=text(title,18,INK,true);top.addView(titleText,new LinearLayout.LayoutParams(0,-2,1));
        if(destination!=0){TextView home=text("ホーム  ⌂",14,INK,true);pad(home,10,8);home.setOnClickListener(v->showHome());top.addView(home);}
        add(shell,top);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setClipToPadding(false);
        body=vertical();pad(body,18,20);sc.addView(body);
        shell.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(shell);
    }
    private int countKnown(int lesson){int n=0;for(int id:known)if((id-1)/100+1==lesson)n++;return n;}
    private void showHome(){stopAudio();mount("フランス語1000",0);
        LinearLayout hero=panel();hero.setBackground(bg(PALE,20));
        TextView title=text("Bonjour !",25,INK,true);add(hero,title);
        TextView sub=text("毎日少しずつ、フランス語を身につけよう。",13,MUTED,false);add(hero,sub);gap(hero,14);
        TextView count=text("覚えた単語  "+known.size()+" / 1000",22,INK,true);add(hero,count);
        TextView rest=text("学習した単語 "+seen.size()+"   ·   復習待ち "+review.size()+"   ·   テスト "+history.length()+"回",12,MUTED,false);add(hero,rest);
        gap(body,16);
        add(body,button("▶  フラッシュカードを始める",true,()->showLessonPicker(false)));
        gap(body,10);add(body,button("✎  ランダムテスト",false,this::showTestSetup));
        gap(body,10);add(body,button("↺  間違えた単語を復習（"+review.size()+"）",false,this::startReview));
        gap(body,10);add(body,button("✎  間違えた単語だけテスト（"+review.size()+"）",false,()->startWrongTest(false)));
        gap(body,10);add(body,button("▤  学習記録",false,this::showStats));
        gap(body,26);heading("100語ずつ学ぶ", "やさしい順に10レッスン。タップしてすぐ学習できます。");
        for(int l=1;l<=10;l++){
            final int lesson=l;
            LinearLayout item=panel();
            LinearLayout r=row();TextView name=text(String.format(Locale.JAPAN,"%02d  %s",l,LESSON_NAMES[l-1]),16,INK,true);r.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            TextView progress=text(countKnown(l)+"/100",13,BLUE,true);r.addView(progress);add(item,r);
            gap(item,8);TextView range=text(((l-1)*100+1)+" 〜 "+(l*100)+" 語   •   カードを開く →",12,MUTED,false);add(item,range);
            item.setOnClickListener(v->startLesson(lesson));gap(body,10);
        }
        gap(body,6);
        TextView note=text("全1000語と発音音声を端末内に収録。通信・会員登録・課金は不要です。",12,MUTED,false);add(body,note);
    }
    private void showLessonPicker(boolean forTest){mount("レッスンを選ぶ",4);heading("100語ずつ学習","まずは最初の100語から。後のレッスンも自由に選べます。");
        for(int l=1;l<=10;l++){
            int lesson=l;LinearLayout v=panel();TextView title=text("Lesson "+l+"   "+LESSON_NAMES[l-1],18,INK,true);add(v,title);
            TextView meta=text(((l-1)*100+1)+"–"+(l*100)+"語    覚えた "+countKnown(l)+"/100",13,MUTED,false);add(v,meta);
            v.setOnClickListener(w->startLesson(lesson));gap(body,12);
        }
    }
    private void startLesson(int lesson){
        reviewing=false;flashIds=new ArrayList<>();for(int i=(lesson-1)*100+1;i<=lesson*100;i++)flashIds.add(i);
        cardIndex=0;revealed=false;showFlash();
    }
    private void startReview(){
        if(review.isEmpty()){Toast.makeText(this,"復習待ちの単語はありません",Toast.LENGTH_SHORT).show();return;}
        reviewing=true;flashIds=new ArrayList<>(review);
        flashIds.sort((a,b)->Integer.compare(wrongCounts.getOrDefault(b,0),wrongCounts.getOrDefault(a,0)));
        cardIndex=0;revealed=false;showFlash();
    }
    private void showFlash(){
        if(flashIds.isEmpty()){showHome();return;}
        if(cardIndex<0)cardIndex=0;if(cardIndex>=flashIds.size())cardIndex=flashIds.size()-1;
        int id=flashIds.get(cardIndex);Word w=words.get(id-1);seen.add(id);saveProgress();
        mount(reviewing?"苦手単語の復習":"フラッシュカード",1);
        TextView counter=text((cardIndex+1)+" / "+flashIds.size()+"     •     "+(reviewing?"復習カード":"Lesson "+w.lesson),14,MUTED,true);add(body,counter);gap(body,13);
        LinearLayout card=vertical();card.setGravity(Gravity.CENTER);pad(card,20,40);card.setMinimumHeight(dp(295));card.setBackground(outline(WHITE,Color.rgb(228,228,228)));add(body,card);
        TextView word=text(w.fr,33,INK,true);word.setGravity(Gravity.CENTER);add(card,word);gap(card,20);
        TextView meaning=text(revealed?w.ja:"タップして日本語を表示",revealed?23:15,revealed?INK:MUTED,revealed);
        meaning.setGravity(Gravity.CENTER);add(card,meaning);gap(card,14);
        if(reviewing){TextView prompt=text("過去の間違い "+wrongCounts.getOrDefault(id,0)+"回",12,MUTED,false);prompt.setGravity(Gravity.CENTER);add(card,prompt);}
        card.setOnClickListener(v->{revealed=!revealed;meaning.setText(revealed?w.ja:"タップして日本語を表示");meaning.setTextSize(revealed?23:15);meaning.setTextColor(revealed?INK:MUTED);});
        gap(body,15);add(body,button("🔊  もう一度発音を聞く",false,()->playAudio(id)));gap(body,14);
        LinearLayout nav=row();Button prev=button("← 前へ",false,()->moveCard(-1));Button next=button("次へ →",true,()->moveCard(1));
        nav.addView(prev,weight());View sp=new View(this);nav.addView(sp,new LinearLayout.LayoutParams(dp(9),1));nav.addView(next,weight());add(body,nav);
        gap(body,14);LinearLayout actions=row();
        Button later=button("まだ覚えていない",false,()->markCard(false));Button yes=button("✓ 覚えた",true,()->markCard(true));
        actions.addView(later,weight());View mid=new View(this);actions.addView(mid,new LinearLayout.LayoutParams(dp(9),1));actions.addView(yes,weight());add(body,actions);
        gap(body,14);TextView help=text("カードをタップして意味を確認。開くたびに発音が自動再生されます。",12,MUTED,false);add(body,help);
        body.post(()->{if(screen==1 && !flashIds.isEmpty() && flashIds.get(cardIndex)==id)playAudio(id);});
    }
    private void moveCard(int diff){
        if(cardIndex+diff<0){Toast.makeText(this,"最初のカードです",Toast.LENGTH_SHORT).show();return;}
        if(cardIndex+diff>=flashIds.size()){Toast.makeText(this,"最後のカードです",Toast.LENGTH_SHORT).show();return;}
        cardIndex+=diff;revealed=false;showFlash();
    }
    private void markCard(boolean mastered){
        int id=flashIds.get(cardIndex);seen.add(id);
        if(mastered){known.add(id);if(reviewing){review.remove(id);flashIds.remove(cardIndex);if(cardIndex>=flashIds.size())cardIndex=Math.max(0,flashIds.size()-1);}}
        else { known.remove(id);review.add(id); }
        saveProgress();
        if(flashIds.isEmpty()){Toast.makeText(this,"復習完了！",Toast.LENGTH_LONG).show();showHome();return;}
        if(!(mastered&&reviewing)){
            if(cardIndex<flashIds.size()-1)cardIndex++;
            else{Toast.makeText(this,"最後まで学習しました！",Toast.LENGTH_SHORT).show();showHome();return;}
        }
        revealed=false;showFlash();
    }
    private Spinner dropdown(String[] options){
        Spinner sp=new Spinner(this);ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,options);
        sp.setAdapter(adapter);sp.setBackground(outline(WHITE,Color.rgb(220,220,220)));sp.setPadding(dp(8),0,dp(8),0);sp.setMinimumHeight(dp(54));return sp;
    }
    private void showTestSetup(){mount("テスト設定",2);heading("テストする範囲","指定した範囲からランダムに出題。4択で意味を答えます。");
        String[] lessons=new String[10];for(int l=1;l<=10;l++)lessons[l-1]="Lesson "+l+"  ("+((l-1)*100+1)+"〜"+(l*100)+")";
        TextView label=text("開始レッスン",14,INK,true);add(body,label);gap(body,7);startSpinner=dropdown(lessons);startSpinner.setSelection(startLesson-1);add(body,startSpinner);
        gap(body,17);add(body,text("終了レッスン",14,INK,true));gap(body,7);endSpinner=dropdown(lessons);endSpinner.setSelection(endLesson-1);add(body,endSpinner);
        gap(body,17);add(body,text("出題数",14,INK,true));gap(body,7);
        TextView countLabel=text("20 問",18,BLUE,true);add(body,countLabel);gap(body,5);
        countSeek=new SeekBar(this);countSeek.setMax(80);countSeek.setProgress(0);add(body,countSeek);
        countSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar v,int progress,boolean fromUser){countLabel.setText((progress+20)+" 問");}
            @Override public void onStartTrackingTouch(SeekBar v){}
            @Override public void onStopTrackingTouch(SeekBar v){}
        });
        allCheck=new CheckBox(this);allCheck.setText("指定範囲の全問を出題する");allCheck.setTextColor(INK);
        allCheck.setTextSize(15);add(body,allCheck);
        allCheck.setOnCheckedChangeListener((v,checked)->{countSeek.setEnabled(!checked);countLabel.setText(checked?"範囲内の全問":(20+countSeek.getProgress())+" 問");});
        gap(body,21);
        LinearLayout info=panel();add(info,text("例：Lesson 1〜3・40問",14,INK,true));gap(info,5);
        add(info,text("300語からランダムに40語を出題します。「範囲内の全問」なら300語すべてを出題します。",13,MUTED,false));
        gap(body,20);add(body,button("テストを開始する →",true,this::launchTest));
        gap(body,12);add(body,text("※ 解答履歴や間違いは、この端末内に保存されます。",12,MUTED,false));
    }
    private void launchTest(){
        int a=startSpinner.getSelectedItemPosition()+1,b=endSpinner.getSelectedItemPosition()+1;
        if(b<a){Toast.makeText(this,"終了レッスンは開始以降にしてください",Toast.LENGTH_LONG).show();return;}
        startLesson=a;endLesson=b;
        ArrayList<Integer> candidates=new ArrayList<>();for(int i=(a-1)*100+1;i<=b*100;i++)candidates.add(i);
        Collections.shuffle(candidates,random);
        int n=allCheck.isChecked()?candidates.size():20+countSeek.getProgress();
        beginTest(new ArrayList<>(candidates.subList(0,Math.min(n,candidates.size()))),false);
    }
    private void beginTest(ArrayList<Integer> selected,boolean mistakesOnly){
        if(selected.isEmpty()){Toast.makeText(this,"出題する単語がありません",Toast.LENGTH_SHORT).show();return;}
        stopAudio();testIds=selected;wrongOnlyTest=mistakesOnly;
        testAnswers.clear();questionIndex=0;correctCount=0;answered=false;
        quizStart=System.currentTimeMillis();showQuestion();
    }
    private void startWrongTest(boolean lastOnly){
        ArrayList<Integer> ids=new ArrayList<>(lastOnly?lastWrongIds:review);
        if(ids.isEmpty()){Toast.makeText(this,"間違えた単語はありません",Toast.LENGTH_SHORT).show();return;}
        Collections.shuffle(ids,random);
        if(ids.size()>100)ids=new ArrayList<>(ids.subList(0,100));
        beginTest(ids,true);
    }
    private void showQuestion(){
        if(questionIndex>=testIds.size()){showResults();return;}
        int id=testIds.get(questionIndex);Word w=words.get(id-1);
        answered=false;mount("単語テスト",3);
        TextView progress=text((questionIndex+1)+" / "+testIds.size(),14,MUTED,true);add(body,progress);gap(body,12);
        LinearLayout card=panel();card.setMinimumHeight(dp(150));card.setGravity(Gravity.CENTER);
        TextView word=text(w.fr,31,INK,true);word.setGravity(Gravity.CENTER);add(card,word);gap(card,10);
        TextView question=text("この単語の意味は？",14,MUTED,false);question.setGravity(Gravity.CENTER);add(card,question);
        gap(body,12);add(body,button("🔊  発音を聞く",false,()->playAudio(id)));gap(body,17);
        ArrayList<Integer> distractors=new ArrayList<>();
        for(int i=1;i<=1000;i++)if(i!=id&&words.get(i-1).lesson==w.lesson&&!words.get(i-1).ja.equals(w.ja))distractors.add(i);
        Collections.shuffle(distractors,random);
        ArrayList<String> picked=new ArrayList<>();picked.add(w.ja);
        for(int cid:distractors){String a=words.get(cid-1).ja;if(!picked.contains(a)){picked.add(a);if(picked.size()==4)break;}}
        if(picked.size()<4)throw new IllegalStateException("選択肢が不足しています");
        Collections.shuffle(picked,random);
        for(String meaning:picked){
            Button option=button(meaning,false,()->chooseAnswer(meaning,w));
            option.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
            option.setTextColor(INK);option.setBackground(outline(WHITE,Color.rgb(224,224,224)));
            option.setTextSize(16);option.setPadding(dp(18),dp(12),dp(14),dp(12));
            add(body,option);gap(body,10);
        }
        gap(body,10);add(body,text("採点は全問終了後にまとめて表示されます。",12,MUTED,false));
        body.post(()->{if(screen==3 && questionIndex<testIds.size() && testIds.get(questionIndex)==id)playAudio(id);});
    }
    private void chooseAnswer(String selected,Word w){
        if(answered)return;answered=true;
        testAnswers.add(selected);
        seen.add(w.id);
        questionIndex++;
        stopAudio();showQuestion();
    }
    private void showResults(){
        correctCount=0;lastWrongIds.clear();
        for(int i=0;i<testIds.size();i++){
            int id=testIds.get(i);Word w=words.get(id-1);String selected=testAnswers.get(i);
            boolean right=selected.equals(w.ja);
            if(right){correctCount++;known.add(id);review.remove(id);}
            else{known.remove(id);review.add(id);lastWrongIds.add(id);wrongCounts.put(id,wrongCounts.getOrDefault(id,0)+1);}
            seen.add(id);
        }
        int total=testIds.size(),pct=total==0?0:(int)Math.round(correctCount*100.0/total);
        try{
            JSONObject record=new JSONObject();record.put("time",System.currentTimeMillis());
            record.put("from",wrongOnlyTest?0:startLesson);record.put("to",wrongOnlyTest?0:endLesson);
            record.put("questions",total);record.put("correct",correctCount);
            record.put("seconds",(System.currentTimeMillis()-quizStart)/1000);
            JSONArray last=new JSONArray();last.put(record);
            for(int i=0;i<Math.min(history.length(),99);i++)last.put(history.get(i));history=last;
        }catch(Exception ignored){}
        saveProgress();stopAudio();mount("テスト結果",5);
        LinearLayout summary=panel();summary.setGravity(Gravity.CENTER);
        TextView grade=text(pct+"%",51,INK,true);grade.setGravity(Gravity.CENTER);add(summary,grade);
        TextView detail=text(correctCount+" / "+total+" 問 正解",19,INK,true);detail.setGravity(Gravity.CENTER);add(summary,detail);
        gap(summary,9);TextView wrong=text("間違い "+lastWrongIds.size()+" 語",14,MUTED,false);wrong.setGravity(Gravity.CENTER);add(summary,wrong);
        gap(body,16);
        if(!lastWrongIds.isEmpty()){
            add(body,button("今回の間違いだけ再テスト（"+lastWrongIds.size()+"）",true,()->startWrongTest(true)));
            gap(body,9);add(body,button("間違えた単語のカードを開く",false,this::startReview));gap(body,18);
        }
        heading("まとめて答え合わせ","全問の正解と自分の回答を確認できます。");
        for(int i=0;i<total;i++){
            final int id=testIds.get(i);Word w=words.get(id-1);
            boolean right=testAnswers.get(i).equals(w.ja);
            LinearLayout item=panel();item.setBackground(outline(WHITE,Color.rgb(230,230,230)));
            LinearLayout line=row();TextView fr=text(w.fr,18,INK,true);line.addView(fr,new LinearLayout.LayoutParams(0,-2,1));
            TextView symbol=text(right?"✓":"×",22,right?GREEN:RED,true);line.addView(symbol);add(item,line);
            gap(item,5);add(item,text("正解："+w.ja,15,INK,false));
            if(!right){gap(item,5);add(item,text("回答："+testAnswers.get(i),14,RED,false));}
            TextView replay=text("▶ 発音を再生",12,MUTED,false);pad(replay,0,11);replay.setOnClickListener(v->playAudio(id));add(item,replay);
            gap(body,9);
        }
        gap(body,12);add(body,button("ホームへ戻る",true,this::showHome));
    }
    private void showStats(){
        stopAudio();mount("学習記録",6);heading("これまでの学習","記録はこの端末内だけに保存されています。");
        LinearLayout p=panel();add(p,text("覚えた単語     "+known.size()+" / 1000",18,INK,true));gap(p,11);
        add(p,text("一度学習した単語     "+seen.size()+"",15,INK,false));gap(p,9);
        add(p,text("間違いの累積回数     "+totalWrong(),15,INK,false));gap(p,9);
        add(p,text("復習待ち     "+review.size()+"語",15,RED,true));gap(p,9);
        add(p,text("受験したテスト     "+history.length()+"回",15,INK,false));
        gap(body,20);heading("テスト履歴", "新しい順に表示します。");
        if(history.length()==0)add(body,text("まだテスト履歴はありません。",14,MUTED,false));
        for(int i=0;i<Math.min(20,history.length());i++){
            JSONObject r=history.optJSONObject(i);if(r==null)continue;
            LinearLayout item=panel();
            String when=new SimpleDateFormat("yyyy/MM/dd HH:mm",Locale.JAPAN).format(new Date(r.optLong("time")));
            int cor=r.optInt("correct"),n=r.optInt("questions");
            add(item,text(when+"     "+cor+" / "+n+"問",16,INK,true));gap(item,4);
            add(item,text((r.optInt("from")==0?"間違いのみ":"Lesson "+r.optInt("from")+"〜"+r.optInt("to"))+"   ·   "+(n==0?0:(cor*100/n))+"% 正解",13,MUTED,false));gap(body,10);
        }
        gap(body,14);heading("間違いの記録", "累積で間違えた回数の多い順です。復習を終えても履歴は残ります。");
        ArrayList<Integer> ids=new ArrayList<>(wrongCounts.keySet());ids.sort((a,b)->Integer.compare(wrongCounts.get(b),wrongCounts.get(a)));
        if(ids.isEmpty())add(body,text("間違えた単語はまだありません。",14,MUTED,false));
        for(int id:ids){Word w=words.get(id-1);LinearLayout item=panel();
            add(item,text(w.fr+"  —  "+w.ja,15,INK,true));gap(item,4);
            add(item,text(wrongCounts.get(id)+"回間違い"+(review.contains(id)?"   ·  要復習":"   ·  復習済み"),12,review.contains(id)?RED:MUTED,false));
            gap(body,8);
        }
        gap(body,18);add(body,button("復習カードを開く",true,this::startReview));gap(body,16);
    }
    private int totalWrong(){int n=0;for(int x:wrongCounts.values())n+=x;return n;}
    private void playAudio(int id){
        stopAudio();
        try{
            AssetFileDescriptor f=getAssets().openFd(String.format(Locale.ROOT,"audio/%04d.ogg",id));
            MediaPlayer mp=new MediaPlayer();player=mp;mp.setDataSource(f.getFileDescriptor(),f.getStartOffset(),f.getLength());f.close();
            mp.setOnPreparedListener(p->{if(player==p)p.start();});
            mp.setOnErrorListener((p,what,extra)->{if(player==p)stopAudio();return true;});
            mp.prepareAsync();
        }catch(Exception ex){stopAudio();Toast.makeText(this,"音声を再生できません",Toast.LENGTH_SHORT).show();}
    }
    private void stopAudio(){if(player!=null){MediaPlayer p=player;player=null;try{p.reset();p.release();}catch(Exception ignored){}}}
    @Override protected void onPause(){super.onPause();stopAudio();}
    @Override protected void onDestroy(){stopAudio();super.onDestroy();}
    @Override public void onBackPressed(){if(screen==0)super.onBackPressed();else showHome();}
}
