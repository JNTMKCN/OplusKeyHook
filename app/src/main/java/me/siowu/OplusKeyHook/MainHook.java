package me.siowu.OplusKeyHook;


import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import me.siowu.OplusKeyHook.hooks.KeyHook;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;
        if ("android".equals(packageName)) {
            new KeyHook().handleLoadPackage(lpparam);
        }
    }
}

