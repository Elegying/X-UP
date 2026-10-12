package io.github.jared.xlowerseek;

import android.view.SurfaceView;
import android.view.TextureView;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

/** Discover video contracts once; runtime callbacks never search arbitrary application objects. */
final class VideoTargets implements PlaybackAccess {
    final Class<?> player, exo, media, lease, scribe, variant;
    final Method renderLease, renderPlayer, texture, surface, release, variants;
    final Class<?>[] screens;
    final Method[] screenLifecycle;
    final Method lifecycleState;
    final Field leasePlayer, variantUrl, variantType, variantRate;
    private final Method duration, position, isAd, isPlaying, seek, getSpeed, setSpeed;
    private final Constructor<?> makeSpeed;
    private final Field speed, pitch;

    VideoTargets(TargetResolver resolver)throws Exception {
        exo=Reflect.findClass("androidx.media3.exoplayer.ExoPlayer",resolver.loader);
        player=resolver.type("video.player",ClassMatcher.create().usingEqStrings("ExoPlayerImpl","Replacing existing SurfaceTextureListener."),
            type->exo.isAssignableFrom(type));
        texture=resolver.method("video.texture",MethodMatcher.create().declaredClass(player).paramTypes(TextureView.class)
            .returnType(void.class).usingEqStrings("Replacing existing SurfaceTextureListener."));
        surface=resolver.method("video.surface",MethodMatcher.create().declaredClass(player).paramTypes(SurfaceView.class)
            .returnType(void.class).usingNumbers(10000));
        release=Reflect.findMethodExact(player,"release");
        Class<?> configuration=resolver.type("video.configuration",ClassMatcher.create().usingStrings("PlayerConfiguration(videoScale="));
        renderLease=resolver.method("video.render-lease",MethodMatcher.create().paramCount(6,64).returnType(void.class)
            .declaredClass(ClassMatcher.create().className("com.x.media.playback.",StringMatchType.StartsWith)),
            method->{
                Class<?>[] p=method.getParameterTypes();
                return Modifier.isStatic(method.getModifiers())&&p.length>=6&&p[2]==configuration
                    &&p[0].isInterface()&&p[1].isInterface()&&p[3].isInterface()
                    &&p[4].getName().equals("androidx.compose.ui.Modifier")&&compositionSuffix(p)
                    &&occurrences(p,p[0])==1&&occurrences(p,p[1])==1&&occurrences(p,p[2])==1&&occurrences(p,p[3])==1;
            });
        media=renderLease.getParameterTypes()[0];
        scribe=renderLease.getParameterTypes()[3];
        variants=VideoMembers.method(media,m->m.getParameterCount()==0&&Iterable.class.isAssignableFrom(m.getReturnType()));
        renderPlayer=VideoMembers.method(renderLease.getDeclaringClass(),m->Modifier.isStatic(m.getModifiers())
            &&m.getParameterCount()>=6&&m.getReturnType()==void.class&&m.getParameterTypes()[0]==exo
            &&m.getParameterTypes()[1]==scribe&&m.getParameterTypes()[2].getName().equals("androidx.compose.ui.Modifier")
            &&m.getParameterTypes()[3]==boolean.class&&m.getParameterTypes()[4]==configuration
            &&occurrences(m.getParameterTypes(),exo)==1&&occurrences(m.getParameterTypes(),scribe)==1
            &&occurrences(m.getParameterTypes(),configuration)==1&&compositionSuffix(m.getParameterTypes()));
        lease=resolver.type("video.player-lease",ClassMatcher.create().addInterface(renderLease.getParameterTypes()[1].getName()).addFieldForType(exo));
        leasePlayer=VideoMembers.field(lease,exo);
        variant=resolver.type("video.variant",ClassMatcher.create().usingStrings("MediaVariant(url=",", bitRate=",", contentType="));
        Constructor<?> makeVariant=VideoMembers.constructor(variant,String.class,Integer.class,String.class);
        String probeUrl="https://video.twimg.com/xup-compatibility-probe.mp4";
        Object variantProbe=makeVariant.newInstance(probeUrl,73,"video/mp4");
        if(!variantProbe.toString().contains("url="+probeUrl)||!variantProbe.toString().contains("contentType=video/mp4"))
            throw new IllegalStateException("Media variant contract changed");
        variantUrl=VideoMembers.field(variant,f->f.getType()==String.class&&probeUrl.equals(VideoMembers.read(f,variantProbe)));
        variantType=VideoMembers.field(variant,f->f.getType()==String.class&&"video/mp4".equals(VideoMembers.read(f,variantProbe)));
        variantRate=VideoMembers.field(variant,Integer.class);
        screens=new Class<?>[]{
            resolver.type("video.gallery-screen",ClassMatcher.create().usingEqStrings("x_lite_immersive_media_gallery_chrome_enabled"),VideoTargets::screenContract),
            resolver.type("video.immersive-screen",ClassMatcher.create().usingEqStrings("xlite_video_playback_double_tap_like","xlite_video_playback_profile_swipe"),VideoTargets::screenContract)
        };
        screenLifecycle=new Method[]{Reflect.findMethodExact(screens[0],"getLifecycle"),Reflect.findMethodExact(screens[1],"getLifecycle")};
        if(screenLifecycle[0].getReturnType()!=screenLifecycle[1].getReturnType())throw new IllegalStateException("Lifecycle contract changed");
        lifecycleState=VideoMembers.method(screenLifecycle[0].getReturnType(),m->m.getParameterCount()==0&&m.getReturnType().isEnum());
        Enum.valueOf(lifecycleState.getReturnType().asSubclass(Enum.class),"RESUMED");
        duration=Reflect.findMethodExact(player,"getDuration");
        position=Reflect.findMethodExact(player,"getCurrentPosition");
        isPlaying=Reflect.findMethodExact(player,"isPlaying");
        seek=Reflect.findMethodExact(player,"seekTo",long.class);
        isAd=resolver.method("video.is-ad",MethodMatcher.create().declaredClass(player).paramCount(0).returnType(boolean.class)
            .addCaller(MethodMatcher.create().declaredClass(player).usingEqStrings("seekTo ignored because an ad is playing")));
        Class<?> parameters=resolver.type("video.playback-parameters",ClassMatcher.create().usingEqStrings("PlaybackParameters(speed=%.2f, pitch=%.2f)"));
        getSpeed=VideoMembers.method(player,parameters);
        setSpeed=VideoMembers.method(player,void.class,parameters);
        makeSpeed=VideoMembers.constructor(parameters,float.class,float.class);
        Object speedProbe=makeSpeed.newInstance(2f,1.125f);
        speed=VideoMembers.field(parameters,f->f.getType()==float.class&&Float.valueOf(2f).equals(VideoMembers.read(f,speedProbe)));
        pitch=VideoMembers.field(parameters,f->f.getType()==float.class&&Float.valueOf(1.125f).equals(VideoMembers.read(f,speedProbe)));
        if(!speedProbe.toString().startsWith("PlaybackParameters(speed=2.00, pitch="))throw new IllegalStateException("Playback speed contract changed");
    }

