package io.github.jared.xlowerseek;

import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.reflect.Member;

/** Separate gesture recognizer: taps and vertical swipes remain with X. */
final class FullScreenGestureHook {
    interface Target { Object player(); View surface(); View decor(); void syncProgress(Object player); boolean swipeEnabled(); boolean holdEnabled(); }
    private final Target target;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private Object player, originalSpeed,downSpeed;
    private View decor;
    private MotionEvent down;
    private Member dispatch;
    private float x,y,width;
    private long start,duration,lastSeek;
    private boolean dragging,boosting,cancelled,ignoreStream;
    private TextView hint;
    private final Runnable hold=()->boost();
    FullScreenGestureHook(Target target) {this.target=target;}
    void install(ClassLoader loader) {
        Hooks.findAndHookMethod("com.android.internal.policy.DecorView",loader,"dispatchTouchEvent",MotionEvent.class,new HookCallback(){
            @Override protected void beforeHookedMethod(HookParam p) {
                if(p.thisObject!=target.decor() && p.thisObject!=decor)return;
                try {if(touch((View)p.thisObject,p.method,(MotionEvent)p.args[0]))p.setResult(true);}
                catch(Throwable e){boolean claimed=cancelled||ignoreStream;reset();ignoreStream=claimed;if(claimed)p.setResult(true);android.util.Log.w("XLowerSeek","gesture unavailable: "+e.getClass().getSimpleName());}
            }
        });
    }
    private boolean touch(View root,Member method,MotionEvent e) throws Throwable {
        int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){
            reset();ignoreStream=false;
            Object active=target.player();View surface=target.surface();
            if(active==null||surface==null)return false;
            Rect r=new Rect();if(!surface.getGlobalVisibleRect(r))return false;
            float density=root.getResources().getDisplayMetrics().density;
            int[] location=new int[2];root.getLocationOnScreen(location);
            float localX=e.getRawX()-location[0],localY=e.getRawY()-location[1];
            WindowInsets windowInsets=root.getRootWindowInsets();
            android.graphics.Insets insets=windowInsets==null?android.graphics.Insets.NONE:
                    windowInsets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.systemGestures());
            // Leave system edges, toolbar, native timeline and bottom actions untouched.
            if(!r.contains((int)localX,(int)localY)
                    ||!GestureArea.contains(root.getWidth(),root.getHeight(),density,localX,localY,
                            insets.left,insets.top,insets.right,insets.bottom)
                    ||localY>r.bottom-32*density)return false;
            long total=((Number)Reflect.callMethod(active,"getDuration")).longValue();
            if(total<=0||Boolean.TRUE.equals(Reflect.callMethod(active,"g")))return false;
            player=active;decor=root;dispatch=method;down=MotionEvent.obtain(e);
            x=e.getX();y=e.getY();width=root.getWidth();duration=total;
            start=((Number)Reflect.callMethod(player,"getCurrentPosition")).longValue();
            downSpeed=target.holdEnabled()?Reflect.callMethod(player,"d"):null;
            if(target.holdEnabled())handler.postDelayed(hold,500);return false;
        }
        if(ignoreStream){if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)ignoreStream=false;return true;}
        if(player==null)return false;
        if(player!=target.player()) {boolean used=cancelled;reset();ignoreStream=used;return used;}
        if(e.getPointerCount()>1||action==MotionEvent.ACTION_CANCEL||action==MotionEvent.ACTION_OUTSIDE){boolean used=cancelled;reset();ignoreStream=used&&action!=MotionEvent.ACTION_CANCEL;return used;}
        if(action==MotionEvent.ACTION_MOVE){
            float dx=e.getX()-x,dy=e.getY()-y;
            int slop=ViewConfiguration.get(root.getContext()).getScaledTouchSlop()*2;
            if(!dragging&&!boosting&&Math.hypot(dx,dy)>slop){
                handler.removeCallbacks(hold);
                if(target.swipeEnabled()&&Math.abs(dx)>Math.abs(dy)*1.5){dragging=true;cancelOriginal();}
                else {reset();return false;}
            }
            if(dragging)seek(dx,false);
            return cancelled;
        }
        if(action==MotionEvent.ACTION_UP){
            boolean used=cancelled;
            if(dragging)seek(e.getX()-x,true);
            reset();return used;
        }
        return cancelled;
    }
    private void cancelOriginal() throws Throwable {
        if(cancelled||down==null)return;
        MotionEvent cancel=MotionEvent.obtain(down);cancel.setAction(MotionEvent.ACTION_CANCEL);
        try{Hooks.invokeOriginalMethod(dispatch,decor,new Object[]{cancel});cancelled=true;}finally{cancel.recycle();}
    }
    private void seek(float delta,boolean finish){
        long position=SwipePolicy.position(start,duration,delta,width);
        long now=SystemClock.uptimeMillis();
        if(finish||now-lastSeek>=80){Reflect.callMethod(player,"seekTo",position);target.syncProgress(player);lastSeek=now;}
        show(time(position)+" / "+time(duration));
        if(finish)android.util.Log.i("XLowerSeek","swipeSeek from="+start+" to="+position);
    }
    private void boost(){
        try{
            if(!target.holdEnabled()||player==null||player!=target.player()||dragging||!Boolean.TRUE.equals(Reflect.callMethod(player,"isPlaying")))return;
            originalSpeed=downSpeed;
            if(originalSpeed==null)return;
            Object speed=Reflect.newInstance(originalSpeed.getClass(),2f,Reflect.getFloatField(originalSpeed,"b"));
            cancelOriginal();Reflect.callMethod(player,"c",speed);boosting=true;
            android.util.Log.i("XLowerSeek","holdSpeed=2 original="+Reflect.getFloatField(originalSpeed,"a"));
        }catch(Throwable e){reset();}
    }
    void reset(){
        // Once X receives CANCEL, consume the remainder even if settings or reflection fail.
        ignoreStream=ignoreStream||cancelled;
        handler.removeCallbacks(hold);
        if(originalSpeed!=null&&player!=null)try{
            Reflect.callMethod(player,"c",originalSpeed);
            android.util.Log.i("XLowerSeek","holdSpeed restored="+Reflect.getFloatField(originalSpeed,"a"));
        }catch(Throwable ignored){}
        if(hint!=null&&hint.getParent() instanceof android.view.ViewGroup)((android.view.ViewGroup)hint.getParent()).removeView(hint);
        hint=null;originalSpeed=null;downSpeed=null;player=null;decor=null;dispatch=null;
        if(down!=null)down.recycle();down=null;dragging=false;boosting=false;cancelled=false;lastSeek=0;
    }
    private void show(String value){
        if(hint==null&&decor instanceof android.view.ViewGroup){
            hint=new TextView(decor.getContext());hint.setTextColor(-1);hint.setTextSize(18);hint.setBackgroundColor(0xCC20242C);hint.setPadding(28,20,28,20);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);
            ((android.view.ViewGroup)decor).addView(hint,lp);
        }
        if(hint!=null)hint.setText(value);
    }
    private static String time(long ms){long s=ms/1000;return String.format(java.util.Locale.ROOT,"%d:%02d",s/60,s%60);}
}
