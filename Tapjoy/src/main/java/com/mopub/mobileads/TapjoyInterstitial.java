// Copyright (C) 2015 by Tapjoy Inc.
//
// This file is part of the Tapjoy SDK.
//
// By using the Tapjoy SDK in your software, you agree to the terms of the Tapjoy SDK License Agreement.
//
// The Tapjoy SDK is bound by the Tapjoy SDK License Agreement and can be found here: https://www.tapjoy.com/sdk/license

package com.mopub.mobileads;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.mopub.common.LifecycleListener;
import com.mopub.common.MoPub;
import com.mopub.common.logging.MoPubLog;
import com.mopub.common.privacy.PersonalInfoManager;
import com.mopub.common.util.Json;
import com.tapjoy.TJActionRequest;
import com.tapjoy.TJConnectListener;
import com.tapjoy.TJError;
import com.tapjoy.TJPlacement;
import com.tapjoy.TJPlacementListener;
import com.tapjoy.Tapjoy;

import org.json.JSONException;

import java.util.HashMap;
import java.util.Map;

import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CLICKED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.CUSTOM;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.LOAD_SUCCESS;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_ATTEMPTED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_FAILED;
import static com.mopub.common.logging.MoPubLog.AdapterLogEvent.SHOW_SUCCESS;

public class TapjoyInterstitial extends BaseAd implements TJPlacementListener {
    private static final String TAG = TapjoyInterstitial.class.getSimpleName();
    private static final String TJC_MOPUB_NETWORK_CONSTANT = "mopub";
    private static final String TJC_MOPUB_ADAPTER_VERSION_NUMBER = "4.1.0";

    // Configuration keys
    public static final String SDK_KEY = "sdkKey";
    public static final String DEBUG_ENABLED = "debugEnabled";
    public static final String PLACEMENT_NAME = "name";
    public static final String ADAPTER_NAME = TapjoyInterstitial.class.getSimpleName();
    private static final String AD_MARKUP_KEY = "adm";
    private String mPlacementName;

    @NonNull
    private TapjoyAdapterConfiguration mTapjoyAdapterConfiguration;

    private TJPlacement tjPlacement;
    private Handler mHandler;

    static {
        MoPubLog.log(CUSTOM, "Class initialized with network adapter version ", TJC_MOPUB_ADAPTER_VERSION_NUMBER);
    }

    public TapjoyInterstitial() {
        mTapjoyAdapterConfiguration = new TapjoyAdapterConfiguration();
    }

    @Override
    protected void load(@NonNull final Context context, @NonNull final AdData adData) {

        mHandler = new Handler(Looper.getMainLooper());

        fetchMoPubGDPRSettings();

        final Map<String, String> extras = adData.getExtras();
        mPlacementName = extras.get(PLACEMENT_NAME);
        if (TextUtils.isEmpty(mPlacementName)) {
            MoPubLog.log(mPlacementName, CUSTOM, ADAPTER_NAME, "Tapjoy interstitial loaded with empty 'name' field. Request will fail.");
            MoPubLog.log(mPlacementName, LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.NETWORK_NO_FILL.getIntCode(), MoPubErrorCode.NETWORK_NO_FILL);
        }

        final String adMarkup = extras.get(AD_MARKUP_KEY);

        boolean canRequestPlacement = true;
        if (!Tapjoy.isConnected()) {
            // Check if configuration data is available
            boolean enableDebug = Boolean.valueOf(extras.get(DEBUG_ENABLED));
            Tapjoy.setDebugEnabled(enableDebug);

            setAutomaticImpressionAndClickTracking(false);
            String sdkKey = extras.get(SDK_KEY);
            if (!TextUtils.isEmpty(sdkKey)) {
                MoPubLog.log(mPlacementName, CUSTOM, ADAPTER_NAME, "Connecting to Tapjoy via MoPub dashboard settings...");
                Tapjoy.connect(context, sdkKey, null, new TJConnectListener() {
                    @Override
                    public void onConnectSuccess() {
                        MoPubLog.log(mPlacementName, CUSTOM, "Tapjoy connected successfully");
                        mTapjoyAdapterConfiguration.setCachedInitializationParameters(context, extras);
                        MoPubLog.log(mPlacementName, CUSTOM, ADAPTER_NAME, "Tapjoy connected successfully");
                        createPlacement(context, mPlacementName, adMarkup);
                    }

                    @Override
                    public void onConnectFailure() {
                        MoPubLog.log(mPlacementName, CUSTOM, ADAPTER_NAME, "Tapjoy connect failed");
                    }
                });

                // If sdkKey is present via MoPub dashboard, we only want to request placement
                // after auto-connect succeeds
                canRequestPlacement = false;
            } else {
                MoPubLog.log(mPlacementName, CUSTOM, ADAPTER_NAME, "Tapjoy interstitial is initialized with empty 'sdkKey'. You must call Tapjoy.connect()");
            }
        }

        if (canRequestPlacement) {
            createPlacement(context, mPlacementName, adMarkup);
        }
    }

