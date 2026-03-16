package com.tablet.splitqs;

import android.content.res.Configuration;
import android.content.res.Resources;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class TabletLandscapeModule implements IXposedHookLoadPackage {

    private static final String SYSTEMUI = "com.android.systemui";

    private final Set<Integer> cachedQsColumnIds = new HashSet<>();
    private final Set<Integer> cachedIgnoredIds = new HashSet<>();

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {

        if (!SYSTEMUI.equals(lpparam.packageName)) return;

        try {
            Class<?> splitShadeCls = XposedHelpers.findClassIfExists(
                    "com.android.systemui.statusbar.policy.SplitShadeStateControllerImpl",
                    lpparam.classLoader
            );
            if (splitShadeCls != null) {
                XposedBridge.hookAllMethods(splitShadeCls, "shouldUseSplitNotificationShade", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isLandscape()) {
                            param.setResult(true);
                        }
                    }
                });
            }

            Class<?> tileRowKtCls = XposedHelpers.findClassIfExists(
                    "com.android.systemui.qs.panels.shared.model.TileRowKt",
                    lpparam.classLoader
            );
            if (tileRowKtCls != null) {
                XposedHelpers.findAndHookMethod(
                        tileRowKtCls,
                        "splitInRowsSequence",
                        int.class,
                        List.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                if (isLandscape()) {
                                    param.args[0] = 4;
                                }
                            }
                        }
                );
            }

            XposedHelpers.findAndHookMethod(Resources.class, "getInteger", int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    int id = (int) param.args[0];

                    if (cachedIgnoredIds.contains(id)) return;

                    if (cachedQsColumnIds.contains(id)) {
                        if (isLandscape()) param.setResult(4);
                        return;
                    }

                    Resources res = (Resources) param.thisObject;
                    try {
                        String name = res.getResourceEntryName(id);
                        if (name != null && name.contains("quick_settings") && name.contains("columns")) {
                            cachedQsColumnIds.add(id);
                            if (isLandscape()) {
                                param.setResult(4);
                            }
                        } else {
                            cachedIgnoredIds.add(id);
                        }
                    } catch (Resources.NotFoundException ignored) {
                        cachedIgnoredIds.add(id);
                    }
                }
            });

        } catch (Throwable t) {
            XposedBridge.log(t);
        }
    }

    private boolean isLandscape() {
        return Resources.getSystem().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }
}
