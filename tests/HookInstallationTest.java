package io.github.jared.xlowerseek;

import io.github.libxposed.api.XposedInterface;
import java.lang.reflect.*;
import java.util.*;

public class HookInstallationTest {
    static int checks;
    static void check(boolean condition) { checks++; if (!condition) throw new AssertionError("case " + checks); }
    static void target() {}
    public static void main(String[] args) throws Throwable {
        List<Boolean> active = new ArrayList<>();
        XposedInterface api = (XposedInterface) Proxy.newProxyInstance(HookInstallationTest.class.getClassLoader(),
                new Class<?>[]{XposedInterface.class}, (receiver, method, values) -> {
            if (!method.getName().equals("hook")) return null;
            Executable target = (Executable) values[0];
            return Proxy.newProxyInstance(HookInstallationTest.class.getClassLoader(), new Class<?>[]{XposedInterface.HookBuilder.class},
                    (builder, operation, parameters) -> {
                if (!operation.getName().equals("intercept")) return builder;
                int id = active.size(); active.add(true);
                return new XposedInterface.HookHandle() {
                    public Executable getExecutable() { return target; }
                    public void unhook() { active.set(id, false); }
                    public String getId() { return "test"; }
                    public XposedInterface.HookHandle replaceHook(XposedInterface.Hooker hooker) { return this; }
                };
            });
        });
        Hooks.initialize(api);
        Method target = HookInstallationTest.class.getDeclaredMethod("target");
        check(Hooks.installAtomically(() -> Hooks.hookMethod(target, new HookCallback() {})) == 1);
        IllegalStateException changed = new IllegalStateException("new X structure");
        try {
            Hooks.installAtomically(() -> {
                Hooks.hookMethod(target, new HookCallback() {});
                Hooks.hookMethod(target, new HookCallback() {});
                throw changed;
            });
            throw new AssertionError("failure was swallowed");
        } catch (IllegalStateException expected) { check(expected == changed); }
        check(active.equals(Arrays.asList(true, false, false)));
        check(Hooks.installAtomically(() -> Hooks.hookMethod(target, new HookCallback() {})) == 1);
        check(active.equals(Arrays.asList(true, false, false, true)));
        try { Hooks.installAtomically(() -> {}); throw new AssertionError("empty feature reported ready"); }
        catch (IllegalStateException expected) { check(expected.getMessage().contains("No compatible")); }
        try { Hooks.installAtomically(() -> Hooks.installAtomically(() -> {})); throw new AssertionError("nested installation allowed"); }
        catch (IllegalStateException expected) { check(expected.getMessage().contains("Nested")); }
        check(Hooks.installAtomically(() -> Hooks.hookMethod(target, new HookCallback() {})) == 1);
        System.out.println("HookInstallation: " + checks + " assertions passed");
    }
}