    private void createPlacement(Context context, String placementName, final String adMarkup) {
        tjPlacement = new TJPlacement(context, placementName, this);
        tjPlacement.setMediationName(TJC_MOPUB_NETWORK_CONSTANT);
        tjPlacement.setAdapterVersion(TJC_MOPUB_ADAPTER_VERSION_NUMBER);

        if (!TextUtils.isEmpty(adMarkup)) {
            try {
                Map<String, String> auctionData = Json.jsonStringToMap(adMarkup);
                tjPlacement.setAuctionData(new HashMap<>(auctionData));
            } catch (JSONException e) {
                MoPubLog.log(CUSTOM, ADAPTER_NAME, "Unable to parse auction data.");
            }
        }

        tjPlacement.requestContent();
        MoPubLog.log(placementName, LOAD_ATTEMPTED, ADAPTER_NAME);
    }

    // Pass the user consent from the MoPub SDK to Tapjoy as per GDPR
    private void fetchMoPubGDPRSettings() {

        PersonalInfoManager personalInfoManager = MoPub.getPersonalInformationManager();

        if (personalInfoManager != null) {
            Boolean gdprApplies = personalInfoManager.gdprApplies();

            if (gdprApplies != null) {
                Tapjoy.subjectToGDPR(gdprApplies);

                if (gdprApplies) {
                    String userConsented = MoPub.canCollectPersonalInformation() ? "1" : "0";

                    Tapjoy.setUserConsent(userConsented);
                } else {
                    Tapjoy.setUserConsent("-1");
                }
            }
        }
    }

    @Override
    protected void onInvalidate() {
        // No custom cleanup to do here.
    }

    @Nullable
    @Override
    protected LifecycleListener getLifecycleListener() {
        return null;
    }

    @NonNull
    @Override
    protected String getAdNetworkId() {
        return mPlacementName != null ? mPlacementName : "";
    }

    @Override
    protected boolean checkAndInitializeSdk(@NonNull final Activity launcherActivity,
                                            @NonNull final AdData adData){
        return false;
    }

    @Override
    protected void show() {
        if (tjPlacement != null) {
            MoPubLog.log(mPlacementName, SHOW_ATTEMPTED, ADAPTER_NAME);
            tjPlacement.showContent();
        } else {
            MoPubLog.log(mPlacementName, SHOW_FAILED, ADAPTER_NAME, MoPubErrorCode.NETWORK_NO_FILL.getIntCode(), MoPubErrorCode.NETWORK_NO_FILL);
        }
    }

    // Tapjoy

    @Override
    public void onRequestSuccess(final TJPlacement placement) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (placement.isContentAvailable()) {
                    if (mLoadListener != null) {
                        mLoadListener.onAdLoaded();
                    }
                    MoPubLog.log(mPlacementName, LOAD_SUCCESS, ADAPTER_NAME);
                } else {
                    if (mLoadListener != null) {
                        mLoadListener.onAdLoadFailed(MoPubErrorCode.NETWORK_NO_FILL);
                    }
                    MoPubLog.log(mPlacementName, LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.NETWORK_NO_FILL.getIntCode(), MoPubErrorCode.NETWORK_NO_FILL);
                }
            }
        });
    }

    @Override
    public void onRequestFailure(TJPlacement placement, TJError error) {

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mLoadListener != null) {
                    mLoadListener.onAdLoadFailed(MoPubErrorCode.NETWORK_NO_FILL);
                }
                MoPubLog.log(mPlacementName, LOAD_FAILED, ADAPTER_NAME, MoPubErrorCode.NETWORK_NO_FILL.getIntCode(), MoPubErrorCode.NETWORK_NO_FILL);
            }
        });
    }

    @Override
    public void onContentShow(TJPlacement placement) {

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mInteractionListener != null) {
                    mInteractionListener.onAdShown();
                    mInteractionListener.onAdImpression();
                }
                MoPubLog.log(mPlacementName, SHOW_SUCCESS, ADAPTER_NAME);
            }
        });
    }

    @Override
    public void onContentDismiss(TJPlacement placement) {
        MoPubLog.log(mPlacementName, CUSTOM, ADAPTER_NAME, "Tapjoy interstitial dismissed");

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mInteractionListener != null) {
                    mInteractionListener.onAdDismissed();
                }
            }
        });
    }

    @Override
    public void onContentReady(TJPlacement placement) {
    }

    @Override
    public void onClick(TJPlacement placement) {
        MoPubLog.log(mPlacementName, CLICKED, ADAPTER_NAME);
        if (mInteractionListener != null) {
            mInteractionListener.onAdClicked();
        }
    }

    @Override
    public void onPurchaseRequest(TJPlacement placement, TJActionRequest request,
                                  String productId) {
    }

    @Override
    public void onRewardRequest(TJPlacement placement, TJActionRequest request, String itemId,
                                int quantity) {
    }
}
