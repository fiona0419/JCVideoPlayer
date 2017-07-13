package fm.jiecao.jcvideoplayer_lib.xclub;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.SystemProperties;
import android.provider.Settings;
import android.support.v4.text.TextUtilsCompat;
import android.support.v4.view.ViewCompat;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * Created by fuqiang.zhong on 2016/9/5.
 */
public class XClubJCUtils {
    private static final String TAG = "XClubJCUtils";

    /**
     * @param context Context
     * @return 获得屏幕亮度模式 手动1/自动0
     */
    public static int getScreenMode(Context context) {
        int screenMode = 0;
        try {
            screenMode = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE);
        } catch (Exception localException) {
            Log.e(TAG, "getScreenMode error");
        }
        return screenMode;
    }

    /**
     * @param context  Context
     * @param paramInt 设置屏幕亮度模式
     */
    public static void setScreenMode(Context context, int paramInt) {
        try {
            Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, paramInt);
        } catch (Exception localException) {
            Log.e(TAG, "setScreenMode error");
        }
    }

    /**
     * @param context Context
     * @return 获得系统屏幕亮度 0~255
     */
    public static int getSystemScreenBrightness(Context context) {
        int screenBrightness = 255;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.System.canWrite(context)) {
                    screenBrightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
                } else {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            } else {
                screenBrightness = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            }
        } catch (Exception localException) {
            Log.e(TAG, "getScreenBrightness error");
        }
        return screenBrightness;
    }

    /**
     * @param context  Context
     * @param paramInt 设置系统屏幕亮度
     */
    public static void setSystemScreenBrightness(Context context, int paramInt) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.System.canWrite(context)) {
                    Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, paramInt);
                } else {
                    Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
                    intent.setData(Uri.parse("package:" + context.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                }
            } else {
                Settings.System.putInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, paramInt);
            }
        } catch (Exception localException) {
            Log.e(TAG, "saveScreenBrightness error");
        }
    }

    /**
     * @param activity Activity
     * @param def      屏幕亮度初始值
     * @return 设置当前屏幕亮度
     */
    public static int getScreenBrightness(Activity activity, int def) {
        Window localWindow = activity.getWindow();
        WindowManager.LayoutParams localLayoutParams = localWindow.getAttributes();
        int screenBrightness = (int) (localLayoutParams.screenBrightness * 255);
        if (screenBrightness < 0) {
            screenBrightness = def;
        }
        return screenBrightness;
    }

    /**
     * @param activity Activity
     * @param paramInt 更改当前屏幕亮度
     */
    public static void setScreenBrightness(Activity activity, int paramInt) {
        if (paramInt > 255) paramInt = 255;
        if (paramInt < 0) paramInt = 0;
        Window localWindow = activity.getWindow();
        WindowManager.LayoutParams localLayoutParams = localWindow.getAttributes();
        localLayoutParams.screenBrightness = paramInt / 255.0f;
        localWindow.setAttributes(localLayoutParams);
    }

    /**
     * @param context   Context
     * @param direction 设置音量值
     */
    public static void setVolume(Context context, int direction) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0);
    }

    /**
     * @param context Context
     * @return 获得音量最大值
     */
    public static int getMaxVolume(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    }

    /**
     * @param context Context
     * @return 获得当前音量
     */
    public static int getCurVolume(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    /**
     * @param context Context
     * @return boolean
     */
    public static boolean needShowGuide(Context context) {
        return context.getSharedPreferences(TAG, Context.MODE_PRIVATE).getBoolean(TAG + "guide_first", true);
    }

    /**
     * @param context Context
     */
    public static void noLongerShowGuide(Context context) {
        context.getSharedPreferences(TAG, Context.MODE_PRIVATE).edit().putBoolean(TAG + "guide_first", false).apply();
    }

    /**
     * @param context Context
     * @return 是否有网络连接
     */
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo[] info = cm.getAllNetworkInfo();
            if (info != null) {
                for (NetworkInfo anInfo : info) {
                    if (anInfo.getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 退出全屏
     *
     * @param activity Activity
     * @throws IllegalAccessException
     */
    public static void exitFullScreen(Activity activity) throws IllegalAccessException {
//        int flags = View.SYSTEM_UI_FLAG_VISIBLE;
//        try {
//            Field filed = View.class.getField("SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR");
//            flags |= filed.getInt(View.class);
//        } catch (NoSuchFieldException e) {
//            showNavigationBar(flags, activity);
//        }
//        showNavigationBar(flags, activity);
        Window window = (activity).getWindow();

        if (Build.VERSION.SDK_INT >= 19) {
            window.getDecorView().setSystemUiVisibility(sOldFlags);
            final View decorView = window.getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(
                    new View.OnSystemUiVisibilityChangeListener() {

                        @Override
                        public void onSystemUiVisibilityChange(int visibility) {
                            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                                decorView.setSystemUiVisibility(sOldFlags);
                            }
                        }
                    });
        }
    }

    private static void showNavigationBar(final int flags, Activity activity) {
        Window window = activity.getWindow();
        window.getDecorView().setSystemUiVisibility(flags);
        final View decorView = window.getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(
                new View.OnSystemUiVisibilityChangeListener() {

                    @Override
                    public void onSystemUiVisibilityChange(int visibility) {
                        decorView.setSystemUiVisibility(flags);
                    }
                });
    }

    private static int sOldFlags = -1;

    /**
     * 进入全屏
     *
     * @param activity Activity
     */
    public static void enterFullScreen(Activity activity) {
        Window window = (activity).getWindow();
        sOldFlags = window.getDecorView().getSystemUiVisibility();
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;

        if (Build.VERSION.SDK_INT >= 19) {
            window.getDecorView().setSystemUiVisibility(flags);
            final View decorView = window.getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(
                    new View.OnSystemUiVisibilityChangeListener() {

                        @Override
                        public void onSystemUiVisibilityChange(int visibility) {
                            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                                decorView.setSystemUiVisibility(flags);
                            }
                        }
                    });
        }

    }

    /**
     * @return 是否有导航栏
     */
    public static boolean haveNavigationBar() {
        String property = SystemProperties.get("qemu.hw.mainkeys");
        return !property.equals("1");
    }

    /**
     * dp转为px
     *
     * @param context  Context
     * @param dipValue float
     * @return px
     */
    public static int dip2px(Context context, float dipValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    /**
     * 是否阿拉伯布局
     *
     * @return boolean
     */
    public static boolean isRtl() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            return TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) ==
                    ViewCompat.LAYOUT_DIRECTION_RTL;
        } else {
            return false;
        }
    }
}
