package org.chromium.chrome.browser.extensions;

import org.jni_zero.CalledByNative;
import org.jni_zero.JNINamespace;
import org.jni_zero.NativeMethods;

import org.chromium.base.ThreadUtils;
import org.chromium.chrome.browser.ChromeTabbedActivity;
import org.chromium.chrome.browser.tab.Tab;
import org.chromium.chrome.browser.tab.TabLaunchType;
import org.chromium.chrome.browser.tabmodel.TabCreator;
import org.chromium.chrome.browser.tabmodel.TabModel;
import org.chromium.chrome.browser.tabmodel.TabModelSelector;
import org.chromium.chrome.browser.tabmodel.TabModelUtils;
import org.chromium.content_public.browser.LoadUrlParams;

import android.util.Log;

@JNINamespace("extensions")
public class TabsApiHelper {

    private static ChromeTabbedActivity getChromeTabbedActivity() {
        return ChromeTabbedActivity.getActivity();
    }

    private static TabModelSelector getTabModelSelector() {
        ChromeTabbedActivity activity = getChromeTabbedActivity();
        return activity != null ? activity.getTabModelSelector() : null;
    }

    @CalledByNative
    private static Tab createTab(String url) {
        Log.d("TabsApiHelper", "Creating background tab with URL: " + url);
        return ThreadUtils.runOnUiThreadBlockingNoException(() -> {
            ChromeTabbedActivity activity = getChromeTabbedActivity();
            if (activity == null) return null;
    
            TabModelSelector selector = activity.getTabModelSelector();
            if (selector == null) return null;
    
            Tab currentTab = selector.getCurrentTab();
            boolean isIncognito = currentTab != null ? currentTab.isIncognito() : false;
    
            TabCreator tabCreator = activity.getTabCreator(isIncognito);
            if (tabCreator == null) return null;
    
            LoadUrlParams loadUrlParams = new LoadUrlParams(url);
            Tab newTab = tabCreator.createNewTab(
                loadUrlParams,
                TabLaunchType.FROM_LONGPRESS_BACKGROUND,
                currentTab);
    
            if (newTab == null) return null;
    
            return newTab;
        });
    }

    @CalledByNative
    private static boolean removeTab(int tabId) {
        Log.d("TabsApiHelper", "Removing tab with ID: " + tabId);
        return ThreadUtils.runOnUiThreadBlockingNoException(() -> {
            TabModelSelector selector = getTabModelSelector();
            if (selector == null) return false;

            TabModel model = selector.getModelForTabId(tabId);
            if (model == null) return false;

            return TabModelUtils.closeTabById(model, tabId, false);
        });
    }
}
