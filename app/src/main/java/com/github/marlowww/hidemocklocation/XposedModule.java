package com.github.marlowww.hidemocklocation;

import android.content.ContentResolver;
import android.os.Bundle;
import android.provider.Settings;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


public class XposedModule implements IXposedHookZygoteInit, IXposedHookLoadPackage {

    public XSharedPreferences prefs;

    public XC_ProcessNameMethodHook hideAllowMockSettingHook;
    public XC_ProcessNameMethodHook hideMockProviderHook;
    public XC_ProcessNameMethodHook hideMockGooglePlayServicesHook;


    // Hook with additional member - processName
    // Used to whitelisting/blacklisting apps
    class XC_ProcessNameMethodHook extends XC_MethodHook {

        private String processName;

        private XC_MethodHook init(String processName){
            this.processName = processName;
            return this;
        }

        boolean isHidingEnabled() {
            Common.ListType listType = getListType();
            Set<String> apps = getAppList(listType);

            switch (listType) {
                case BLACKLIST:
                    if (apps.contains(processName))
                        return true;
                    break;
                case WHITELIST:
                    if (!apps.contains(processName))
                        return true;
            }
            return false;
        }
    }

    public Common.ListType getListType() {
        prefs.reload();
        return prefs.getString(Common.PREF_LIST_TYPE, Common.ListType.BLACKLIST.toString())
            .equals(Common.ListType.BLACKLIST.toString())
                ? Common.ListType.BLACKLIST : Common.ListType.WHITELIST;
    }

    public Set<String> getAppList(Common.ListType type) {
        prefs.reload();
        return prefs.getStringSet(type.toString(), new HashSet<String>(0));
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        // Hooking Settings.Secure API methods instead of internal methods - longer code, but more SDK independent.
        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getString",
                ContentResolver.class, String.class, hideAllowMockSettingHook.init(lpparam.processName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getInt",
                ContentResolver.class, String.class, hideAllowMockSettingHook.init(lpparam.processName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getInt",
                ContentResolver.class, String.class, int.class, hideAllowMockSettingHook.init(lpparam.processName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getFloat",
                ContentResolver.class, String.class, hideAllowMockSettingHook.init(lpparam.processName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getFloat",
                ContentResolver.class, String.class, float.class, hideAllowMockSettingHook.init(lpparam.processName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getLong",
                ContentResolver.class, String.class, hideAllowMockSettingHook.init(lpparam.processName));

        XposedHelpers.findAndHookMethod("android.provider.Settings.Secure", lpparam.classLoader, "getLong",
                ContentResolver.class, String.class, long.class, hideAllowMockSettingHook.init(lpparam.processName));

        // Additional info - not implemented - probably will not be implemented in future:
        //
        // There is one more method - getUriFor. Its returned value can be used
        // to listen for setting changes, without getting any settings values.
        // (Low risk of checking something only with this way)

        // Google Play Services
        XposedHelpers.findAndHookMethod("android.location.Location", lpparam.classLoader, "getExtras",
                hideMockGooglePlayServicesHook.init(lpparam.processName));

        // New way of checking if location is mocked, SDK 18+
        if (Common.JB_MR2_NEWER)
            XposedHelpers.findAndHookMethod("android.location.Location", lpparam.classLoader,
                    "isFromMockProvider", hideMockProviderHook.init(lpparam.processName));

        // Self hook - informing Activity that Xposed module is enabled
        if(lpparam.packageName.equals(Common.PACKAGE_NAME))
            XposedHelpers.findAndHookMethod(Common.ACTIVITY_NAME, lpparam.classLoader, "isModuleEnabled",
                    XC_MethodReplacement.returnConstant(true));
    }

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        prefs = new XSharedPreferences(Common.PACKAGE_NAME, Common.PACKAGE_PREFERENCES);
        prefs.makeWorldReadable();

        hideAllowMockSettingHook = new XC_ProcessNameMethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if (isHidingEnabled()) {
                    String methodName = param.method.getName();
                    String setting = (String) param.args[1];
                    if (setting.equals(Settings.Secure.ALLOW_MOCK_LOCATION)) {
                        switch (methodName) {
                            case "getInt":
                                param.setResult(0);
                                break;
                            case "getString":
                                param.setResult("0");
                                break;
                            case "getFloat":
                                param.setResult(0.0f);
                                break;
                            case "getLong":
                                param.setResult(0L);
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
        };

        hideMockProviderHook = new XC_ProcessNameMethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                if(isHidingEnabled())
                    param.setResult(false);
            }
        };

        hideMockGooglePlayServicesHook = new XC_ProcessNameMethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                if(isHidingEnabled()) {
                    Bundle extras = (Bundle) param.getResult();
                    if (extras != null && extras.getBoolean(Common.GMS_MOCK_KEY))
                        extras.putBoolean(Common.GMS_MOCK_KEY, false);
                    param.setResult(extras);
                }
            }
        };
    }
}