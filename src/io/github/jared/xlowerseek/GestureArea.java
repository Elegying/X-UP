package io.github.jared.xlowerseek;

/** Bounds for fullscreen swipe/hold only; native double-tap handling is separate. */
final class GestureArea {
    static boolean contains(float width, float height, float density, float x, float y,
                            int left, int top, int right, int bottom) {
        if (width <= 0 || height <= 0 || density <= 0) return false;
        float contentHeight = height - top - bottom;
        boolean landscape = width > height;
        float toolbar = Math.min((landscape ? 56 : 140) * density, contentHeight * .22f);
        float actions = Math.min((landscape ? 72 : 180) * density, contentHeight * .28f);
        return x >= Math.max(left, 24 * density) && x <= width - Math.max(right, 24 * density)
                && y >= Math.max(top, toolbar) && y <= height - Math.max(bottom, actions);
    }
}
