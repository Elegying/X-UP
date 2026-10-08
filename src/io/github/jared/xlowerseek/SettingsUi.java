package io.github.jared.xlowerseek;

import android.app.*;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;

/** Shared native Android settings components; colors follow the system theme. */
final class SettingsUi {
    final Activity activity;
    final int background,surface,ink,muted,accent,line;
    final LinearLayout body;
    SettingsUi(Activity activity,String title,String subtitle,boolean back){
        this.activity=activity;
        boolean dark=(activity.getResources().getConfiguration().uiMode&0x30)==0x20;
        background=Color.parseColor(dark?"#101318":"#F3F5F8");surface=Color.parseColor(dark?"#1B2028":"#FFFFFF");
        ink=Color.parseColor(dark?"#F2F4F8":"#18202C");muted=Color.parseColor(dark?"#ADB8C8":"#596679");
        accent=Color.parseColor(dark?"#94B9FF":"#255DB0");line=Color.parseColor(dark?"#303947":"#E6EAF0");
        activity.getWindow().getDecorView().setBackgroundColor(background);
        WindowInsetsController bars=activity.getWindow().getInsetsController();
        if(bars!=null)bars.setSystemBarsAppearance(dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        ScrollView scroll=new ScrollView(activity);scroll.setFillViewport(true);scroll.setBackgroundColor(background);scroll.setClipToPadding(false);
        body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(12),dp(20),dp(28));scroll.addView(body);
        scroll.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets b=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(b.left,b.top,b.right,b.bottom);return insets;});
        if(back){Button button=button(body,"‹  返回",false);button.setOnClickListener(v->activity.finish());}
        TextView name=text(body,title,30,ink);name.setTypeface(null,Typeface.BOLD);name.setPadding(dp(4),dp(16),0,dp(4));
        TextView description=text(body,subtitle,14,muted);description.setPadding(dp(4),0,0,dp(16));
        activity.setContentView(scroll);
    }
    int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    TextView text(LinearLayout parent,String text,int size,int color){TextView v=new TextView(activity);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setLineSpacing(dp(3),1);parent.addView(v,new LinearLayout.LayoutParams(-1,-2));return v;}
    LinearLayout section(String title){
        TextView heading=text(body,title,13,muted);heading.setTypeface(null,Typeface.BOLD);heading.setPadding(dp(4),dp(18),0,dp(8));heading.setAccessibilityHeading(true);
        LinearLayout card=new LinearLayout(activity);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(16),dp(10),dp(16),dp(10));card.setBackground(shape(surface,18));body.addView(card,new LinearLayout.LayoutParams(-1,-2));return card;
    }
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    void divider(LinearLayout card){View v=new View(activity);v.setBackgroundColor(line);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.topMargin=dp(8);lp.bottomMargin=dp(8);card.addView(v,lp);}
    void info(LinearLayout card,String title,String description){TextView t=text(card,title,16,ink);t.setPadding(0,dp(10),0,dp(2));TextView d=text(card,description,13,muted);d.setPadding(0,0,0,dp(10));}
    Switch toggle(LinearLayout card,String title,String description){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(10),0,dp(10));card.addView(row);
        LinearLayout labels=new LinearLayout(activity);labels.setOrientation(LinearLayout.VERTICAL);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));text(labels,title,16,ink);TextView d=text(labels,description,13,muted);d.setPadding(0,dp(4),dp(12),0);
        Switch control=new Switch(activity);control.setContentDescription(title);control.setMinHeight(dp(48));control.setMinWidth(dp(52));row.addView(control);row.setOnClickListener(v->{if(control.isEnabled())control.setChecked(!control.isChecked());});return control;
    }
    Button button(LinearLayout card,String title,boolean primary){
        Button b=new Button(activity);b.setText(title);b.setTextSize(15);b.setAllCaps(false);b.setMinHeight(dp(50));b.setTextColor(primary?Color.WHITE:accent);
        b.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x226B9EF5),shape(primary?Color.rgb(37,93,176):surface,12),null));b.setStateListAnimator(null);b.setElevation(0);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(4);lp.bottomMargin=dp(4);card.addView(b,lp);return b;
    }
    Button navigation(LinearLayout card,String title,String detail,Runnable action){
        Button b=button(card,title+"  ›",false);b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setOnClickListener(v->action.run());
        if(!detail.isEmpty()){TextView t=text(card,detail,13,muted);t.setPadding(dp(4),0,dp(4),dp(8));}return b;
    }
    TextView status(LinearLayout card,String value){TextView t=text(card,value,14,muted);t.setPadding(0,dp(8),0,dp(8));t.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);return t;}
    void details(String title,String message){new AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("知道了",null).show();}
}
