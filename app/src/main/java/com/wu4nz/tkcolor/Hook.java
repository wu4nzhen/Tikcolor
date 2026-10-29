package com.wu4nz.tkcolor;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Hook implements IXposedHookLoadPackage {

    // 195 = cyan/xanh nước biển sáng, 210 = xanh dương điện,
    // 150 = xanh lá neon, 270 = tím điện, 320 = hồng magenta
    private static final float HUE = 195f;
    private static volatile boolean enabled = true;

    private static int swap(int c) {
        if (!enabled) return c;
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

    private static void track(Object act) {
        String n = act.getClass().getName();
        enabled = !n.toLowerCase().contains("splash");
        XposedBridge.log("TkColor: activity " + n + " enabled=" + enabled);
    }

    // hook method có tham số int đầu tiên là màu
    private static void hookIntArg(Class<?> cls, String method, Class<?>... extra) {
        try {
            Object[] params = new Object[1 + extra.length + 1];
            params[0] = int.class;
            System.arraycopy(extra, 0, params, 1, extra.length);
            params[params.length - 1] = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    p.args[0] = swap((Integer) p.args[0]);
                }
            };
            XposedHelpers.findAndHookMethod(cls, method, params);
        } catch (Throwable t) {
            XposedBridge.log("TkColor: " + cls.getSimpleName() + "." + method + " fail: " + t);
        }
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!lp.packageName.equals("com.zhiliaoapp.musically")
                && !lp.packageName.equals("com.ss.android.ugc.trill")) return;

        // 1. Resources.getColor
        XC_MethodHook afterColor = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam p) {
                p.setResult(swap((Integer) p.getResult()));
            }
        };
        try {
            XposedHelpers.findAndHookMethod(Resources.class, "getColor",
                    int.class, afterColor);
            XposedHelpers.findAndHookMethod(Resources.class, "getColor",
                    int.class, Resources.Theme.class, afterColor);
        } catch (Throwable t) {
            XposedBridge.log("TkColor: getColor fail: " + t);
        }

        // 2. Resources.getColorStateList
        try {
            XC_MethodHook cslHook = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        ColorStateList csl = (ColorStateList) p.getResult();
                        if (csl == null || !enabled) return;
                        int[][] specs = (int[][]) XposedHelpers
                                .getObjectField(csl, "mStateSpecs");
                        int[] cols = ((int[]) XposedHelpers
                                .getObjectField(csl, "mColors")).clone();
                        boolean changed = false;
                        for (int i = 0; i < cols.length; i++) {
                            int n = swap(cols[i]);
                            if (n != cols[i]) { cols[i] = n; changed = true; }
                        }
                        if (changed)
                            p.setResult(new ColorStateList(specs, cols));
                    } catch (Throwable ignored) { }
                }
            };
            XposedHelpers.findAndHookMethod(Resources.class,
                    "getColorStateList", int.class, cslHook);
            XposedHelpers.findAndHookMethod(Resources.class,
                    "getColorStateList", int.class, Resources.Theme.class, cslHook);
        } catch (Throwable t) {
            XposedBridge.log("TkColor: csl fail: " + t);
        }

        // 3. Màu vẽ trực tiếp / tint / filter
        hookIntArg(Paint.class, "setColor");
        hookIntArg(ColorDrawable.class, "setColor");
        hookIntArg(Drawable.class, "setTint");
        hookIntArg(ImageView.class, "setColorFilter");

        try {
            XposedBridge.hookAllConstructors(PorterDuffColorFilter.class,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length > 0 && p.args[0] instanceof Integer)
                        p.args[0] = swap((Integer) p.args[0]);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("TkColor: filter fail: " + t);
        }

        try {
            XposedBridge.hookAllConstructors(ColorDrawable.class,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    if (p.args.length == 1 && p.args[0] instanceof Integer)
                        p.args[0] = swap((Integer) p.args[0]);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("TkColor: colordrawable ctor fail: " + t);
        }

        // 4. Theo dõi activity để bỏ qua màn splash
        try {
            XC_MethodHook trk = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    track(p.thisObject);
                }
            };
            XposedHelpers.findAndHookMethod(Activity.class, "onCreate",
                    Bundle.class, trk);
            XposedHelpers.findAndHookMethod(Activity.class, "onResume", trk);
        } catch (Throwable t) {
            XposedBridge.log("TkColor: activity hook fail: " + t);
        }

        XposedBridge.log("TkColor: hooked " + lp.packageName);
    }
}
