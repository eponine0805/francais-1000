package com.francais.mille;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.text.TextWatcher;
import android.text.Editable;
import android.widget.EditText;
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
import java.util.Calendar;

/** Native, local-only vocabulary trainer. No permissions, network or external dependencies. */
public final class MainActivity extends Activity {
    private static final int BLUE=Color.rgb(24,24,24), INK=Color.rgb(23,23,23), MUTED=Color.rgb(120,120,120),
        BACK=Color.rgb(251,251,250), WHITE=Color.WHITE, PALE=Color.rgb(244,244,243),
        GREEN=Color.rgb(24,24,24), RED=Color.rgb(24,24,24), LINE=Color.rgb(225,225,223);
    private static final String[] LESSON_NAMES={
        "はじめての会話", "数字・毎日の動詞", "人・家族・身体", "家と暮らし", "食べ物・買い物",
        "日時・天気・自然", "街・旅行", "色・形容詞・副詞", "学校・仕事・趣味", "発展する動詞・表現"};
    private final ArrayList<Word> words=new ArrayList<>();
    private final Set<Integer> seen=new HashSet<>(), known=new HashSet<>(), review=new HashSet<>(), favorites=new HashSet<>();
    private final Map<Integer,Integer> wrongCounts=new HashMap<>();
    private JSONArray history=new JSONArray();
    private JSONObject daily=new JSONObject();
    private int lastLesson=1;
    private int resultOffset=0;
    private LinearLayout resultContainer;
    private final ArrayList<Integer> resultsInOrder=new ArrayList<>();
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
        String fr,ja,ipa;
        Word(JSONObject obj) throws Exception {id=obj.getInt("id");lesson=obj.getInt("lesson");fr=obj.getString("fr");ja=obj.getString("ja");ipa=obj.optString("ipa","");}
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
            readSet(obj.optJSONArray("favorites"),favorites);
            daily=obj.optJSONObject("daily");if(daily==null)daily=new JSONObject();
            lastLesson=Math.max(1,Math.min(10,obj.optInt("last_lesson",1)));
        }catch(Exception ignored) { /* A damaged local record must not prevent study. */ }
    }
    private int upgradeId(int id){return migrateOldProgress?legacyIds.getOrDefault(id,0):id;}
    private void readSet(JSONArray a,Set<Integer> target){if(a==null)return;for(int i=0;i<a.length();i++){int id=upgradeId(a.optInt(i));if(id>=1&&id<=1000)target.add(id);}}
    private JSONArray toArray(Set<Integer> ids){JSONArray a=new JSONArray();ArrayList<Integer> sorted=new ArrayList<>(ids);Collections.sort(sorted);for(int id:sorted)a.put(id);return a;}
    private void saveProgress(){
        try{
            JSONObject out=new JSONObject();out.put("seen",toArray(seen));out.put("known",toArray(known));out.put("review",toArray(review));
            JSONObject mistakes=new JSONObject();for(Map.Entry<Integer,Integer> e:wrongCounts.entrySet())mistakes.put(String.valueOf(e.getKey()),e.getValue());
            out.put("mistakes",mistakes);out.put("history",history);out.put("favorites",toArray(favorites));
            out.put("daily",daily);out.put("last_lesson",lastLesson);out.put("catalog_version",2);
            prefs.edit().putString("state",out.toString()).apply();
        }catch(Exception ignored){}
    }

    private String dayKey(int offset){Calendar cal=Calendar.getInstance();cal.add(Calendar.DAY_OF_YEAR,offset);
        return new SimpleDateFormat("yyyy-MM-dd",Locale.US).format(cal.getTime());}
    private void recordDaily(){try{String k=dayKey(0);daily.put(k,daily.optInt(k)+1);}catch(Exception ignored){}}
    private int streak(){int count=0;int start=daily.optInt(dayKey(0))>0?0:-1;
        for(int i=start;i>-366;i--){if(daily.optInt(dayKey(i))>0)count++;else break;}return count;}
    private void toggleFavorite(int id){if(!favorites.add(id))favorites.remove(id);saveProgress();}
    private boolean isFavorite(int id){return favorites.contains(id);}
    private LinearLayout progressBar(float percent){
        LinearLayout track=row();track.setBackground(bg(LINE,5));
        View fill=new View(this);fill.setBackground(bg(INK,5));
        float frac=Math.max(0.01f,Math.min(100f,percent));
        track.addView(fill,new LinearLayout.LayoutParams(0,dp(5),frac));
        track.addView(new View(this),new LinearLayout.LayoutParams(0,dp(5),100f-frac));return track;
    }
    private TextView eyebrow(String t){TextView v=text(t,11,MUTED,true);v.setLetterSpacing(.1f);return v;}
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
        b.setTextColor(primary?WHITE:INK);b.setBackground(outline(primary?INK:WHITE,primary?INK:LINE));
        b.setForeground(new RippleDrawable(ColorStateList.valueOf(primary?0x44FFFFFF:0x14000000),null,bg(WHITE,18)));
        b.setGravity(Gravity.CENTER);b.setPadding(dp(16),dp(11),dp(16),dp(11));b.setMinHeight(dp(52));
        b.setOnClickListener(v->action.run());return b;
    }
    private void heading(String title,String subtitle){
        TextView a=text(title,22,INK,true);add(body,a);
        if(subtitle!=null){TextView b=text(subtitle,13,MUTED,false);add(body,b);}gap(body,14);
    }
    private LinearLayout panel(){LinearLayout v=vertical();pad(v,20,20);v.setBackground(outline(WHITE,LINE));add(body,v);return v;}
    private void mount(String title,int destination){
        screen=destination;
        shell=vertical();shell.setBackgroundColor(BACK);
        LinearLayout top=row();top.setBackgroundColor(WHITE);pad(top,20,13);
        TextView titleText=text(title,17,INK,true);top.addView(titleText,new LinearLayout.LayoutParams(0,-2,1));
        if(destination!=0){TextView home=text("閉じる ×",13,MUTED,false);pad(home,10,8);
            home.setOnClickListener(v->{if(screen==3)confirmLeaveQuiz();else showHome();});top.addView(home);}
        add(shell,top);
        View rule=new View(this);rule.setBackgroundColor(LINE);shell.addView(rule,new LinearLayout.LayoutParams(-1,dp(1)));
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setClipToPadding(false);
        body=vertical();pad(body,19,21);sc.addView(body);
        shell.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        if(destination==0||destination==2||destination==4||destination==6||destination==7){
            LinearLayout bar=row();bar.setBackgroundColor(WHITE);pad(bar,9,10);
            String[] labels={"学ぶ","テスト","単語帳","記録"};
            Runnable[] actions={this::showHome,this::showTestSetup,()->showDictionary(false),this::showStats};
            int selected=destination==0||destination==4?0:destination==2?1:destination==7?2:3;
            for(int i=0;i<labels.length;i++){
                final int n=i;TextView item=text(labels[i],12,i==selected?INK:MUTED,i==selected);
                item.setGravity(Gravity.CENTER);pad(item,4,9);bar.addView(item,new LinearLayout.LayoutParams(0,dp(39),1));
                item.setOnClickListener(v->actions[n].run());
            }
            View border=new View(this);border.setBackgroundColor(LINE);shell.addView(border,new LinearLayout.LayoutParams(-1,dp(1)));
            add(shell,bar);
        }
        setContentView(shell);
    }
    private void confirmLeaveQuiz(){new AlertDialog.Builder(this).setTitle("テストを終了しますか？")
        .setMessage("このテストの未完了の回答は保存されません。")
        .setNegativeButton("続ける",(d,w)->{}).setPositiveButton("終了する",(d,w)->showHome()).show();}
    private int countKnown(int lesson){int n=0;for(int id:known)if((id-1)/100+1==lesson)n++;return n;}
    private void showHome(){stopAudio();mount("Français 1000",0);
        add(body,eyebrow("BONJOUR  ·  EVERYDAY FRENCH"));gap(body,10);
        add(body,text("少しずつ、確実に。",28,INK,true));gap(body,7);
        add(body,text("聞いて、覚えて、試して、定着させる。",14,MUTED,false));gap(body,19);
        LinearLayout hero=panel();hero.setBackground(bg(WHITE,18));
        LinearLayout top=row();TextView label=eyebrow("習得した単語");top.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        top.addView(text("連続 "+streak()+" 日",12,MUTED,true));add(hero,top);gap(hero,6);
        add(hero,text(known.size()+" / 1,000",31,INK,true));gap(hero,14);
        add(hero,progressBar(known.size()/10f));gap(hero,13);
        add(hero,text("学習済み  "+seen.size()+"語     復習待ち  "+review.size()+"語     テスト  "+history.length()+"回",12,MUTED,false));
        gap(body,15);add(body,button("続きから学ぶ   →",true,()->startLesson(lastLesson)));
        gap(body,10);
        LinearLayout quick=row();Button test=button("4択テスト",false,this::showTestSetup);
        Button wrong=button("苦手を復習  "+review.size(),false,this::startReview);
        quick.addView(test,weight());View sep=new View(this);quick.addView(sep,new LinearLayout.LayoutParams(dp(9),1));quick.addView(wrong,weight());add(body,quick);
        gap(body,24);add(body,eyebrow("LESSONS"));gap(body,6);
        add(body,text("100語ずつ、10ステップ",21,INK,true));gap(body,6);
        add(body,text("レッスンを選択すると自動で発音します。",12,MUTED,false));gap(body,14);
        for(int l=1;l<=10;l++){
            final int lesson=l;LinearLayout item=panel();
            LinearLayout line=row();TextView name=text(String.format(Locale.JAPAN,"%02d   %s",l,LESSON_NAMES[l-1]),15,INK,true);
            line.addView(name,new LinearLayout.LayoutParams(0,-2,1));line.addView(text("→",19,MUTED,false));add(item,line);
            gap(item,13);add(item,progressBar(countKnown(l)));gap(item,8);
            add(item,text("習得済み "+countKnown(l)+" / 100",12,MUTED,false));
            item.setOnClickListener(v->startLesson(lesson));gap(body,9);
        }
        gap(body,15);add(body,text("1000項目と女性音声はすべて端末内に収録。通信やログインは必要ありません。",12,MUTED,false));
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
        lastLesson=lesson;cardIndex=0;revealed=false;saveProgress();showFlash();
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
        int id=flashIds.get(cardIndex);Word w=words.get(id-1);if(seen.add(id))recordDaily();saveProgress();
        mount(reviewing?"苦手単語の復習":"フラッシュカード",1);
        TextView counter=text((cardIndex+1)+" / "+flashIds.size()+"     •     "+(reviewing?"復習カード":"Lesson "+w.lesson),14,MUTED,true);add(body,counter);gap(body,13);
        LinearLayout card=vertical();card.setGravity(Gravity.CENTER);pad(card,20,40);card.setMinimumHeight(dp(295));card.setBackground(outline(WHITE,Color.rgb(228,228,228)));add(body,card);
        TextView word=text(w.fr,34,INK,true);word.setGravity(Gravity.CENTER);add(card,word);gap(card,11);
        TextView ipa=text(w.ipa,17,MUTED,false);ipa.setGravity(Gravity.CENTER);add(card,ipa);gap(card,22);
        TextView meaning=text(revealed?w.ja:"タップして日本語を表示",revealed?23:15,revealed?INK:MUTED,revealed);
        meaning.setGravity(Gravity.CENTER);add(card,meaning);gap(card,14);
        if(reviewing){TextView prompt=text("過去の間違い "+wrongCounts.getOrDefault(id,0)+"回",12,MUTED,false);prompt.setGravity(Gravity.CENTER);add(card,prompt);}
        card.setOnClickListener(v->{revealed=!revealed;meaning.setText(revealed?w.ja:"タップして日本語を表示");meaning.setTextSize(revealed?23:15);meaning.setTextColor(revealed?INK:MUTED);});
        gap(body,12);
        LinearLayout audioActions=row();
        Button play=button("▶  発音を聞く",false,()->playAudio(id));
        Button star=button(isFavorite(id)?"★ お気に入り":"☆ お気に入り",false,()->{toggleFavorite(id);showFlash();});
        audioActions.addView(play,weight());View gap=new View(this);audioActions.addView(gap,new LinearLayout.LayoutParams(dp(8),1));audioActions.addView(star,weight());add(body,audioActions);gap(body,13);
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
        TextView word=text(w.fr,31,INK,true);word.setGravity(Gravity.CENTER);add(card,word);gap(card,8);
        TextView ipa=text(w.ipa,16,MUTED,false);ipa.setGravity(Gravity.CENTER);add(card,ipa);gap(card,13);
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
            option.setGravity(Gravity.CENTER);
            option.setTextColor(INK);option.setBackground(outline(WHITE,Color.rgb(224,224,224)));
            option.setTextSize(16);option.setMinHeight(dp(62));option.setPadding(dp(18),dp(14),dp(18),dp(14));
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
        heading("答え合わせ", "間違えた問題を先に表示します。発音も再確認できます。");
        resultsInOrder.clear();
        for(int i=0;i<total;i++)if(!testAnswers.get(i).equals(words.get(testIds.get(i)-1).ja))resultsInOrder.add(i);
        for(int i=0;i<total;i++)if(testAnswers.get(i).equals(words.get(testIds.get(i)-1).ja))resultsInOrder.add(i);
        resultOffset=0;resultContainer=vertical();add(body,resultContainer);appendResults();
        gap(body,12);add(body,button("ホームへ戻る",false,this::showHome));
    }
    private void appendResults(){
        int until=Math.min(resultsInOrder.size(),resultOffset+30);
        for(int j=resultOffset;j<until;j++){
            int i=resultsInOrder.get(j);final int id=testIds.get(i);Word w=words.get(id-1);
            boolean right=testAnswers.get(i).equals(w.ja);
            LinearLayout item=vertical();pad(item,19,17);item.setBackground(outline(WHITE,LINE));add(resultContainer,item);
            LinearLayout line=row();TextView fr=text(w.fr,18,INK,true);
            line.addView(fr,new LinearLayout.LayoutParams(0,-2,1));
            line.addView(text(right?"✓ 正解":"× 不正解",13,right?MUTED:INK,true));add(item,line);
            gap(item,6);add(item,text(w.ipa,15,MUTED,false));gap(item,7);
            add(item,text("正解  "+w.ja,14,INK,false));
            if(!right){gap(item,5);add(item,text("あなたの回答  "+testAnswers.get(i),13,MUTED,false));}
            TextView replay=text("▶  発音を聞く",13,INK,true);pad(replay,0,10);replay.setOnClickListener(v->playAudio(id));add(item,replay);
            gap(resultContainer,9);
        }
        resultOffset=until;
        if(resultOffset<resultsInOrder.size()){
            Button more=button("続きを表示  （"+resultOffset+" / "+resultsInOrder.size()+"）",false,()->{
                int childCount=resultContainer.getChildCount();
                resultContainer.removeViewAt(childCount-1);appendResults();
            });add(resultContainer,more);
        }
    }
    private void showDictionary(boolean favoriteOnly){
        stopAudio();mount("単語帳",7);
        add(body,eyebrow("ALL VOCABULARY"));gap(body,8);
        add(body,text("探して、聞いて、保存する。",22,INK,true));gap(body,13);
        EditText search=new EditText(this);search.setSingleLine(true);search.setTextSize(16);
        search.setHint("フランス語・日本語で検索");search.setTextColor(INK);search.setHintTextColor(MUTED);
        search.setPadding(dp(16),dp(12),dp(16),dp(12));search.setBackground(outline(WHITE,LINE));add(body,search);
        gap(body,10);
        LinearLayout filters=row();Button all=button("すべて",!favoriteOnly,()->showDictionary(false));
        Button saved=button("☆ 保存済み  "+favorites.size(),favoriteOnly,()->showDictionary(true));
        filters.addView(all,weight());View space=new View(this);filters.addView(space,new LinearLayout.LayoutParams(dp(8),1));
        filters.addView(saved,weight());add(body,filters);gap(body,15);
        LinearLayout list=vertical();add(body,list);
        search.addTextChangedListener(new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence c,int a,int n,int d){}
            @Override public void onTextChanged(CharSequence c,int st,int b,int n){fillDictionary(list,c.toString(),favoriteOnly,40);}
            @Override public void afterTextChanged(Editable e){}
        });
        fillDictionary(list,"",favoriteOnly,40);
    }
    private void fillDictionary(LinearLayout list,String query,boolean favoriteOnly,int limit){
        list.removeAllViews();String q=query.trim().toLowerCase(Locale.ROOT);
        ArrayList<Word> matching=new ArrayList<>();
        for(Word w:words)if((!favoriteOnly||isFavorite(w.id))&&(q.isEmpty()||w.fr.toLowerCase(Locale.ROOT).contains(q)||w.ja.contains(q)))matching.add(w);
        add(list,text(matching.size()+" 件",12,MUTED,true));gap(list,10);
        int until=Math.min(matching.size(),limit);
        for(int i=0;i<until;i++){
            Word w=matching.get(i);LinearLayout item=vertical();pad(item,17,15);item.setBackground(outline(WHITE,LINE));
            LinearLayout r=row();TextView word=text(w.fr,18,INK,true);r.addView(word,new LinearLayout.LayoutParams(0,-2,1));
            TextView fav=text(isFavorite(w.id)?"★":"☆",22,INK,false);pad(fav,10,3);
            fav.setOnClickListener(v->{toggleFavorite(w.id);fav.setText(isFavorite(w.id)?"★":"☆");});r.addView(fav);add(item,r);
            gap(item,5);add(item,text(w.ipa,14,MUTED,false));gap(item,7);add(item,text(w.ja,14,INK,false));
            TextView play=text("▶ 発音を聞く",12,MUTED,true);pad(play,0,10);play.setOnClickListener(v->playAudio(w.id));add(item,play);
            add(list,item);gap(list,8);
        }
        if(matching.size()>until){
            Button more=button("さらに表示  "+until+" / "+matching.size(),false,
                ()->fillDictionary(list,query,favoriteOnly,limit+40));add(list,more);
        }
        if(matching.isEmpty())add(list,text("一致する単語はありません。",14,MUTED,false));
    }
    private void showStats(){
        stopAudio();mount("学習記録",6);heading("これまでの学習","記録はこの端末内だけに保存されています。");
        LinearLayout p=panel();add(p,text("覚えた単語     "+known.size()+" / 1000",18,INK,true));gap(p,11);
        add(p,text("一度学習した単語     "+seen.size()+"",15,INK,false));gap(p,9);
        add(p,text("間違いの累積回数     "+totalWrong(),15,INK,false));gap(p,9);
        add(p,text("復習待ち     "+review.size()+"語",15,INK,true));gap(p,9);
        add(p,text("お気に入り     "+favorites.size()+"語",15,INK,false));gap(p,9);
        add(p,text("学習継続     "+streak()+"日",15,INK,false));gap(p,9);
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
        gap(body,18);add(body,button("苦手カードを開く",true,this::startReview));gap(body,10);
        add(body,button("間違い履歴すべてで再テスト",false,()->{
            ArrayList<Integer> all=new ArrayList<>(wrongCounts.keySet());Collections.shuffle(all,random);
            if(all.size()>100)all=new ArrayList<>(all.subList(0,100));beginTest(all,true);
        }));gap(body,16);
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
    @Override public void onBackPressed(){if(screen==0)super.onBackPressed();else if(screen==3)confirmLeaveQuiz();else showHome();}
}
