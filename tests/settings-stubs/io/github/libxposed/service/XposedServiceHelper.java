package io.github.libxposed.service;
public final class XposedServiceHelper {
    public interface OnServiceListener {
        void onServiceBind(XposedService service);
        void onServiceDied(XposedService service);
    }
    private static OnServiceListener listener;
    public static void registerListener(OnServiceListener value) { listener = value; }
    public static void bind(XposedService service) { listener.onServiceBind(service); }
    public static void die(XposedService service) { listener.onServiceDied(service); }
}
