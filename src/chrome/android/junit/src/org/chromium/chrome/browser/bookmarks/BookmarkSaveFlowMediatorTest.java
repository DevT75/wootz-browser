// Copyright 2022 The Chromium Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.chrome.browser.bookmarks;

import android.content.Context;
import android.content.res.Resources;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.annotation.Config;

import org.chromium.base.test.BaseRobolectricTestRunner;
import org.chromium.base.test.util.Batch;
import org.chromium.base.test.util.Features;
import org.chromium.base.test.util.Features.DisableFeatures;
import org.chromium.base.test.util.Features.EnableFeatures;
import org.chromium.base.test.util.JniMocker;
import org.chromium.chrome.R;
import org.chromium.chrome.browser.commerce.PriceTrackingUtils;
import org.chromium.chrome.browser.commerce.PriceTrackingUtilsJni;
import org.chromium.chrome.browser.flags.ChromeFeatureList;
import org.chromium.chrome.browser.profiles.Profile;
import org.chromium.components.bookmarks.BookmarkId;
import org.chromium.components.bookmarks.BookmarkItem;
import org.chromium.components.commerce.core.CommerceSubscription;
import org.chromium.components.commerce.core.IdentifierType;
import org.chromium.components.commerce.core.ManagementType;
import org.chromium.components.commerce.core.ShoppingService;
import org.chromium.components.commerce.core.SubscriptionType;
import org.chromium.components.power_bookmarks.PowerBookmarkMeta;
import org.chromium.components.signin.identitymanager.IdentityManager;
import org.chromium.ui.modelutil.PropertyModel;
import org.chromium.ui.shadows.ShadowAppCompatResources;
import org.chromium.url.GURL;

/** Unit tests for {@link BookmarkSaveFlowMediator}. */
@Batch(Batch.UNIT_TESTS)
@RunWith(BaseRobolectricTestRunner.class)
@Config(
        manifest = Config.NONE,
        shadows = {ShadowAppCompatResources.class})
@DisableFeatures(ChromeFeatureList.ANDROID_IMPROVED_BOOKMARKS)
public class BookmarkSaveFlowMediatorTest {
    @Rule public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule public final Features.JUnitProcessor mProcessor = new Features.JUnitProcessor();
    @Rule public JniMocker mJniMocker = new JniMocker();

    private BookmarkSaveFlowMediator mMediator;
    private PropertyModel mPropertyModel =
            new PropertyModel(ImprovedBookmarkSaveFlowProperties.ALL_KEYS);

