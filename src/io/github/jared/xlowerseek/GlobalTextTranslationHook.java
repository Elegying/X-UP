package io.github.jared.xlowerseek;

import android.app.*;
import android.os.*;
import android.text.*;
import android.text.style.*;
import android.widget.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;

/** Local translation of display text. Input, URLs and interactive annotation ranges stay intact. */
final class GlobalTextTranslationHook {
    private final GoogleAttribution attribution=new GoogleAttribution();private WeakReference<Activity> currentActivity=new WeakReference<>(null);
    private final RemoteSettings settings;
    private final LocalTranslationClient client;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Class<?> annotated,range;
    private final Method mutableState;
    private final LinkedHashMap<String,Observer> observers=new LinkedHashMap<>(16,.75f,true);
    private static final int BINDING_TAG=0x7e585550;
    private final Map<TextView,WeakReference<ViewBinding>> views=new WeakHashMap<>();
    private final WeakIdentityMap<CharSequence,CharSequence> originals=new WeakIdentityMap<>();
    private final Set<HookCallback.HookParam> entered=Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean contextRefreshPending;
    private int depth,resumed;
    private boolean replacing,enabled;
    private final Map<LocalTranslationClient.Listener,PostContextIndex.Binding> contextBindings=new WeakHashMap<>();
    private String engine="";
    private final Runnable visibilityTick=new Runnable(){public void run(){if(resumed>0){refreshPending();main.postDelayed(()->client.pruneInvisible(),250);main.postDelayed(this,1000);}}};
    private long retry;private long revision=-1;private boolean localEnabled,contextEnabled;
    private GlobalTextTranslationHook(Application app,ClassLoader loader,RemoteSettings settings){
        this.settings=settings;enabled=settings.translate;client=new LocalTranslationClient(app,settings);
        annotated=Reflect.findClass("androidx.compose.ui.text.h",loader);
        range=Reflect.findClass("androidx.compose.ui.text.f",loader);
        mutableState=Reflect.findMethodExact(Reflect.findClass("androidx.compose.runtime.i",loader),"r",Object.class);
    }
    private final class Observer implements LocalTranslationClient.Listener {
        final Object state;int version;
        Observer()throws ReflectiveOperationException{state=mutableState.invoke(null,0);}
        public void changed(){Reflect.callMethod(state,"setValue",++version);}
    }
    static void install(Application app,ClassLoader loader,RemoteSettings settings){
        GlobalTextTranslationHook hook=new GlobalTextTranslationHook(app,loader,settings);
        // The X wrapper handles rich-text hit testing, so translate before it captures the text.
        hook.compose(Reflect.findClass("com.x.ui.common.ports.text.f",loader),new String[]{"a","b","d"});
        // Foundation BasicText only; editable BasicTextField is explicitly excluded below.
        hook.compose(Reflect.findClass("androidx.compose.foundation.text.e1",loader),new String[]{"b","c"});
        Class<?> inputs=Reflect.findClass("androidx.compose.foundation.text.k0",loader);
        for(Method m:inputs.getDeclaredMethods())if(m.getReturnType()==void.class&&hasComposer(m)){
            Hooks.hookMethod(m,new HookCallback(){
                protected void beforeHookedMethod(HookParam p){if(Looper.myLooper()==Looper.getMainLooper()){hook.depth++;hook.entered.add(p);}}
                protected void afterHookedMethod(HookParam p){if(Looper.myLooper()==Looper.getMainLooper()&&hook.entered.remove(p))hook.depth--;}
            });
        }
        hook.textViews();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
            public void onActivityResumed(Activity a){hook.resumed++;hook.currentActivity=new WeakReference<>(a);hook.client.setForeground(true);hook.updateSettings();hook.main.removeCallbacks(hook.visibilityTick);hook.main.post(hook.visibilityTick);hook.invalidate();}
            public void onActivityPaused(Activity a){hook.attribution.clear();if(hook.currentActivity.get()==a)hook.currentActivity.clear();hook.resumed=Math.max(0,hook.resumed-1);hook.client.setForeground(hook.resumed>0);hook.main.postDelayed(()->{if(hook.resumed==0){hook.client.suspend();hook.main.removeCallbacks(hook.visibilityTick);}},1000);}
            public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){}
            public void onActivityStopped(Activity a){} public void onActivityDestroyed(Activity a){}
            public void onActivitySaveInstanceState(Activity a,Bundle b){}
        });
        settings.addListener(()->hook.main.post(hook::updateSettings));
        PostContextHook.INDEX.onChange(()->hook.main.post(()->{
            if(hook.contextRefreshPending||hook.resumed==0||!settings.feature(Feature.LOCAL_TEXT)||!settings.translate||!settings.engine.equals("tencent")||!settings.feature(Feature.LOCAL_CONTEXT))return;
            hook.contextRefreshPending=true;
            hook.main.postDelayed(()->{hook.contextRefreshPending=false;if(hook.resumed>0)hook.invalidate();},200);
        }));
    }
    private void updateSettings(){
        if(retry!=settings.retry){retry=settings.retry;client.retryFailures();invalidate();}
        if(engine.equals(settings.engine)&&enabled==settings.translate&&revision==settings.localRevision&&localEnabled==settings.feature(Feature.LOCAL_TEXT)&&contextEnabled==settings.feature(Feature.LOCAL_CONTEXT))return;
        engine=settings.engine;enabled=settings.translate;revision=settings.localRevision;localEnabled=settings.feature(Feature.LOCAL_TEXT);contextEnabled=settings.feature(Feature.LOCAL_CONTEXT);
        client.clear();client.setForeground(resumed>0);contextBindings.clear();invalidate();
    }
    private void refreshPending(){
        client.refreshPending();
        // TextViews may become visible without being rebound; retain their visibility checks.
        for(WeakReference<ViewBinding> ref:new ArrayList<>(views.values())){ViewBinding binding=ref.get();if(binding!=null&&binding.waiting)binding.changed();}
    }
    private void invalidate(){
        attribution.update(currentActivity.get(),resumed>0&&settings.translate&&settings.feature(Feature.LOCAL_TEXT)&&settings.engine.equals("mlkit"));
        for(Observer observer:new ArrayList<>(observers.values()))observer.changed();
        for(WeakReference<ViewBinding> ref:new ArrayList<>(views.values())){ViewBinding binding=ref.get();if(binding!=null)binding.changed();}
    }
    private static boolean hasComposer(Method m){for(Class<?> c:m.getParameterTypes())if(c.getName().equals("androidx.compose.runtime.Composer"))return true;return false;}
    private void compose(Class<?> type,String[] names){
        for(Method method:type.getDeclaredMethods()){
            if(!Arrays.asList(names).contains(method.getName())||!hasComposer(method))continue;
            Class<?>[] parameters=method.getParameterTypes();
            if(parameters[0]!=String.class&&parameters[0]!=annotated)continue;
            int position=-1;for(int i=0;i<parameters.length;i++)if(parameters[i].getName().equals("androidx.compose.runtime.Composer"))position=i;
            final int composer=position;
            Hooks.hookMethod(method,new HookCallback(){
                @Override protected void beforeHookedMethod(HookParam p)throws Throwable{
                    if(Looper.myLooper()!=Looper.getMainLooper()||depth>0)return;
                    depth++;entered.add(p);
                    CharSequence source=(CharSequence)p.args[0];if(source==null||source.length()>8192)return;
                    CharSequence incoming=source;
                    CharSequence remembered=originals.get(source);if(remembered!=null)source=remembered;
                    String key=source.toString();
                    Observer observer=observers.get(key);
                    if(observer==null){
                        if(!settings.translate||!settings.feature(Feature.LOCAL_TEXT)||IdentityTextHook.INDEX.contains(key)||!PostTranslationPolicy.eligible(null,key)){
                            if(source!=incoming){p.args[0]=source;if(composer+1<p.args.length&&p.args[composer+1] instanceof Integer)p.args[composer+1]=(((Integer)p.args[composer+1])&~14)|5;}
                            return;
                        }
                        if(observers.size()>=256)observers.remove(observers.keySet().iterator().next());
                        observer=new Observer();observers.put(key,observer);
                    }
                    Reflect.callMethod(observer.state,"getValue"); // subscribes the active restart scope
                    Object translated=settings.translate&&resumed>0?translateCompose(source,observer):source;
                    if(!translated.equals(incoming)){
                        if(!translated.equals(source)){originals.put((CharSequence)translated,source);}
                        p.args[0]=translated;
                        if(composer+1<p.args.length&&p.args[composer+1] instanceof Integer)
                            p.args[composer+1]=(((Integer)p.args[composer+1])&~14)|5;
                    }
                }
                @Override protected void afterHookedMethod(HookParam p){if(Looper.myLooper()==Looper.getMainLooper()&&entered.remove(p))depth--;}
            });
        }
    }
    private Object translateCompose(CharSequence source,Observer observer){
        if(source instanceof String)return translatePlain(source.toString(),Collections.emptyList(),observer).text;
        List<?> annotations=(List<?>)Reflect.getObjectField(source,"a");
        List<TextTranslationPlan.Range> ranges=new ArrayList<>();
        if(annotations!=null)for(Object item:annotations){
            String payload=Reflect.getObjectField(item,"a").getClass().getName();
            boolean protect=!payload.equals("androidx.compose.ui.text.s0")&&!payload.equals("androidx.compose.ui.text.f0");
            ranges.add(new TextTranslationPlan.Range(Reflect.getIntField(item,"b"),Reflect.getIntField(item,"c"),protect));
        }
        TextTranslationPlan.Result result=translatePlain(source.toString(),ranges,observer);
        if(result.text.equals(source.toString()))return source;
        List<Object> mapped=new ArrayList<>();
        if(annotations!=null)for(Object item:annotations)mapped.add(Reflect.newInstance(range,
            Reflect.getObjectField(item,"d"),result.offset(Reflect.getIntField(item,"b")),result.offset(Reflect.getIntField(item,"c")),Reflect.getObjectField(item,"a")));
        return Reflect.newInstance(annotated,mapped,result.text);
    }
    private TextTranslationPlan.Result translatePlain(String text,List<TextTranslationPlan.Range> ranges,LocalTranslationClient.Listener listener){
        WholeTextPlan plan=new WholeTextPlan(text,ranges);
        if(!settings.feature(Feature.LOCAL_TEXT)||IdentityTextHook.INDEX.contains(text)||!PostTranslationPolicy.eligible(null,text))return plan.decode(plan.encoded);
        String context="";
        if(settings.engine.equals("tencent")&&settings.feature(Feature.LOCAL_CONTEXT))context=contextBindings.computeIfAbsent(listener,k->new PostContextIndex.Binding()).resolve(PostContextHook.INDEX.forText(text)).text;
        return plan.decode(client.lookup(plan.encoded,context,listener));
    }

    private void textViews(){
        Hooks.findAndHookMethod(TextView.class,"setText",CharSequence.class,TextView.BufferType.class,new HookCallback(){
            protected void beforeHookedMethod(HookParam p){
                if(replacing||depth>0||Looper.myLooper()!=Looper.getMainLooper()||p.thisObject instanceof EditText)return;
                TextView view=(TextView)p.thisObject;CharSequence source=(CharSequence)p.args[0];
                if(source==null||source.length()>8192||view.onCheckIsTextEditor()||view.getTransformationMethod() instanceof android.text.method.PasswordTransformationMethod)return;
                ViewBinding binding=new ViewBinding(view,source,(TextView.BufferType)p.args[1]);view.setTag(BINDING_TAG,binding);views.put(view,new WeakReference<>(binding));
                view.post(binding::changed);
                p.args[0]=binding.translated();
            }
        });
    }
    private final class ViewBinding implements LocalTranslationClient.Listener {
        final WeakReference<TextView> view;final CharSequence original;final TextView.BufferType type;boolean waiting=true;
        ViewBinding(TextView view,CharSequence text,TextView.BufferType type){this.view=new WeakReference<>(view);original=text;this.type=type;}
        CharSequence translated(){
            if(!settings.translate||resumed==0)return original;
            if(!settings.feature(Feature.LOCAL_TEXT)||IdentityTextHook.INDEX.contains(original.toString())||!PostTranslationPolicy.eligible(null,original.toString())){waiting=false;return original;}
            TextView current=view.get();if(current==null||!current.isShown()||!current.getGlobalVisibleRect(new android.graphics.Rect()))return original;
            List<TextTranslationPlan.Range> ranges=new ArrayList<>();
            Object[] spans=original instanceof Spanned?((Spanned)original).getSpans(0,original.length(),Object.class):new Object[0];
            for(Object span:spans){if(span instanceof NoCopySpan)continue;Spanned s=(Spanned)original;ranges.add(new TextTranslationPlan.Range(s.getSpanStart(span),s.getSpanEnd(span),span instanceof ClickableSpan||span instanceof ReplacementSpan));}
            TextTranslationPlan.Result result=translatePlain(original.toString(),ranges,this);
            waiting=result.text.equals(original.toString());
            if(waiting)return original;

            SpannableString out=new SpannableString(result.text);
            for(Object span:spans){if(span instanceof NoCopySpan)continue;Spanned s=(Spanned)original;out.setSpan(span,result.offset(s.getSpanStart(span)),result.offset(s.getSpanEnd(span)),s.getSpanFlags(span));}
            return out;
        }
        public void changed(){
            TextView target=view.get();if(target==null||target.getTag(BINDING_TAG)!=this||!target.isAttachedToWindow())return;
            try{replacing=true;CharSequence text=translated();if(!TextUtils.equals(target.getText(),text))target.setText(text,type);}
            finally{replacing=false;}
        }
    }
}
