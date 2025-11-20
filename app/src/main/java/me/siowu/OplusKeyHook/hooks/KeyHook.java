package me.siowu.OplusKeyHook.hooks;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;


public class KeyHook {

    XSharedPreferences sp = null;
    private long lastDownTime = 0;
    private long lastUpTime = 0;
    private int clickCount = 0;
    private boolean isLongPress = false;
    private static final long DOUBLE_CLICK_DELAY = 250;
    private static final long LONG_PRESS_TIME = 495;
    private static Context systemContext;

    public void handleLoadPackage(LoadPackageParam lpparam) {

        sp = new XSharedPreferences("me.siowu.OplusKeyHook", "key_action");
        sp.makeWorldReadable();

        try {
            Class<?> clazz = XposedHelpers.findClass(
                    "com.android.server.policy.StrategyActionButtonKeyLaunchApp",
                    lpparam.classLoader
            );

            if(clazz == null){
                XposedBridge.log("[Hook] Error: StrategyActionButtonKeyLaunchApp class not found");
            }

            XposedHelpers.findAndHookMethod(clazz,
                    "actionInterceptKeyBeforeQueueing",
                    KeyEvent.class, int.class, int.class, boolean.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            KeyEvent event = (KeyEvent) param.args[0];
                            int keyCode = event.getKeyCode();
                            boolean down = (boolean) param.args[3];
                            boolean interactive = (boolean) param.args[4]; // 屏幕状态：true=亮屏，false=熄屏
                            Object currentStrategy = param.thisObject;

                            if (keyCode == 780) {
                                long now = System.currentTimeMillis();
                                // 🔽=== 按下事件 ACTION_DOWN ===🔽
                                if (event.getAction() == KeyEvent.ACTION_DOWN && down) {
                                    lastDownTime = now;
                                    isLongPress = false;
                                    // 启动一个判定长按的线程
                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(LONG_PRESS_TIME);
                                            // 若超过495ms仍未抬起，则判定为长按
                                            if (lastUpTime < lastDownTime && !isLongPress) {
                                                isLongPress = true;
                                                XposedBridge.log("触发长按事件");
                                                handleClick("long_", interactive, currentStrategy);
                                            }
                                        } catch (Exception ignored) {
                                        }
                                    }).start();

                                    param.setResult(null);
                                    return;
                                }

                                // 🔼=== 抬起事件 ACTION_UP ===🔼
                                if (event.getAction() == KeyEvent.ACTION_UP && !down) {
                                    lastUpTime = now;
                                    // 如果已被长按消耗，不处理短按和双击
                                    if (isLongPress) {
                                        param.setResult(null);
                                        return;
                                    }
                                    clickCount++;

                                    // 判断双击
                                    if (clickCount == 2 && (now - lastDownTime) < DOUBLE_CLICK_DELAY) {
                                        XposedBridge.log("触发双击事件");
                                        handleClick("double_", interactive, currentStrategy);
                                        clickCount = 0;
                                        param.setResult(null);
                                        return;
                                    }

                                    // 如果 250ms 内没有第二次点击，判定为短按
                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(DOUBLE_CLICK_DELAY);
                                            if (clickCount == 1 && !isLongPress) {
                                                XposedBridge.log("触发短按事件");
                                                handleClick("single_", interactive, currentStrategy);
                                            }
                                            clickCount = 0;
                                        } catch (Exception ignored) {
                                        }
                                    }).start();
                                    param.setResult(null);
                                }
                            }


                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("[Hook] Error: " + t.getMessage());
        }
    }


    public void handleClick(String prefix, boolean interactive, Object currentStrategy) {
        sp.reload();
        if (interactive) {
            XposedBridge.log("当前屏幕是亮屏状态");
            if (sp.getBoolean(prefix + "vibrate", true)) {
                XposedHelpers.callMethod(currentStrategy, "longPressStartVibrate");
            }
            doAction(prefix);
        } else {
            XposedBridge.log("当前屏幕是息屏状态");
            if (sp.getBoolean(prefix + "screen_off", true)) {
                XposedHelpers.callMethod(currentStrategy, "wakeup");
                doAction(prefix);
            } else {
                XposedBridge.log("根据配置设定 不执行操作");
            }
        }
    }

    public void doAction(String prefix) {
        XposedBridge.log("开始执行快捷键操作");
        sp.reload();
        String type = sp.getString(prefix + "type", "");
        XposedBridge.log("当前快捷键类型: " + type);
        switch (type) {
            case "无":
                XposedBridge.log("不执行任何操作");
                break;
            case "常用":
                doCommonAction(prefix);
                break;
            case "自定义Activity":
                doCustomActivity(prefix);
                break;
            case "自定义UrlScheme":
                doCustomUrlScheme(prefix);
                break;
            default:
                XposedBridge.log("未获取到配置");
                break;
        }
    }


    public void doCommonAction(String prefix) {
        sp.reload();
        int index = sp.getInt(prefix + "common_index", 0);
        XposedBridge.log("当前常用操作索引: " + index);
        switch (index) {
            case 0:
                startWechatActivity("launch_type_offline_wallet");
                break;
            case 1:
                startWechatActivity("launch_type_scan_qrcode");
                break;
            case 2:
                startSchemeAsBrowser("alipays://platformapi/startapp?saId=20000056");
                break;
            case 3:
                startSchemeAsBrowser("alipays://platformapi/startapp?saId=10000007");
                break;
        }
    }

    public void doCustomActivity(String prefix) {
        sp.reload();
        String activity = sp.getString(prefix + "activity", "");
        String packageName = sp.getString(prefix + "package", "");
        if (activity.isEmpty() || packageName.isEmpty()) {
            XposedBridge.log("自定义Activity为空");
            return;
        }
        startActivity(packageName, activity);
    }

    public void doCustomUrlScheme(String prefix) {
        sp.reload();
        String scheme = sp.getString(prefix + "url", "");
        if (scheme.isEmpty()) {
            XposedBridge.log("自定义UrlScheme为空");
            return;
        }
        startSchemeAsBrowser(scheme);
    }

    //启动自定义Activity
    private void startActivity(String pkgName, String targetActivity) {
        try {
            // 1. 获取系统上下文（Hook系统进程可直接拿到）
            Context systemContext = (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
            );
            // 2. 构造启动微信的Intent
            Intent intent = new Intent();
            // 设置微信包名和目标Activity
            intent.setComponent(new ComponentName(pkgName, targetActivity));
            // 关键Flag：新建任务栈，避免和其他页面冲突
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 3. 启动Activity（和负一屏的核心步骤完全一致）
            systemContext.startActivity(intent);
            XposedBridge.log("成功启动指定Activity: " + targetActivity);
        } catch (Throwable t) {
            XposedBridge.log("启动指定Activity失败: " + t.getMessage());
        }
    }

    //通过微信官方的分发接口打开微信的界面
    private void startWechatActivity(String targetActivity) {
        try {
            // 1. 获取系统上下文（ActivityThread.currentApplication() 返回 Application）
            Context systemContext = (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
            );
            if (systemContext == null) {
                XposedBridge.log("startWechatPayCode: systemContext == null");
                return;
            }

            // 2. 构造 Intent —— 与负一屏发送的一致：action + target ShortCutDispatchActivity + extras
            Intent intent = new Intent();
            intent.setAction("com.tencent.mm.ui.ShortCutDispatchAction"); // 与日志与源码相符
            intent.setComponent(new ComponentName("com.tencent.mm", "com.tencent.mm.ui.ShortCutDispatchActivity"));
            intent.setPackage("com.tencent.mm"); // 限定发给微信
            // 关键 extras（来源反编译代码表明微信读取这些字段来分发）
            intent.putExtra("LauncherUI.Shortcut.LaunchType", targetActivity); // 付款码
            intent.putExtra("LauncherUI.From.Scaner.Shortcut", false);
            // 模拟系统启动行为
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 3. 启动（以 systemContext 发起，确保调用者是系统）
            systemContext.startActivity(intent);
            XposedBridge.log("startWechatPayCode: started ShortCutDispatchAction -> offline wallet");
        } catch (Throwable t) {
            XposedBridge.log("startWechatPayCode: failed: " + t);
        }
    }

    //以系统上下文模拟浏览器打开任意 scheme
    private boolean startSchemeAsBrowser(String schemeUri) {
        try {
            // 获取系统 Application Context（必须在系统进程里调用才可靠）
            Context systemContext = (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
            );
            if (systemContext == null) {
                XposedBridge.log("startSchemeAsBrowser: systemContext == null");
                return false;
            }

            // 构造 Intent：ACTION_VIEW + Uri + BROWSABLE 类别
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(schemeUri));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            // 不设置 setPackage 或 setComponent，让系统解析哪个 app 处理 scheme
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 启动
            systemContext.startActivity(intent);
            XposedBridge.log("startSchemeAsBrowser: started scheme -> " + schemeUri);
            return true;
        } catch (Throwable t) {
            XposedBridge.log("startSchemeAsBrowser: failed to start scheme: " + t);
            return false;
        }
    }
}