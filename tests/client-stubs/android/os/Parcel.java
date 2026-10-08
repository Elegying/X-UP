package android.os;
public final class Parcel {ResultReceiver receiver; public static Parcel obtain(){return new Parcel();} public void setDataPosition(int position){} public void recycle(){receiver=null;}}
