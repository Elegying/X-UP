import io.github.jared.xlowerseek.GesturePolicy;
public class GesturePolicyTest {
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("case " + checks); }
    static long point(float x,float y) { return ((long)Float.floatToRawIntBits(x)<<32)|(Float.floatToRawIntBits(y)&0xffffffffL); }
    // X 12.31's independently extracted horizontal-zone behavior.
    static int original(float center, float x, int width) {
        float edge=(1-center)*width/2;
        if(x<=edge||x>=width-edge) return x<width/2f?-1:1;
        return 0;
    }
    public static void main(String[] args) {
        for(int[] size:new int[][]{{1200,2360},{2360,1200},{601,999}}) {
            int w=size[0],h=size[1];
            for(float center:new float[]{0,0.2f,0.5f,1}) {
                for(float x:new float[]{1,w/2f,w-1}) {
                    long p=point(x,h*0.75f);
                    check(GesturePolicy.isLower(x,h*0.75f,w,h));
                    long changed=GesturePolicy.forwardOffset(p,w);
                    check(original(center,Float.intBitsToFloat((int)(changed>>32)),w)==1);
                    check((int)changed==(int)p);
                    check(!GesturePolicy.isLower(x,h*0.25f,w,h));
                }
            }
            check(GesturePolicy.isLower(1,h/2f,w,h));
            check(!GesturePolicy.isLower(1,h/2f-0.01f,w,h));
            check(!GesturePolicy.isLower(1,h,w,h));
            check(!GesturePolicy.isLower(w,h*0.75f,w,h));
        }
        check(!GesturePolicy.isLower(Float.NaN,100,200,200));
        check(!GesturePolicy.isLower(1,Float.POSITIVE_INFINITY,200,200));
        check(!GesturePolicy.isLower(-1,150,200,200));
        check(!GesturePolicy.isLower(1,150,0,200));
        check(!GesturePolicy.isLower(1,150,200,0));
        System.out.println("PASS "+checks+" geometry and original-decision assertions");
    }
}
