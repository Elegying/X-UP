package io.github.jared.xlowerseek;
import android.content.Context;
final class LocalModels {
    static int changes;
    static void changed(Context context) { changes++; }
}