    @Mock private Context mContext;
    @Mock private Resources mResources;
    @Mock private Runnable mCloseRunnable;
    @Mock private BookmarkModel mModel;
    @Mock private ShoppingService mShoppingService;
    @Mock private CommerceSubscription mSubscription;
    @Mock private BookmarkImageFetcher mBookmarkImageFetcher;
    @Mock private Profile mProfile;
    @Mock private IdentityManager mIdentityManager;
    @Mock PriceTrackingUtils.Natives mMockPriceTrackingUtilsJni;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mJniMocker.mock(PriceTrackingUtilsJni.TEST_HOOKS, mMockPriceTrackingUtilsJni);
        Mockito.doReturn(mResources).when(mContext).getResources();
        mMediator =
                new BookmarkSaveFlowMediator(
                        mModel,
                        mPropertyModel,
                        mContext,
                        mCloseRunnable,
                        mShoppingService,
                        mBookmarkImageFetcher,
                        mProfile,
                        mIdentityManager);
        mMediator.setSubscriptionForTesting(mSubscription);
    }

    @Test
    public void testSubscribedInBackground() {
        mMediator.setPriceTrackingToggleVisualsOnly(false);
        Assert.assertFalse(
                mPropertyModel.get(BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_TOGGLED));
        Assert.assertEquals(
                R.drawable.price_tracking_disabled,
                (int)
                        mPropertyModel.get(
                                BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_START_ICON_RES));

        mMediator.onSubscribe(mSubscription, true);
        Assert.assertTrue(
                mPropertyModel.get(BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_TOGGLED));
        Assert.assertEquals(
                R.drawable.price_tracking_enabled_filled,
                (int)
                        mPropertyModel.get(
                                BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_START_ICON_RES));
        Mockito.verify(mShoppingService, Mockito.never())
                .subscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
        Mockito.verify(mShoppingService, Mockito.never())
                .unsubscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
    }

    @Test
    @EnableFeatures(ChromeFeatureList.ANDROID_IMPROVED_BOOKMARKS)
    public void testSubscribedInBackground_improved() {
        mMediator.setPriceTrackingToggleVisualsOnly(false);
        Assert.assertFalse(
                mPropertyModel.get(
                        ImprovedBookmarkSaveFlowProperties.PRICE_TRACKING_SWITCH_CHECKED));

        mMediator.onSubscribe(mSubscription, true);
        Assert.assertTrue(
                mPropertyModel.get(
                        ImprovedBookmarkSaveFlowProperties.PRICE_TRACKING_SWITCH_CHECKED));
        Mockito.verify(mShoppingService, Mockito.never())
                .subscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
        Mockito.verify(mShoppingService, Mockito.never())
                .unsubscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
    }

    // Ensure the toggle logic still works when the subscription object changes but has identical
    // information.
    @Test
    public void testSubscribed_differentObjects() {
        String clusterId = "1234";
        CommerceSubscription original =
                new CommerceSubscription(
                        SubscriptionType.PRICE_TRACK,
                        IdentifierType.PRODUCT_CLUSTER_ID,
                        clusterId,
                        ManagementType.USER_MANAGED,
                        null);
        CommerceSubscription clone =
                new CommerceSubscription(
                        SubscriptionType.PRICE_TRACK,
                        IdentifierType.PRODUCT_CLUSTER_ID,
                        clusterId,
                        ManagementType.USER_MANAGED,
                        null);

        mMediator.setSubscriptionForTesting(original);

        mMediator.setPriceTrackingToggleVisualsOnly(false);
        Assert.assertFalse(
                mPropertyModel.get(BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_TOGGLED));
        Assert.assertEquals(
                R.drawable.price_tracking_disabled,
                (int)
                        mPropertyModel.get(
                                BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_START_ICON_RES));

        mMediator.onSubscribe(clone, true);
        Assert.assertTrue(
                mPropertyModel.get(BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_TOGGLED));
        Assert.assertEquals(
                R.drawable.price_tracking_enabled_filled,
                (int)
                        mPropertyModel.get(
                                BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_START_ICON_RES));
        Mockito.verify(mShoppingService, Mockito.never())
                .subscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
        Mockito.verify(mShoppingService, Mockito.never())
                .unsubscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
    }

    @Test
    @EnableFeatures(ChromeFeatureList.ANDROID_IMPROVED_BOOKMARKS)
    public void testSubscribed_differentObjects_improved() {
        String clusterId = "1234";
        CommerceSubscription original =
                new CommerceSubscription(
                        SubscriptionType.PRICE_TRACK,
                        IdentifierType.PRODUCT_CLUSTER_ID,
                        clusterId,
                        ManagementType.USER_MANAGED,
                        null);
        CommerceSubscription clone =
                new CommerceSubscription(
                        SubscriptionType.PRICE_TRACK,
                        IdentifierType.PRODUCT_CLUSTER_ID,
                        clusterId,
                        ManagementType.USER_MANAGED,
                        null);

        mMediator.setSubscriptionForTesting(original);

        mMediator.setPriceTrackingToggleVisualsOnly(false);
        Assert.assertFalse(
                mPropertyModel.get(
                        ImprovedBookmarkSaveFlowProperties.PRICE_TRACKING_SWITCH_CHECKED));

        mMediator.onSubscribe(clone, true);
        Assert.assertTrue(
                mPropertyModel.get(
                        ImprovedBookmarkSaveFlowProperties.PRICE_TRACKING_SWITCH_CHECKED));
        Mockito.verify(mShoppingService, Mockito.never())
                .subscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
        Mockito.verify(mShoppingService, Mockito.never())
                .unsubscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
    }

    @Test
    public void testUnsubscribedInBackground() {
        mMediator.setPriceTrackingToggleVisualsOnly(true);
        Assert.assertTrue(
                mPropertyModel.get(BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_TOGGLED));
        Assert.assertEquals(
                R.drawable.price_tracking_enabled_filled,
                (int)
                        mPropertyModel.get(
                                BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_START_ICON_RES));

        mMediator.onUnsubscribe(mSubscription, true);
        Assert.assertFalse(
                mPropertyModel.get(BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_TOGGLED));
        Assert.assertEquals(
                R.drawable.price_tracking_disabled,
                (int)
                        mPropertyModel.get(
                                BookmarkSaveFlowProperties.NOTIFICATION_SWITCH_START_ICON_RES));
        Mockito.verify(mShoppingService, Mockito.never())
                .subscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
        Mockito.verify(mShoppingService, Mockito.never())
                .unsubscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
    }

    @Test
    @EnableFeatures(ChromeFeatureList.ANDROID_IMPROVED_BOOKMARKS)
    public void testUnsubscribedInBackground_improved() {
        mMediator.setPriceTrackingToggleVisualsOnly(true);
        Assert.assertTrue(
                mPropertyModel.get(
                        ImprovedBookmarkSaveFlowProperties.PRICE_TRACKING_SWITCH_CHECKED));

        mMediator.onUnsubscribe(mSubscription, true);
        Assert.assertFalse(
                mPropertyModel.get(
                        ImprovedBookmarkSaveFlowProperties.PRICE_TRACKING_SWITCH_CHECKED));
        Mockito.verify(mShoppingService, Mockito.never())
                .subscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
        Mockito.verify(mShoppingService, Mockito.never())
                .unsubscribe(Mockito.any(CommerceSubscription.class), Mockito.any());
    }

    @Test
    public void testShow_FromExplicitPriceTracking() {
        BookmarkId bookmarkId = new BookmarkId(0, 0);
        PowerBookmarkMeta meta = PowerBookmarkMeta.newBuilder().build();
        BookmarkItem item =
                new BookmarkItem(
                        bookmarkId,
                        "",
                        new GURL("http://example.com"),
                        false,
                        new BookmarkId(1, 0),
                        true,
                        false,
                        0,
                        false,
                        0,
                        false);
        Mockito.doReturn(item).when(mModel).getBookmarkById(Mockito.any());
        Mockito.doReturn("title").when(mModel).getBookmarkTitle(Mockito.any());

        mMediator.show(bookmarkId, meta, /* fromExplicitTrackUi= */ true, false, false);

        Mockito.verify(mMockPriceTrackingUtilsJni)
                .setPriceTrackingStateForBookmark(
                        Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.eq(true),
                        Mockito.any(),
                        Mockito.anyBoolean());
    }

    @Test
    public void testShow_NonPriceTracking() {
        BookmarkId bookmarkId = new BookmarkId(0, 0);
        BookmarkItem item =
                new BookmarkItem(
                        bookmarkId,
                        "",
                        new GURL("http://example.com"),
                        false,
                        new BookmarkId(1, 0),
                        true,
                        false,
                        0,
                        false,
                        0,
                        false);
        Mockito.doReturn(item).when(mModel).getBookmarkById(Mockito.any());
        Mockito.doReturn("title").when(mModel).getBookmarkTitle(Mockito.any());

        mMediator.show(bookmarkId, null, /* fromExplicitTrackUi= */ false, false, false);

        Mockito.verify(mMockPriceTrackingUtilsJni, Mockito.never())
                .setPriceTrackingStateForBookmark(
                        Mockito.any(),
                        Mockito.anyLong(),
                        Mockito.anyBoolean(),
                        Mockito.any(),
                        Mockito.anyBoolean());
    }
}
