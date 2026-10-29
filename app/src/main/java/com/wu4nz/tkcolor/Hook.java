package com.wu4nz.tkcolor;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
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

    // true = ghi log để dò nút tim, xong thì đổi thành false
    private static final boolean DEBUG = true;

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

    private static ColorMatrixColorFilter hueFilter(float deg) {
        float a = (float) Math.toRadians(deg);
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        float lr = 0.213f, lg = 0.715f, lb = 0.072f;
        float[] m = {
            lr + c * (1 - lr) - s * lr, lg - c * lg - s * lg, lb - c * lb + s * (1 - lb), 0, 0,
            lr - c * lr + s * 0.143f, lg + c * (1 - lg) + s * 0.140f, lb - c * lb - s * 0.283f, 0, 0,
            lr - c * lr - s * (1 - lr), lg - c * lg + s * lg, lb + c * (1 - lb) + s * lb, 0, 0,
            0, 0, 0, 1, 0
        };
        return new ColorMatrixColorFilter(m);
    }

    // hook method có tham số đầu tiên là màu (int)
    private static void hookIntArg(Class<?> cls, String method) {
        try {
            XposedHelpers.findAndHookMethod(cls, method, int.class,
                    new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam p) {
                    p.args[0] = swap((Integer) p.args[0]);
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("TkColor: " + cls.getSimpleName() + "."
                    + method + " fail: " + t);
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

        // 5. Tim: xoay hue cho ImageView dùng resource tên digg/like/heart
        try {
            final ColorMatrixColorFilter hueF = hueFilter(HUE - 350f);
            XposedHelpers.findAndHookMethod(ImageView.class,
                    "setImageResource", int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam p) {
                    try {
                        ImageView v = (ImageView) p.thisObject;
                        int id = (Integer) p.args[0];
                        String n = v.getResources().getResourceEntryName(id)
                                .toLowerCase();
                        if (n.contains("digg") || n.contains("like")
                                || n.contains("heart")) {
                            XposedBridge.log("TkColor: res " + n);
                            v.setColorFilter(hueF);
                        }
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("TkColor: heart fail: " + t);
        }

        // 6. DEBUG: xem nút tim là view gì
        if (DEBUG) {
            try {
                XposedHelpers.findAndHookMethod(View.class, "performClick",
                        new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        try {
                            View v = (View) p.thisObject;
                            String id = "none";
                            try {
                                if (v.getId() != -1)
                                    id = v.getResources()
                                            .getResourceEntryName(v.getId());
                            } catch (Throwable ignored) { }
                            StringBuilder sb = new StringBuilder();
                            Object o = v;
                            for (int i = 0; i < 4 && o instanceof View; i++) {
                                sb.append(o.getClass().getName()).append(" < ");
                                o = ((View) o).getParent();
                            }
                            XposedBridge.log("TkColor: click id=" + id
                                    + " chain=" + sb);
                        } catch (Throwable ignored) { }
                    }
                });
                XposedHelpers.findAndHookMethod(ImageView.class,
                        "setImageDrawable", Drawable.class,
                        new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (p.args[0] != null)
                            XposedBridge.log("TkColor: img "
                                    + p.thisObject.getClass().getName()
                                    + " drawable="
                                    + p.args[0].getClass().getName());
                    }
                });
            } catch (Throwable t) {
                XposedBridge.log("TkColor: debug fail: " + t);
            }
        }

        XposedBridge.log("TkColor: hooked " + lp.packageName);
    }
                            }
