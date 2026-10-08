package io.github.jared.xlowerseek;

import android.os.Parcel;
import android.os.ResultReceiver;

/** Parcel the platform class, never the module-only anonymous receiver subclass. */
final class ResultReceiverTransport {
    static ResultReceiver remote(ResultReceiver receiver) {
        Parcel parcel=Parcel.obtain();
        try {
            receiver.writeToParcel(parcel,0);
            parcel.setDataPosition(0);
            return ResultReceiver.CREATOR.createFromParcel(parcel);
        } finally {parcel.recycle();}
    }
}
