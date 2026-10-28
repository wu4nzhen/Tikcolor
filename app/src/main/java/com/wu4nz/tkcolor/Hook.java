package com.wu4nz.tkcolor;

import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {

    private static final float HUE = 195f; // #00C2FF

    private static int swap(int c) {
        int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
        if (r < 150 || g > 140 || r - b < 60) return c;
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        boolean red = (hsv[0] >= 340f || hsv[0] <= 10f)
                && hsv[1] > 0.5f && hsv[2] > 0.4f;
        if (!red) return c;
        hsv[0] = HUE;
        return Color.HSVToColor(Color.alpha(c), hsv);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!lp.packageName.equals("com.zhiliaoapp.musically")
                && !lp.packageName.equals("com.ss.android.ugc.trill")) return;

        XC_MethodHook after = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                p.setResult(swap((Integer) p.getResult()));
            }
        };

        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getColor",
                    int.class, after);
        } catch (Throwable t) {
            XposedBridge.log("TkColor: getColor(int) fail: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getColor",
                    int.class, Resources.Theme.class, after);
        } catch (Throwable t) {
            XposedBridge.log("TkColor: getColor(int,Theme) fail: " + t);
        }
        try {
            XposedHelpers.findAndHookMethod(Paint.class, "setColor",
                    int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    p.args[0] = swap((Integer) p.args[0]);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("TkColor: Paint.setColor fail: " + t);
        }

        XposedBridge.log("TkColor: hooked " + lp.packageName);
    }
}
