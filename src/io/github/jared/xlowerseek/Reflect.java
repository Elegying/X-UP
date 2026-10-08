package io.github.jared.xlowerseek;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Small reflection adapter, independent of legacy Xposed helpers. */
final class Reflect {
    private static final Map<List<Object>,AccessibleObject> cache=new ConcurrentHashMap<>();
    static Class<?> findClass(String name,ClassLoader loader){
        try{return Class.forName(name,false,loader);}catch(ClassNotFoundException e){throw new IllegalArgumentException(name,e);}
    }
    private static Field field(Class<?> type,String name){
        List<Object> key=Arrays.asList(type,"field",name);Field cached=(Field)cache.get(key);if(cached!=null)return cached;
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);cache.put(key,f);return f;}catch(NoSuchFieldException ignored){}
        throw new IllegalArgumentException("Field missing: "+type.getName()+"."+name);
    }
    static Object getObjectField(Object target,String name){return read(field(target.getClass(),name),target);}
    static Object getStaticObjectField(Class<?> type,String name){return read(field(type,name),null);}
    private static Object read(Field field,Object target){try{return field.get(target);}catch(IllegalAccessException e){throw new IllegalStateException(e);}}
    static int getIntField(Object o,String name){return ((Number)getObjectField(o,name)).intValue();}
    static long getLongField(Object o,String name){return ((Number)getObjectField(o,name)).longValue();}
    static float getFloatField(Object o,String name){return ((Number)getObjectField(o,name)).floatValue();}
    static boolean getBooleanField(Object o,String name){return (Boolean)getObjectField(o,name);}
    static Method findMethodExact(Class<?> type,String name,Class<?>... args){
        Method m=findMethodExactIfExists(type,name,args);if(m==null)throw new IllegalArgumentException("Method missing: "+type.getName()+"."+name);return m;
    }
    static Method findMethodExactIfExists(Class<?> type,String name,Class<?>... args){
        for(Class<?> c=type;c!=null;c=c.getSuperclass())try{Method m=c.getDeclaredMethod(name,args);m.setAccessible(true);return m;}catch(NoSuchMethodException ignored){}
        return null;
    }
    static Object callMethod(Object target,String name,Object... args){
        Method m=(Method)resolve(target.getClass(),name,args,false);
        try{return m.invoke(target,args);}catch(InvocationTargetException e){throw failure(e.getCause());}catch(ReflectiveOperationException e){throw failure(e);}
    }
    static Object newInstance(Class<?> type,Object... args){
        Constructor<?> c=(Constructor<?>)resolve(type,"<init>",args,true);
        try{return c.newInstance(args);}catch(InvocationTargetException e){throw failure(e.getCause());}catch(ReflectiveOperationException e){throw failure(e);}
    }
    private static RuntimeException failure(Throwable e){if(e instanceof RuntimeException)return (RuntimeException)e;if(e instanceof Error)throw (Error)e;return new IllegalStateException(e);}
    private static Executable resolve(Class<?> type,String name,Object[] args,boolean constructor){
        List<Object> key=new ArrayList<>();key.add(type);key.add(name);for(Object a:args)key.add(a==null?null:a.getClass());
        Executable cached=(Executable)cache.get(key);if(cached!=null)return cached;
        List<Executable> choices=new ArrayList<>();
        if(constructor)Collections.addAll(choices,type.getDeclaredConstructors());
        else for(Class<?> c=type;c!=null;c=c.getSuperclass())for(Method m:c.getDeclaredMethods())if(m.getName().equals(name)&&!m.isBridge())choices.add(m);
        Executable best=null;int bestScore=Integer.MAX_VALUE;
        for(Executable e:choices){
            Class<?>[] parameters=e.getParameterTypes();if(parameters.length!=args.length)continue;
            int score=0;
            for(int i=0;i<args.length;i++){
                Class<?> p=boxed(parameters[i]);Object arg=args[i];
                if(arg==null){if(parameters[i].isPrimitive()){score=Integer.MAX_VALUE;break;}score+=100;}
                else if(p==arg.getClass()){}else if(p.isInstance(arg)){score+=10;}else{score=Integer.MAX_VALUE;break;}
            }
            if(score<bestScore){best=e;bestScore=score;}
        }
        if(best==null)throw new IllegalArgumentException("No compatible method: "+type.getName()+"."+name);
        best.setAccessible(true);cache.put(key,best);return best;
    }
    private static Class<?> boxed(Class<?> c){
        if(!c.isPrimitive())return c;
        if(c==int.class)return Integer.class;if(c==long.class)return Long.class;if(c==float.class)return Float.class;if(c==double.class)return Double.class;
        if(c==boolean.class)return Boolean.class;if(c==byte.class)return Byte.class;if(c==short.class)return Short.class;if(c==char.class)return Character.class;
        return Void.class;
    }
}
