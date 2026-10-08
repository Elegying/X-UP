package io.github.jared.xlowerseek;

/** Pure geometry; no global touch interception and no player operations. */
public final class GesturePolicy {
    private GesturePolicy() {}
    public static boolean isLower(float x, float y, int width, int height) {
        return width > 0 && height > 0 && Float.isFinite(x) && Float.isFinite(y)
            && x >= 0 && x < width && y >= height / 2.0f && y < height;
    }
    public static long lowerForwardOffset(long offset, int width, int height) {
        float x=Float.intBitsToFloat((int)(offset>>32));
        float y=Float.intBitsToFloat((int)offset);
        return isLower(x,y,width,height)?forwardOffset(offset,width):offset;
    }
    public static long forwardOffset(long offset, int width) {
        // At the right boundary X always selects its existing FORWARD event.
        return ((long) Float.floatToRawIntBits((float) width) << 32)
            | (offset & 0xffffffffL);
    }
}
