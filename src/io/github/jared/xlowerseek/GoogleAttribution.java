package io.github.jared.xlowerseek;
import android.app.Activity;import android.graphics.BitmapFactory;import android.view.*;import android.widget.*;
/** Official unmodified attribution badge, separate from the author's translated text. */
final class GoogleAttribution {
 private ImageView badge;
 void update(Activity activity,boolean visible){
  if(!visible||activity==null){clear();return;}
  if(badge!=null&&badge.getContext()!=activity)clear();if(badge!=null)return;
  try{
   android.content.Context module=activity.createPackageContext("io.github.jared.xlowerseek",0);
   android.graphics.Bitmap bitmap;try(java.io.InputStream in=module.getAssets().open("branding/google-translate.png")){bitmap=BitmapFactory.decodeStream(in);}if(bitmap==null)return;
   float density=activity.getResources().getDisplayMetrics().density;int width=(int)(160*density),height=(int)(width*(float)bitmap.getHeight()/bitmap.getWidth());
   badge=new ImageView(activity);badge.setImageBitmap(bitmap);badge.setContentDescription("由 Google Translate 自动翻译");badge.setBackgroundColor(0xFFFFFFFF);badge.setPadding((int)(6*density),(int)(3*density),(int)(6*density),(int)(3*density));
   FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(width+(int)(12*density),height+(int)(6*density),Gravity.BOTTOM|Gravity.END);lp.rightMargin=(int)(12*density);lp.bottomMargin=(int)(92*density);
   badge.setClickable(false);((ViewGroup)activity.getWindow().getDecorView()).addView(badge,lp);
  }catch(Exception unavailable){clear();}
 }
 void clear(){if(badge!=null&&badge.getParent() instanceof ViewGroup)((ViewGroup)badge.getParent()).removeView(badge);badge=null;}
}
