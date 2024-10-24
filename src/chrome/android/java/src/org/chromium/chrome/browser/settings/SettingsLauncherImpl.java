// Copyright 2020 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.jni_zero.CalledByNative;

import org.chromium.base.IntentUtils;
import org.chromium.chrome.browser.accessibility.settings.AccessibilitySettings;
import org.chromium.chrome.browser.autofill.settings.AutofillPaymentMethodsFragment;
import org.chromium.chrome.browser.browsing_data.ClearBrowsingDataFragment;
import org.chromium.chrome.browser.browsing_data.ClearBrowsingDataFragmentAdvanced;
import org.chromium.chrome.browser.browsing_data.ClearBrowsingDataTabsFragment;
import org.chromium.chrome.browser.password_manager.settings.PasswordSettings;
import org.chromium.chrome.browser.safety_check.SafetyCheckSettingsFragment;
import org.chromium.chrome.browser.sync.settings.GoogleServicesSettings;
import org.chromium.components.browser_ui.settings.SettingsLauncher;
import org.chromium.components.browser_ui.site_settings.SiteSettings;

/** Implementation class for launching a {@link SettingsActivity}. */
public class SettingsLauncherImpl implements SettingsLauncher {
    /** Can be used by native code to inject SettingsLauncher in modularized Java code. */
    @CalledByNative
    private static SettingsLauncher create() {
        return new SettingsLauncherImpl();
    }

    public SettingsLauncherImpl() {}

    @Override
    public void launchSettingsActivity(Context context) {
        launchSettingsActivity(context, SettingsFragment.MAIN);
    }

    @Override
    public void launchSettingsActivity(Context context, @SettingsFragment int settingsFragment) {
        Bundle fragmentArgs = null;
        switch (settingsFragment) {
            case SettingsFragment.CLEAR_BROWSING_DATA:
                fragmentArgs =
                        ClearBrowsingDataTabsFragment.createFragmentArgs(
                                context.getClass().getName());
                break;
            case SettingsFragment.CLEAR_BROWSING_DATA_ADVANCED_PAGE:
                fragmentArgs =
                        ClearBrowsingDataFragment.createFragmentArgs(
                                context.getClass().getName(),
                                /* isFetcherSuppliedFromOutside= */ false);
                break;
            case SettingsFragment.SAFETY_CHECK:
                fragmentArgs = SafetyCheckSettingsFragment.createBundle(true);
                break;
            case SettingsFragment.MAIN:
            case SettingsFragment.PAYMENT_METHODS:
            case SettingsFragment.SITE:
            case SettingsFragment.ACCESSIBILITY:
            case SettingsFragment.PASSWORDS:
            case SettingsFragment.GOOGLE_SERVICES:
                break;
        }
        launchSettingsActivity(context, getFragmentClassFromEnum(settingsFragment), fragmentArgs);
    }

    @Override
    public void launchSettingsActivity(
            Context context, @Nullable Class<? extends Fragment> fragment) {
        launchSettingsActivity(context, fragment, null);
    }

    @Override
    public void launchSettingsActivity(
            Context context,
            @Nullable Class<? extends Fragment> fragment,
            @Nullable Bundle fragmentArgs) {
        Intent intent = createSettingsActivityIntent(context, fragment, fragmentArgs);
        IntentUtils.safeStartActivity(context, intent);
    }

    @Override
    public Intent createSettingsActivityIntent(
            Context context, @Nullable Class<? extends Fragment> fragment) {
        return createSettingsActivityIntent(context, fragment, null);
    }

    @Override
    public Intent createSettingsActivityIntent(
            Context context,
            @Nullable Class<? extends Fragment> fragment,
            @Nullable Bundle fragmentArgs) {
        Intent intent = new Intent();
        intent.setClass(context, SettingsActivity.class);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        if (fragment != null) {
            intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT, fragment.getName());
        }
        if (fragmentArgs != null) {
            intent.putExtra(SettingsActivity.EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArgs);
        }
        return intent;
    }

    @Override
    public Intent createSettingsActivityIntent(
            Context context, @SettingsFragment int fragment, @Nullable Bundle fragmentArgs) {
        return createSettingsActivityIntent(
                context, getFragmentClassFromEnum(fragment), fragmentArgs);
    }

    private static @Nullable Class<? extends Fragment> getFragmentClassFromEnum(
            @SettingsFragment int fragment) {
        switch (fragment) {
            case SettingsFragment.MAIN:
                return null;
            case SettingsFragment.CLEAR_BROWSING_DATA:
                return ClearBrowsingDataTabsFragment.class;
            case SettingsFragment.CLEAR_BROWSING_DATA_ADVANCED_PAGE:
                return ClearBrowsingDataFragmentAdvanced.class;
            case SettingsFragment.PAYMENT_METHODS:
                return AutofillPaymentMethodsFragment.class;
            case SettingsFragment.SAFETY_CHECK:
                return SafetyCheckSettingsFragment.class;
            case SettingsFragment.SITE:
                return SiteSettings.class;
            case SettingsFragment.ACCESSIBILITY:
                return AccessibilitySettings.class;
            case SettingsFragment.PASSWORDS:
                return PasswordSettings.class;
            case SettingsFragment.GOOGLE_SERVICES:
                return GoogleServicesSettings.class;
        }
        assert false;
        return null;
    }
}
