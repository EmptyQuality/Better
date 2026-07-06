package com.example.clawim.util;

import android.util.Log;

/**
 * 自定义日志工具类
 */

public class LogUtil {
    private static boolean LogEnable = true;
    private static String GlobalTag = "";

    private LogUtil(){

    }

    /**
     * 初始化日志配置（在 Application 里调用一次即可）
     * @param globalTag 全局 TAG，方便过滤
     * @param enable    是否启用日志（发布版传 false）
     */
    public static void init(String globalTag, boolean enable) {
        GlobalTag = globalTag;
        LogEnable = enable;
    }

    /**
     * 生成最终的 TAG：如果传入了自定义 tag，则使用它；否则用全局 tag
     */
    private static String getFinalTag(String tag) {
        if (tag != null && !tag.isEmpty()) {
            return tag;
        }
        return GlobalTag;
    }

    public static void v(String tag, String msg) {
        if (LogEnable) {
            Log.v(getFinalTag(tag), msg);
        }
    }

    public static void d(String tag, String msg) {
        if (LogEnable) {
            Log.d(getFinalTag(tag), msg);
        }
    }

    public static void i(String tag, String msg) {
        if (LogEnable) {
            Log.i(getFinalTag(tag), msg);
        }
    }

    public static void w(String tag, String msg) {
        if (LogEnable) {
            Log.w(getFinalTag(tag), msg);
        }
    }

    public static void e(String tag, String msg) {
        // 错误日志通常始终输出，可以根据需要调整
        Log.e(getFinalTag(tag), msg);
    }

    // 还可添加带 Throwable 的重载
    public static void e(String tag, String msg, Throwable tr) {
        Log.e(getFinalTag(tag), msg, tr);
    }

}
