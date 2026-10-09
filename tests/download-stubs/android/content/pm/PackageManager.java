package android.content.pm;public class PackageManager {public String[] getPackagesForUid(int uid){return uid==111?new String[]{"com.twitter.android"}:uid==222?new String[]{"untrusted.app"}:null;}}
