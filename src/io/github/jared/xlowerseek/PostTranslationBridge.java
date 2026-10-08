package io.github.jared.xlowerseek;

import android.os.Looper;
import android.util.Log;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;

/** X 12.31: gives list/quote presenters the native translation state used by detail posts.
 * The Compose-owned scope is disposed with its cell. No text is sent to an external service.
 */
final class PostTranslationBridge {
    private final Map<Object,Object> locales=new WeakHashMap<>();
    private final ThreadLocal<Object> eligibleModel=new ThreadLocal<>();
    private final RemoteSettings settings;
    private final Class<?> nativePresenter,scribe;
    private final Method scope;
    private boolean warned;
    private PostTranslationBridge(ClassLoader loader,RemoteSettings settings){
        this.settings=settings;
        nativePresenter=Reflect.findClass("com.x.urt.items.post.translate.grok.o",loader);
        scribe=Reflect.findClass("com.x.scribing.post.a",loader);
        scope=Reflect.findMethodExact(Reflect.findClass("androidx.compose.runtime.g0",loader),"j",
            Reflect.findClass("androidx.compose.runtime.Composer",loader));
    }
    static void install(ClassLoader loader,RemoteSettings settings){
        PostTranslationBridge bridge=new PostTranslationBridge(loader,settings);
        Class<?> list=Reflect.findClass("com.x.urt.items.post.translate.grok.b",loader);
        Hooks.hookAllConstructors(list,new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p){
                if(!p.hasThrowable()&&p.args.length==8)bridge.locales.put(p.thisObject,p.args[6]);
            }
        });
        // Only the exact model being rendered by our native presenter becomes eligible.
        Hooks.hookAllMethods(Reflect.findClass("com.x.models.o1",loader),"q",new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p){
                if(!p.hasThrowable()&&bridge.eligibleModel.get()==p.thisObject)p.setResult(true);
            }
        });
        Hooks.hookAllMethods(list,"a",new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p)throws Throwable{
                if(p.hasThrowable()||p.getResult()!=null||Looper.myLooper()!=Looper.getMainLooper())return;
                bridge.render(p);
            }
        });
    }
    private void render(HookCallback.HookParam p)throws Throwable{
        Object owner=p.thisObject,model=Reflect.getObjectField(owner,"b");
        if(!settings.translate||settings.feature(Feature.LOCAL_TEXT)||!settings.feature(Feature.NATIVE_TRANSLATE)||!locales.containsKey(owner)||!scribe.isInstance(Reflect.getObjectField(owner,"c")))return;
        if(!PostTranslationPolicy.eligible((String)Reflect.callMethod(model,"s"),(String)Reflect.callMethod(model,"getText")))return;
        Object composer=p.args[0];
        Reflect.callMethod(composer,"h0",0x58555031);
        Object previous=eligibleModel.get();
        try{
            Object coroutineScope=scope.invoke(null,composer);
            Object presenter=Reflect.callMethod(composer,"T");
            if(!nativePresenter.isInstance(presenter)){
                presenter=Reflect.newInstance(nativePresenter,Reflect.getObjectField(owner,"a"),model,coroutineScope,
                    Reflect.getObjectField(owner,"c"),Reflect.getObjectField(owner,"e"),Reflect.getObjectField(owner,"d"),
                    Reflect.getObjectField(owner,"f"),locales.get(owner),Reflect.getObjectField(owner,"g"));
                Reflect.callMethod(composer,"r0",presenter);
            }
            eligibleModel.set(model);
            Object state=Reflect.callMethod(presenter,"d",composer);
            if(settings.translate)p.setResult(state);
        }catch(Throwable error){
            if(!warned){warned=true;Log.w("XLowerSeek","List translation bridge unavailable; original retained",error);}
        }finally{
            if(previous==null)eligibleModel.remove();else eligibleModel.set(previous);
            Reflect.callMethod(composer,"t",false);
        }
    }
}