    private static int occurrences(Class<?>[] parameters,Class<?> type) {
        int count=0;for(Class<?> parameter:parameters)if(parameter==type)count++;return count;
    }

    private static boolean compositionSuffix(Class<?>[] parameters) {
        int composer=-1;
        for(int i=0;i<parameters.length;i++)if(parameters[i].getName().equals("androidx.compose.runtime.Composer")){
            if(composer>=0)return false;composer=i;
        }
        if(composer<5||composer==parameters.length-1)return false;
        for(int i=composer+1;i<parameters.length;i++)if(parameters[i]!=int.class)return false;
        return true;
    }

    private static boolean screenContract(Class<?> type) {
        try {
            if(type.isInterface()||Modifier.isAbstract(type.getModifiers()))return false;
            Method lifecycle=Reflect.findMethodExactIfExists(type,"getLifecycle");
            if(lifecycle==null)return false;
            Class<?> state=VideoMembers.method(lifecycle.getReturnType(),m->m.getParameterCount()==0&&m.getReturnType().isEnum()).getReturnType();
            return state.getDeclaredField("RESUMED").getType()==state&&state.getDeclaredField("DESTROYED").getType()==state;
        }catch(ReflectiveOperationException|RuntimeException e){return false;}
    }

    Object mediaFromScribe(Object value) {
        Set<Object> visited=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Object> found=Collections.newSetFromMap(new IdentityHashMap<>());
        collectMedia(value,3,visited,found);
        return found.size()==1?found.iterator().next():null;
    }

    private void collectMedia(Object value,int depth,Set<Object> visited,Set<Object> found) {
        if(value==null||!visited.add(value))return;
        if(media.isInstance(value)){found.add(value);return;}
        if(depth==0||!scribe.isInstance(value))return;
        for(Field field:value.getClass().getDeclaredFields()) {
            if(Modifier.isStatic(field.getModifiers())||(!scribe.isAssignableFrom(field.getType())&&!media.isAssignableFrom(field.getType())))continue;
            field.setAccessible(true);
            collectMedia(VideoMembers.read(field,value),depth-1,visited,found);
        }
    }

    String bestMp4(Object value) {
        if(!media.isInstance(value))return null;
        Object values=VideoMembers.invoke(variants,value);
        if(!(values instanceof Iterable))return null;
        String best=null;int bitrate=-1;
        for(Object item:(Iterable<?>)values) {
            if(!variant.isInstance(item))continue;
            String type=(String)VideoMembers.read(variantType,item),url=(String)VideoMembers.read(variantUrl,item);
            Integer rate=(Integer)VideoMembers.read(variantRate,item);int current=rate==null?0:rate;
            if("video/mp4".equals(type)&&DownloadPolicy.validUrl(url)&&current>bitrate){best=url;bitrate=current;}
        }
        return best;
    }

    String state(Object screen) {
        for(int i=0;i<screens.length;i++)if(screens[i].isInstance(screen)) {
            Object lifecycle=VideoMembers.invoke(screenLifecycle[i],screen);
            return ((Enum<?>)VideoMembers.invoke(lifecycleState,lifecycle)).name();
        }
        return "DESTROYED";
    }

    public long duration(Object player){return ((Number)VideoMembers.invoke(duration,player)).longValue();}
    public long position(Object player){return ((Number)VideoMembers.invoke(position,player)).longValue();}
    public boolean ad(Object player){return Boolean.TRUE.equals(VideoMembers.invoke(isAd,player));}
    public boolean playing(Object player){return Boolean.TRUE.equals(VideoMembers.invoke(isPlaying,player));}
    public void seek(Object player,long value){VideoMembers.invoke(seek,player,value);}
    public Object speed(Object player){return VideoMembers.invoke(getSpeed,player);}
    public Object doubled(Object current){
        try{return makeSpeed.newInstance(2f,pitch.getFloat(current));}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    public void speed(Object player,Object value){VideoMembers.invoke(setSpeed,player,value);}
    public float speedValue(Object current){return ((Number)VideoMembers.read(speed,current)).floatValue();}
}
