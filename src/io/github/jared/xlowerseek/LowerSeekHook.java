package io.github.jared.xlowerseek;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import org.luckypray.dexkit.query.enums.StringMatchType;
import org.luckypray.dexkit.query.matchers.ClassMatcher;
import org.luckypray.dexkit.query.matchers.FieldMatcher;
import org.luckypray.dexkit.query.matchers.MethodMatcher;

/** Follow X's native seek graph instead of release numbers or synthetic switch labels. */
final class LowerSeekHook {
    static void install(TargetResolver resolver, RemoteSettings settings) throws Exception {
        Class<?> seek=resolver.type("video.seek-region",ClassMatcher.create().usingStrings("Seek(direction="),
            type->type.getInterfaces().length==1);
        Method zone=resolver.method("video.seek-zone",MethodMatcher.create()
            .paramTypes(float.class,float.class,int.class).returnType(seek.getInterfaces()[0]),
            method->Modifier.isStatic(method.getModifiers()));
        Method doubleTap=resolver.method("video.double-tap",MethodMatcher.create().addInvoke(MethodMatcher.create(zone))
            .returnType(Object.class).paramCount(3),
            method->!Modifier.isStatic(method.getModifiers())&&method.getParameterTypes()[1]==long.class);
        Class<?> function1=Reflect.findClass("kotlin.jvm.functions.Function1",resolver.loader);
        Class<?> function0=Reflect.findClass("kotlin.jvm.functions.Function0",resolver.loader);
        Field gestureState=VideoMembers.field(doubleTap.getDeclaringClass(),
            f->!f.getType().isPrimitive()&&f.getType()!=function1);
        Class<?> state=gestureState.getType();
        Constructor<?> makeGesture=VideoMembers.constructor(doubleTap.getDeclaringClass(),float.class,function1,function1,state);
        Method compose=resolver.method("video.gesture-composer",MethodMatcher.create()
            .addInvoke(MethodMatcher.create(makeGesture)).returnType(void.class),m->Modifier.isStatic(m.getModifiers()));
        Class<?> layout=resolver.type("video.size-callback",ClassMatcher.create().addInterface(function1.getName())
            .addMethod(MethodMatcher.create().name("<init>").paramTypes(state,int.class).addCaller(MethodMatcher.create(compose))));
        Field layoutState=VideoMembers.field(layout,state);
        Method layoutInvoke=layout.getDeclaredMethod("invoke",Object.class);
        // DpSize has the same string/long shape. Follow the size read by this layout callback.
        Class<?> size=resolver.type("video.int-size",ClassMatcher.create()
            .className("androidx.compose.ui.unit.",StringMatchType.StartsWith).usingEqStrings(" x ")
            .addField(FieldMatcher.create().type(long.class)
                .addReadMethod(MethodMatcher.create(layoutInvoke))).fieldCount(1));
        Field packedSize=VideoMembers.field(size,long.class);
        Class<?> point=resolver.type("video.offset",ClassMatcher.create()
            .className("androidx.compose.ui.geometry.",StringMatchType.StartsWith).usingEqStrings("Offset.Unspecified")
            .addFieldForType(long.class).fieldCount(1));
        Field packedPoint=VideoMembers.field(point,long.class);
        Constructor<?> box=VideoMembers.constructor(point,long.class);
        Method continuation=resolver.method("video.continuation-tap",MethodMatcher.create().name("invoke")
            .paramTypes(Object.class).returnType(Object.class).addInvoke(MethodMatcher.create(zone)));
        Constructor<?> makeContinuation=VideoMembers.constructor(continuation.getDeclaringClass(),float.class,function1,function0,state);
        Map<Object,int[]> sizes=Collections.synchronizedMap(new WeakHashMap<>());
        Map<Object,Object> continuations=Collections.synchronizedMap(new WeakHashMap<>());
        // Install only after the complete graph has passed its structural contracts.
        Hooks.hookMethod(layoutInvoke,new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p)throws Throwable {
                if(p.hasThrowable()||!size.isInstance(p.args[0]))return;
                long packed=packedSize.getLong(p.args[0]);
                sizes.put(layoutState.get(p.thisObject),new int[]{(int)(packed>>32),(int)packed});
            }
        });
        Hooks.hookMethod(makeContinuation,new HookCallback(){
            @Override protected void afterHookedMethod(HookParam p){
                if(!p.hasThrowable()&&p.args[1]!=null&&p.args[3]!=null)continuations.put(p.thisObject,p.args[3]);
            }
        });
        Hooks.hookMethod(continuation,ContinuationTapHook.callback(()->settings.feature(Feature.LOWER_SEEK),sizes,continuations,box,packedPoint));
        Hooks.hookMethod(doubleTap,new HookCallback(){
            @Override protected void beforeHookedMethod(HookParam p)throws Throwable {
                if(!settings.feature(Feature.LOWER_SEEK))return;
                int[] dimensions=sizes.get(gestureState.get(p.thisObject));
                if(dimensions!=null)p.args[1]=GesturePolicy.lowerForwardOffset((Long)p.args[1],dimensions[0],dimensions[1]);
            }
        });
    }
}
