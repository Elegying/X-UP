package io.github.jared.xlowerseek;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.*;

final class VideoDownloadHook implements Application.ActivityLifecycleCallbacks {
    private RemoteSettings settings;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<WeakReference<Object>> screens=new ArrayList<>();
    private final Map<Object, Candidate> players = Collections.synchronizedMap(new WeakHashMap<>());
    private final SurfaceRegistry<Object,View> surfaces=new SurfaceRegistry<>();
    private WeakReference<Activity> activity = new WeakReference<>(null);
    private ImageButton button;
    private DownloadIcon downloadIcon;
    private Object gesturePlayer;
    private View gestureSurface;
    private FullScreenGestureHook gestures;
    private final PlayerProgressSync progressSync=new PlayerProgressSync();

    private Candidate selected;
    private boolean downloading;
    private int downloadProgress=-1;
    private boolean downloadAcknowledged;
    private final Runnable downloadTimeout=()->{if(downloading&&!downloadAcknowledged){downloading=false;downloadText="保存到相册";update();}};
    private String downloadText = "保存到相册";
    private final Runnable tick = new Runnable() { public void run() { try {update();} catch(Throwable e){gestures.reset();gesturePlayer=null;gestureSurface=null;} if (activity.get()!=null) main.postDelayed(this, 500); } };
    private static final class Candidate {
        final String url;
        Candidate(String url) { this.url=url; }
    }
    static void install(Application app, ClassLoader loader,RemoteSettings settings) throws Exception {
        VideoDownloadHook hook = new VideoDownloadHook();hook.settings=settings;
        hook.progressSync.install(loader);
        hook.gestures=new FullScreenGestureHook(new FullScreenGestureHook.Target(){
            public Object player(){return hook.gesturePlayer;}
            public boolean swipeEnabled(){return settings.feature(Feature.SWIPE_SEEK);}
            public boolean holdEnabled(){return settings.feature(Feature.HOLD_SPEED);}
            public void syncProgress(Object player){hook.progressSync.refresh(player);}
            public View surface(){return hook.gestureSurface;}
            public View decor(){Activity a=hook.activity.get();return a==null?null:a.getWindow().getDecorView();}
        });
        hook.gestures.install(loader);
        Class<?> render = Reflect.findClass("com.x.media.playback.u0", loader);
        Class<?> player = Reflect.findClass("androidx.media3.exoplayer.i0", loader);
        for (Method m:render.getDeclaredMethods()) if (m.getName().equals("f") && m.getParameterCount()==19) {
            Hooks.hookMethod(m,new HookCallback() {
                @Override protected void beforeHookedMethod(HookParam p) {
                    try {
                        Object lease=p.args[1];
                        if (lease==null || p.args[0]==null || p.args[2]==null) return;
                        if (!lease.getClass().getName().equals("com.x.media.playback.exoplayerpool.h")) return;
                        Object exo=Reflect.getObjectField(lease,"a");
                        Object mode=Reflect.getObjectField(p.args[2],"a");
                        String url=bestMp4(p.args[0]);
                        if(url!=null) {
                            Candidate old=hook.players.get(exo);
                            hook.setCandidate(exo,url);
                            if(old==null || !old.url.equals(url)) android.util.Log.i("XLowerSeek","video source available");
                        } else hook.setCandidate(exo,null);
                    } catch(Throwable e) { android.util.Log.w("XLowerSeek","Video candidate unavailable: "+e.getClass().getSimpleName()); }
                }
            });
        }
        for (Method m:render.getDeclaredMethods()) if (m.getName().equals("e") && m.getParameterCount()==17) {
            Hooks.hookMethod(m,new HookCallback() {
                @Override protected void beforeHookedMethod(HookParam p) {
                    try {
                        if(p.args[0]==null || p.args[1]==null || p.args[4]==null)return;
                        Object scribe=p.args[1];
                        if(scribe.getClass().getName().equals("com.x.media.playback.j"))scribe=Reflect.getObjectField(scribe,"b");
                        if(scribe.getClass().getName().equals("com.x.media.playback.scribing.i"))scribe=Reflect.getObjectField(scribe,"a");
                        Object media=Reflect.getObjectField(scribe,"b");
                        Object mode=Reflect.getObjectField(p.args[4],"a");
                        String url=bestMp4(media);
                        if(url!=null) {
                            Candidate old=hook.players.get(p.args[0]);
                            hook.setCandidate(p.args[0],url);
                            if(old==null || !old.url.equals(url)) android.util.Log.i("XLowerSeek","download candidate mode="+mode+" mp4=true");
                        } else hook.setCandidate(p.args[0],null);
                    } catch(Throwable e) { android.util.Log.w("XLowerSeek","Active video unavailable: "+e.getClass().getSimpleName()); }
                }
            });
        }
        HookCallback surfaceHook=new HookCallback() {
            @Override protected void afterHookedMethod(HookParam p) {
                if (p.args[0] instanceof View) hook.surfaces.set(p.thisObject,(View)p.args[0]);
                else hook.surfaces.remove(p.thisObject);
            }
        };
        Hooks.findAndHookMethod(player,"M",TextureView.class,surfaceHook);
        Hooks.findAndHookMethod(player,"o",SurfaceView.class,surfaceHook);
        Hooks.findAndHookMethod(player,"release",new HookCallback() {
            @Override protected void beforeHookedMethod(HookParam p) { if(hook.gesturePlayer==p.thisObject){hook.gestures.reset();hook.gesturePlayer=null;hook.gestureSurface=null;} hook.players.remove(p.thisObject); hook.surfaces.remove(p.thisObject);hook.progressSync.release(p.thisObject); }
        });
        for(String name:new String[]{"com.x.media.n","com.x.video.tab.b0"}) {
            Hooks.hookAllConstructors(Reflect.findClass(name,loader),new HookCallback(){
                @Override protected void afterHookedMethod(HookParam p){WeakReference<Object> ref=new WeakReference<>(p.thisObject);hook.main.post(()->hook.screens.add(ref));}
            });
        }
        settings.addListener(()->hook.main.post(()->{hook.gestures.reset();try{hook.update();}catch(RuntimeException ignored){}}));
        app.registerActivityLifecycleCallbacks(hook);
    }
    private void setCandidate(Object player,String url){
        Candidate old=players.get(player);
        boolean changed=old!=null&&!Objects.equals(old.url,url);
        if(url==null)players.remove(player);else players.put(player,new Candidate(url));
        if(changed)main.post(()->{if(gesturePlayer==player){gestures.reset();selected=null;}});
    }
    private static String bestMp4(Object media) {
        if(media==null||Reflect.findMethodExactIfExists(media.getClass(),"e")==null)return null;
        Object variants=Reflect.callMethod(media,"e");
        if(!(variants instanceof Iterable)) return null;
        String best=null; int bitrate=-1;
        for(Object variant:(Iterable<?>)variants) {
            String type=(String)Reflect.getObjectField(variant,"c");
            String url=(String)Reflect.getObjectField(variant,"a");
            Integer rate=(Integer)Reflect.getObjectField(variant,"b");
            int value=rate==null?0:rate;
            if("video/mp4".equals(type) && DownloadPolicy.validUrl(url) && value>bitrate) {best=url;bitrate=value;}
        }
        return best;
    }
    private void update() {
        Activity a=activity.get();
        if(a==null || a.isFinishing()) return;
        Candidate next=null; Object nextPlayer=null; View nextSurface=null; int bestArea=0;
        boolean fullScreen=false;
        for(Iterator<WeakReference<Object>> it=screens.iterator();it.hasNext();) {
            Object screen=it.next().get();if(screen==null){it.remove();continue;}
            try {
                Object state=Reflect.callMethod(Reflect.callMethod(screen,"getLifecycle"),"h");
                if("DESTROYED".equals(state.toString()))it.remove();
                if("RESUMED".equals(state.toString()))fullScreen=true;
            } catch(Throwable ignored){}
        }
        View decor=a.getWindow().getDecorView();
        Map<Object,WeakReference<View>> currentSurfaces=surfaces.snapshot();
        for(Map.Entry<Object,WeakReference<View>> entry:currentSurfaces.entrySet()) {
            if(!fullScreen) continue;
            WeakReference<View> ref=entry.getValue(); View view=ref==null?null:ref.get();
            Rect r=new Rect();
            if(view==null || !view.isAttachedToWindow() || !view.isShown() || !view.getGlobalVisibleRect(r)) continue;
            if(view.getRootView()!=decor || r.width()<decor.getWidth()/2 || r.height()<120) continue;
            int area=r.width()*r.height();
            if(area<view.getWidth()*view.getHeight()*0.65) continue;
            if(area>bestArea) {next=players.get(entry.getKey());nextPlayer=entry.getKey();nextSurface=view;bestArea=area;}
        }
        if(gesturePlayer!=nextPlayer)gestures.reset();
        gesturePlayer=nextPlayer;gestureSurface=nextSurface;
        selected=next;
        if(button==null && next!=null && settings.feature(Feature.DOWNLOAD)) {
            button=new ImageButton(a);downloadIcon=new DownloadIcon();button.setImageDrawable(downloadIcon);button.setScaleType(ImageView.ScaleType.FIT_CENTER);
            button.setContentDescription("保存当前视频到相册");
            int pad=dp(a,14);button.setPadding(pad,pad,pad,pad);button.setMinimumHeight(dp(a,44));
            GradientDrawable bg=new GradientDrawable();bg.setColor(0xCC20242C);bg.setCornerRadius(dp(a,22));button.setBackground(bg);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(a,48),dp(a,48),Gravity.TOP|Gravity.RIGHT);
            lp.topMargin=dp(a,48);lp.rightMargin=dp(a,80);
            ((ViewGroup)decor).addView(button,lp);
            button.setOnClickListener(v -> download());
        }
        if(button!=null) {button.setVisibility(next==null||!settings.feature(Feature.DOWNLOAD)?View.GONE:View.VISIBLE);button.setContentDescription(downloading?downloadText:"保存当前视频到相册");button.setEnabled(!downloading);downloadIcon.setBusy(downloading,downloadProgress);}
    }
    private void download() {
        try{update();}catch(Throwable e){return;}
        Activity a=activity.get(); Candidate candidate=selected;
        if(a==null||candidate==null||downloading||!settings.feature(Feature.DOWNLOAD))return;
        downloadAcknowledged=false;downloadProgress=-1;downloading=true;main.postDelayed(downloadTimeout,30000);downloadText="准备下载…";update();
        ResultReceiver receiver=new ResultReceiver(main) {
            @Override protected void onReceiveResult(int code,Bundle data) {
                if(data==null)return;
                downloadAcknowledged=true;main.removeCallbacks(downloadTimeout);
                if(code==1) {downloadText=data.getString("message","下载中…");downloadProgress=data.getInt("progress",-1);}
                else {
                    downloading=false;downloadText="保存到相册";
                    Activity now=activity.get();
                    if(now!=null)Toast.makeText(now,data.getString("message",code==2?"已保存到相册":"下载失败，请重试"),Toast.LENGTH_LONG).show();
                }
                try{update();}catch(Throwable ignored){}
            }
        };
        Intent intent=new Intent().setClassName("io.github.jared.xlowerseek","io.github.jared.xlowerseek.DownloadActivity")
            .putExtra("url",candidate.url).putExtra("receiver",ResultReceiverTransport.remote(receiver));
        try {a.startActivity(intent);} catch(RuntimeException e) {main.removeCallbacks(downloadTimeout);downloading=false;downloadText="保存到相册";Toast.makeText(a,"请先打开插件设置页，再重试下载",Toast.LENGTH_LONG).show();}
    }
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    public void onActivityResumed(Activity a){if(!a.getPackageName().equals("com.twitter.android"))return;activity=new WeakReference<>(a);main.removeCallbacks(tick);main.post(tick);}
    public void onActivityPaused(Activity a){if(activity.get()==a){gestures.reset();gesturePlayer=null;gestureSurface=null;activity.clear();main.removeCallbacks(tick);if(button!=null&&button.getParent()!=null)((ViewGroup)button.getParent()).removeView(button);button=null;selected=null;}}
    public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){} public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
}
