package io.github.jared.xlowerseek;
final class SwipePolicy {
    private SwipePolicy(){}
    static long position(long start,long duration,float delta,float width){
        if(duration<=0||width<=0||!Float.isFinite(delta)||!Float.isFinite(width))return start;
        double span=Math.min(duration,120000L);
        return Math.max(0,Math.min(duration,start+Math.round(delta/width*span)));
    }
}
