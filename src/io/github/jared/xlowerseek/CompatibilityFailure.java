package io.github.jared.xlowerseek;

/** Stable diagnostics contain no reflected argument values or user content. */
final class CompatibilityFailure extends IllegalStateException {
    final int matches;
    CompatibilityFailure(String target,int matches){super(target+": compatible targets="+matches);this.matches=matches;}
    static boolean structural(Throwable error){
        return error instanceof CompatibilityFailure||error instanceof ReflectiveOperationException
            ||error instanceof LinkageError;
    }
    static String describe(Throwable error){
        if(error instanceof CompatibilityFailure)
            return ((CompatibilityFailure)error).matches==0?"未找到符合结构的入口":"存在多个候选入口，已停止匹配";
        if(error instanceof ReflectiveOperationException||error instanceof LinkageError)
            return "依赖类型、方法或字段已变化";
        return "初始化失败（"+error.getClass().getSimpleName()+"）";
    }
    static boolean retry(boolean cached,Throwable error){
        return cached&&structural(error)&&error.getSuppressed().length==0;
    }
}
