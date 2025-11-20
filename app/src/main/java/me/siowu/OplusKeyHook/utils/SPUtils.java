package me.siowu.OplusKeyHook.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class SPUtils {
    private static final String FILE_NAME = "key_action";
    private static SharedPreferences sp;

    // 初始化（建议在 Application 或第一个 Activity 中调用一次）
    public static void init(Context context) {
        if (sp == null) {
            sp = context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_WORLD_READABLE);
        }
    }

    // 写入 String
    public static void putString(String key, String value) {
        if (sp != null) {
            sp.edit().putString(key, value).apply();
        }
    }

    // 读取 String
    public static String getString(String key, String defValue) {
        if (sp != null) {
            return sp.getString(key, defValue);
        }
        return defValue;
    }

    // 写入 int
    public static void putInt(String key, int value) {
        if (sp != null) {
            sp.edit().putInt(key, value).apply();
        }
    }

    // 读取 int
    public static int getInt(String key, int defValue) {
        if (sp != null) {
            return sp.getInt(key, defValue);
        }
        return defValue;
    }

    // 写入 boolean
    public static void putBoolean(String key, boolean value) {
        if (sp != null) {
            sp.edit().putBoolean(key, value).apply();
        }
    }

    // 读取 boolean
    public static boolean getBoolean(String key, boolean defValue) {
        if (sp != null) {
            return sp.getBoolean(key, defValue);
        }
        return defValue;
    }

    // 删除指定 key
    public static void remove(String key) {
        if (sp != null) {
            sp.edit().remove(key).apply();
        }
    }

    // 清空所有数据
    public static void clear() {
        if (sp != null) {
            sp.edit().clear().apply();
        }
    }
}
