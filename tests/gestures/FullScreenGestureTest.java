package io.github.jared.xlowerseek;

import android.view.*;
import android.os.Handler;
import java.lang.reflect.*;

public final class FullScreenGestureTest {
    static int checks;
    static void check(boolean value) { checks++; if (!value) throw new AssertionError("gesture case " + checks); }
    static final Method TOUCH;
    static final Field PLAYER;
    static { try {
        TOUCH = FullScreenGestureHook.class.getDeclaredMethod("touch", View.class, Member.class, MotionEvent.class); TOUCH.setAccessible(true);
        PLAYER = FullScreenGestureHook.class.getDeclaredField("player"); PLAYER.setAccessible(true);
    } catch (Exception e) { throw new AssertionError(e); } }
    public static final class Speed { public float a,b; public Speed(float a,float b){this.a=a;this.b=b;} }
    public static final class Player {
        long position = 20000; boolean failBoost; Speed speed = new Speed(1f,1f);
        public long getDuration(){return 60000;} public long getCurrentPosition(){return position;}
        public boolean g(){return false;} public boolean isPlaying(){return true;}
        public Speed d(){return speed;} public void c(Speed v){if(failBoost&&v.a==2)throw new IllegalStateException();speed=v;}
        public void seekTo(long value){position=value;}
    }
    static FullScreenGestureHook hook(View root, Player player, boolean hold) {
        return new FullScreenGestureHook(new FullScreenGestureHook.Target() {
            public Object player(){return player;} public View surface(){return root;} public View decor(){return root;}
            public void syncProgress(Object p){} public boolean swipeEnabled(){return true;} public boolean holdEnabled(){return hold;}
        });
    }
    static boolean event(FullScreenGestureHook hook,View root,int action,float x,float y)throws Exception {
        return (Boolean)TOUCH.invoke(hook,root,null,new MotionEvent(action,x,y));
    }
    public static void main(String[] args)throws Exception {
        android.os.SystemClock.now=1000;
        for(int height:new int[]{300,320,360,800}) {
            View root=new View(640,height); FullScreenGestureHook h=hook(root,new Player(),false); int accepted=0;
            for(int y=0;y<height;y++){event(h,root,0,320,y+.5f);if(PLAYER.get(h)!=null)accepted++;}
            check(accepted >= height/2); if(height==800)check(accepted==480);
            System.out.println("Fullscreen height="+height+" accepted="+accepted);h.reset();
        }
        View root=new View(640,360);Player player=new Player();FullScreenGestureHook h=hook(root,player,true);
        root.insets=new WindowInsets(new android.graphics.Insets(70,0,0,40));
        event(h,root,0,30,180);check(PLAYER.get(h)==null);
        event(h,root,0,320,20);check(PLAYER.get(h)==null);
        event(h,root,0,320,340);check(PLAYER.get(h)==null);
        check(!event(h,root,0,320,180));check(PLAYER.get(h)!=null);
        check(event(h,root,2,400,180));check(player.position>20000);
        h.reset();check(event(h,root,2,410,180));check(event(h,root,1,410,180));
        check(!event(h,root,0,320,180));check(!event(h,root,1,320,180));
        int cancels=Hooks.cancels;player.failBoost=true;
        event(h,root,0,320,180);Handler.advance(500);
        check(Hooks.cancels==cancels+1);check(event(h,root,2,321,180));check(event(h,root,1,321,180));
        player.failBoost=false;event(h,root,0,320,180);Handler.advance(500);check(player.speed.a==2);
        check(event(h,root,1,320,180));check(player.speed.a==1);h.reset();
        root.screenX=200;root.screenY=400;
        event(h,root,0,520,580);check(PLAYER.get(h)!=null);h.reset();
        event(h,root,0,520,740);check(PLAYER.get(h)==null);h.reset();
        check(Handler.queued()==0);
        System.out.println("FullScreenGesture: "+checks+" assertions passed (production recognizer, fake Android events)");
    }
}
