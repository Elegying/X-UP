package io.github.jared.xlowerseek;

/** X 12.31 identity adapters; stored names and biographies are never modified. */
final class IdentityTextHook {
 static final IdentityTextIndex INDEX=new IdentityTextIndex();
 static void install(ClassLoader loader){
  for(String name:new String[]{"com.x.models.i8","com.x.models.d5"}){
   Class<?> type=Reflect.findClass(name,loader);
   for(String getter:new String[]{"getName","D"})Hooks.findAndHookMethod(type,getter,new HookCallback(){
    protected void afterHookedMethod(HookParam p){if(!p.hasThrowable())remember(p.getResult());}
   });
   Hooks.hookAllConstructors(type,new HookCallback(){protected void afterHookedMethod(HookParam p){
    if(!p.hasThrowable()){remember(Reflect.callMethod(p.thisObject,"getName"));remember(Reflect.callMethod(p.thisObject,"D"));}
   }});
  }
  Hooks.hookAllConstructors(Reflect.findClass("com.x.models.dm.x7",loader),new HookCallback(){protected void afterHookedMethod(HookParam p){
   if(!p.hasThrowable()){remember(Reflect.getObjectField(p.thisObject,"a"));remember(Reflect.getObjectField(p.thisObject,"b"));}
  }});
 }
 private static void remember(Object text){if(text instanceof String)INDEX.remember((String)text);}
 private IdentityTextHook(){}
}
