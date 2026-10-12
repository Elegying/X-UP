package io.github.jared.xlowerseek;

import android.app.Activity;
import android.app.Application;
import android.app.AlertDialog;
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
    private VideoTargets targets;
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
    private VideoDownloadClient downloads;
    private String downloadText = "保存到相册";
    private final Runnable tick = new Runnable() { public void run() { try {update();} catch(Throwable e){gestures.reset();gesturePlayer=null;gestureSurface=null;} if (activity.get()!=null) main.postDelayed(this, 500); } };
    private static final class Candidate {
        final String url;
        Candidate(String url) { this.url=url; }
    }
    static void install(Application app, TargetResolver resolver,RemoteSettings settings) throws Exception {
        VideoTargets targets=new VideoTargets(resolver);
        ClassLoader loader=resolver.loader;
        VideoDownloadHook hook = new VideoDownloadHook();hook.settings=settings;hook.targets=targets;
        hook.downloads = new VideoDownloadClient(app, new VideoDownloadClient.Listener() {
            public void changed(boolean busy, int progress, String message) {
                hook.downloading = busy; hook.downloadProgress = progress; hook.downloadText = message;
                try { hook.update(); } catch (RuntimeException ignored) {}
            }
            public void launch(String id, String capability) {
                Activity a = hook.activity.get();
                if (a == null || a.isFinishing()) throw new IllegalStateException();
                a.startActivity(new Intent().setClassName("io.github.jared.xlowerseek", "io.github.jared.xlowerseek.DownloadActivity")
                        .putExtra("id", id).putExtra("capability", capability));
            }
            public void completed(String message) {
                Activity a = hook.activity.get();
                if (a != null) Toast.makeText(a, message, Toast.LENGTH_LONG).show();
            }
        });
        hook.progressSync.install(resolver,targets);
        hook.gestures=new FullScreenGestureHook(new FullScreenGestureHook.Target(){
            public Object player(){return hook.gesturePlayer;}
            public boolean swipeEnabled(){return settings.feature(Feature.SWIPE_SEEK);}
            public boolean holdEnabled(){return settings.feature(Feature.HOLD_SPEED);}
            public void syncProgress(Object player){hook.progressSync.refresh(player);}
            public View surface(){return hook.gestureSurface;}
            public View decor(){Activity a=hook.activity.get();return a==null?null:a.getWindow().getDecorView();}
        },targets);
        hook.gestures.install(loader);
        Hooks.hookMethod(targets.renderLease,new HookCallback() {
            @Override protected void beforeHookedMethod(HookParam p) {
                Object lease=p.args[1];
                if(!targets.lease.isInstance(lease))return;
                Object exo=VideoMembers.read(targets.leasePlayer,lease);
                hook.setCandidate(exo,targets.bestMp4(p.args[0]));
            }
        });
        Hooks.hookMethod(targets.renderPlayer,new HookCallback() {
            @Override protected void beforeHookedMethod(HookParam p) {
                if(p.args[0]!=null)hook.setCandidate(p.args[0],targets.bestMp4(targets.mediaFromScribe(p.args[1])));
            }
        });
        HookCallback surfaceHook=new HookCallback() {
            @Override protected void afterHookedMethod(HookParam p) {
                if(p.hasThrowable())return;
                if (p.args[0] instanceof View) hook.surfaces.set(p.thisObject,(View)p.args[0]);
                else hook.surfaces.remove(p.thisObject);
            }
        };
        Hooks.hookMethod(targets.texture,surfaceHook);
        Hooks.hookMethod(targets.surface,surfaceHook);
        Hooks.hookMethod(targets.release,new HookCallback() {
            @Override protected void beforeHookedMethod(HookParam p) { if(hook.gesturePlayer==p.thisObject){hook.gestures.reset();hook.gesturePlayer=null;hook.gestureSurface=null;} hook.players.remove(p.thisObject); hook.surfaces.remove(p.thisObject);hook.progressSync.release(p.thisObject); }
        });
        for(Class<?> screen:targets.screens) {
            Hooks.hookAllConstructors(screen,new HookCallback(){
                @Override protected void afterHookedMethod(HookParam p){if(p.hasThrowable())return;WeakReference<Object> ref=new WeakReference<>(p.thisObject);hook.main.post(()->hook.screens.add(ref));}
            });
        }
        settings.addListener(()->hook.main.post(()->{hook.gestures.reset();try{hook.update();}catch(RuntimeException ignored){}}));
        app.registerActivityLifecycleCallbacks(hook);
    }
    private void setCandidate(Object player,String url){
        Candidate old=players.get(player);
        boolean changed=old!=null&&!Objects.equals(old.url,url);
        if(url==null)players.remove(player);else players.put(player,new Candidate(url));
        if(url!=null&&(old==null||changed))android.util.Log.i("XLowerSeek","video source available");
        if(changed)main.post(()->{if(gesturePlayer==player){gestures.reset();selected=null;}});
    }
    private void update() {
        Activity a=activity.get();
        if(a==null || a.isFinishing()) return;
        Candidate next=null; Object nextPlayer=null; View nextSurface=null; int bestArea=0;
        boolean fullScreen=false;
        for(Iterator<WeakReference<Object>> it=screens.iterator();it.hasNext();) {
            Object screen=it.next().get();if(screen==null){it.remove();continue;}
            try {
                String state=targets.state(screen);
                if("DESTROYED".equals(state))it.remove();
                if("RESUMED".equals(state))fullScreen=true;
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
        if(button==null && fullScreen && ((next!=null && settings.feature(Feature.DOWNLOAD)) || downloading)) {
            button=new ImageButton(a);downloadIcon=new DownloadIcon();button.setImageDrawable(downloadIcon);button.setScaleType(ImageView.ScaleType.FIT_CENTER);
            button.setContentDescription("保存当前视频到相册");
            int pad=dp(a,14);button.setPadding(pad,pad,pad,pad);button.setMinimumHeight(dp(a,44));
            GradientDrawable bg=new GradientDrawable();bg.setColor(0xCC20242C);bg.setCornerRadius(dp(a,22));button.setBackground(bg);
            FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(dp(a,48),dp(a,48),Gravity.TOP|Gravity.RIGHT);
            lp.topMargin=dp(a,48);lp.rightMargin=dp(a,80);
            ((ViewGroup)decor).addView(button,lp);
            button.setOnClickListener(v -> download());
        }
        if(button!=null) {button.setVisibility(!fullScreen||((next==null||!settings.feature(Feature.DOWNLOAD))&&!downloading)?View.GONE:View.VISIBLE);button.setContentDescription(downloading?downloadText+"，点击查看或取消":"保存当前视频到相册");button.setEnabled(true);downloadIcon.setBusy(downloading,downloadProgress);}
    }
    private void download() {
        try { update(); } catch (Throwable e) { return; }
        Activity a = activity.get();
        if (a == null) return;
        if (downloading) {
            Runnable cancel = downloads.cancellation();
            downloads.refresh();
            new AlertDialog.Builder(a).setTitle("视频下载").setMessage(downloadText)
                    .setNegativeButton("继续下载", null)
                    .setPositiveButton("取消下载", (dialog, which) -> cancel.run()).show();
            return;
        }
        if (selected != null && settings.feature(Feature.DOWNLOAD)) downloads.start(selected.url);
    }
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    public void onActivityResumed(Activity a){if(!a.getPackageName().equals("com.twitter.android"))return;activity=new WeakReference<>(a);downloads.visible(true);main.removeCallbacks(tick);main.post(tick);}
    public void onActivityPaused(Activity a){if(activity.get()==a){downloads.visible(false);gestures.reset();gesturePlayer=null;gestureSurface=null;activity.clear();main.removeCallbacks(tick);if(button!=null&&button.getParent()!=null)((ViewGroup)button.getParent()).removeView(button);button=null;selected=null;}}
    public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){} public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
}
