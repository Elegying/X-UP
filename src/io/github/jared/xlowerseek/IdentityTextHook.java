package io.github.jared.xlowerseek;

import java.lang.reflect.*;
import java.util.*;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.UsingFieldData;

/** Names are discovered through semantic properties and kept out of local translation. */
final class IdentityTextHook {
    static final IdentityTextIndex INDEX=new IdentityTextIndex();
    static void install(TargetResolver resolver)throws Exception {
        for(String prefix:new String[]{"ProfileUser(id=","MinimalUser(id=","SocialProofUser(displayName="}){
            Class<?> type=ContentTargets.model(resolver,prefix);
            Field[] fields=prefix.startsWith("SocialProof")?socialProof(type):userFields(resolver,type,prefix);
            Field name=fields[0],handle=fields[1];
            if(!prefix.startsWith("SocialProof"))for(Field property:fields){
                Method getter=ContentTargets.getter(resolver,prefix+property.getName(),type,property);
                Hooks.hookMethod(getter,new HookCallback(){protected void afterHookedMethod(HookParam p){if(!p.hasThrowable())remember(p.getResult());}});
            }
            Hooks.hookAllConstructors(type,new HookCallback(){protected void afterHookedMethod(HookParam p)throws Throwable {
                if(!p.hasThrowable()){remember(name.get(p.thisObject));remember(handle.get(p.thisObject));}
            }});
        }
    }
    private static Field[] userFields(TargetResolver resolver,Class<?> type,String prefix)throws Exception {
        MethodData description=resolver.dex().getMethodData(type.getDeclaredMethod("toString"));
        List<String> labels=description.getUsingStrings();List<UsingFieldData> reads=description.getUsingFields();
        if(labels.size()<3||reads.size()<3||!new HashSet<>(labels.subList(0,3)).equals(new HashSet<>(Arrays.asList(prefix,", screenName=",", name="))))
            throw new NoSuchFieldException("Identity model property contract");
        Field id=getterField(resolver,type,"getId"),name=getterField(resolver,type,"getName");
        Field first=reads.get(0).getField().getFieldInstance(resolver.loader),handle=reads.get(1).getField().getFieldInstance(resolver.loader),third=reads.get(2).getField().getFieldInstance(resolver.loader);
        // Known getter witnesses bind both ends; the only intervening string is screenName.
        // This accepts renamed fields and reordered string constants, never a guessed alphabetic field order.
        if(!first.equals(id)||!third.equals(name)||name.getType()!=String.class||handle.getType()!=String.class
            ||handle.getDeclaringClass()!=type||name.equals(handle))throw new NoSuchFieldException("Identity model getter contract");
        name.setAccessible(true);handle.setAccessible(true);return new Field[]{name,handle};
    }
    private static Field getterField(TargetResolver resolver,Class<?> type,String name)throws Exception {
        Method method=type.getDeclaredMethod(name);List<UsingFieldData> fields=resolver.dex().getMethodData(method).getUsingFields();
        if(fields.size()!=1||!fields.get(0).getUsingType().isRead())throw new NoSuchFieldException("Identity getter shape");
        Field field=fields.get(0).getField().getFieldInstance(resolver.loader);
        if(field.getDeclaringClass()!=type||Modifier.isStatic(field.getModifiers())||field.getType()!=method.getReturnType())throw new NoSuchFieldException("Identity getter type");
        return field;
    }
    private static Field[] socialProof(Class<?> type)throws Exception {
        int count=0;for(Field field:type.getDeclaredFields())if(!Modifier.isStatic(field.getModifiers())){
            if(field.getType()!=String.class)throw new NoSuchFieldException("Social proof shape");count++;
        }
        if(count<2||count>16)throw new NoSuchFieldException("Social proof field count");
        Class<?>[] params=new Class<?>[count];Arrays.fill(params,String.class);
        Constructor<?> constructor=type.getDeclaredConstructor(params);constructor.setAccessible(true);
        Object[] markers=new Object[count];for(int i=0;i<count;i++)markers[i]="xup_identity_probe_"+i;
        Object sample=constructor.newInstance(markers);
        Field name=ContentModelProbe.property(sample,"displayName",String.class),handle=ContentModelProbe.property(sample,"screenName",String.class);
        if(name.equals(handle))throw new NoSuchFieldException("Ambiguous social identity");return new Field[]{name,handle};
    }
    private static void remember(Object text){if(text instanceof String)INDEX.remember((String)text);}
    private IdentityTextHook(){}
}
