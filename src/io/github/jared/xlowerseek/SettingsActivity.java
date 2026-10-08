package io.github.jared.xlowerseek;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

/** The home page contains daily choices; service setup and diagnostics have their own pages. */
public final class SettingsActivity extends Activity {
    private final java.util.Map<Feature,Switch> featureSwitches=new java.util.EnumMap<>(Feature.class);
    private RadioGroup choices;
    private Switch latest,translate;
    private TextView status;
    private boolean binding;
    private int savedSeconds;
    private boolean savedLatest,savedTranslate;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        SettingsUi ui=new SettingsUi(this,"X-UP","视频更顺手，阅读更轻松",false);
        LinearLayout video=ui.section("视频");
        addFeature(ui,video,Feature.LOWER_SEEK);ui.divider(video);addFeature(ui,video,Feature.SEEK_STEP);
        choices=new RadioGroup(this);choices.setOrientation(LinearLayout.HORIZONTAL);
        for(int seconds:new int[]{5,10}){RadioButton b=new RadioButton(this);b.setId(seconds);b.setText(seconds+" 秒");b.setTextSize(16);b.setTextColor(ui.ink);b.setMinHeight(ui.dp(52));choices.addView(b,new RadioGroup.LayoutParams(0,-2,1));}video.addView(choices);
        ui.divider(video);addFeature(ui,video,Feature.SWIPE_SEEK);
        ui.divider(video);addFeature(ui,video,Feature.HOLD_SPEED);
        ui.divider(video);addFeature(ui,video,Feature.DOWNLOAD);ui.navigation(video,"下载设置","",()->startActivity(new Intent(this,DownloadSettingsActivity.class)));
        LinearLayout reading=ui.section("阅读与翻译");
        latest=ui.toggle(reading,"最新回复优先","自动选最新回复，仍可手动切换。");
        ui.divider(reading);translate=ui.toggle(reading,"自动中文阅读","翻译总开关；图片文字不处理。");
        ui.navigation(reading,"翻译方式与模型","三个离线引擎可选，原生翻译备用。",()->startActivity(new Intent(this,LocalModelActivity.class)));
        LinearLayout module=ui.section("模块");status=ui.status(module,"");
        ui.navigation(module,"状态与帮助","适配版本、启用方法与功能边界。",()->startActivity(new Intent(this,ModuleStatusActivity.class)));
        ui.button(ui.body,"打开 X",true).setOnClickListener(v->{Intent launch=getPackageManager().getLaunchIntentForPackage("com.twitter.android");if(launch==null){status.setText("未安装 X");return;}startActivity(launch);});
        bind();choices.setOnCheckedChangeListener((g,id)->save());latest.setOnCheckedChangeListener((v,b)->save());translate.setOnCheckedChangeListener((v,b)->save());
    }
    @Override protected void onStart(){super.onStart();SettingsStore.observe(this::bind);bind();}
    @Override protected void onStop(){SettingsStore.observe(null);super.onStop();}
    private void bind(){
        binding=true;savedSeconds=SettingsStore.seconds(this);savedLatest=SettingsStore.latest(this);savedTranslate=SettingsStore.translate(this);
        for(java.util.Map.Entry<Feature,Switch> entry:featureSwitches.entrySet())entry.getValue().setChecked(SettingsStore.feature(this,entry.getKey()));
        for(int i=0;i<choices.getChildCount();i++){boolean on=SettingsStore.feature(this,Feature.SEEK_STEP);choices.getChildAt(i).setEnabled(on);choices.getChildAt(i).setAlpha(on?1f:.45f);}
        choices.check(savedSeconds);latest.setChecked(savedLatest);translate.setChecked(savedTranslate);binding=false;
        status.setText(SettingsStore.isShared()?"设置服务已连接 · 修改立即保存":"设置已保存在本机 · 等待 LSPosed 连接");
    }
    private void addFeature(SettingsUi ui,LinearLayout card,Feature feature){
        Switch control=ui.toggle(card,feature.title,feature.description);featureSwitches.put(feature,control);
        control.setOnCheckedChangeListener((v,on)->{if(binding)return;if(!SettingsStore.setFeature(this,feature,on)){binding=true;control.setChecked(!on);binding=false;status.setText("保存失败，已恢复之前设置。");}else bind();});
    }
    private void save(){
        if(binding)return;
        if(SettingsStore.save(this,choices.getCheckedRadioButtonId(),latest.isChecked(),translate.isChecked()))bind();
        else{binding=true;choices.check(savedSeconds);latest.setChecked(savedLatest);translate.setChecked(savedTranslate);binding=false;status.setText("保存失败，已恢复之前的设置。");}
    }
}
