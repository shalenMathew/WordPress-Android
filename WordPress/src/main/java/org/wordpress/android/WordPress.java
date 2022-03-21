package org.wordpress.android;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.text.TextUtils;

import androidx.multidex.MultiDexApplication;

import com.android.volley.RequestQueue;

import org.wordpress.android.AppInitializer.StoryNotificationTrackerProvider;
import org.wordpress.android.fluxc.tools.FluxCImageLoader;
import org.wordpress.android.modules.AppComponent;
import org.wordpress.android.modules.DaggerAppComponent;
import org.wordpress.android.networking.RestClientUtils;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.BitmapLruCache;

import java.lang.reflect.Field;

import javax.inject.Inject;

import dagger.android.AndroidInjector;
import dagger.android.DispatchingAndroidInjector;
import dagger.android.HasAndroidInjector;

public class WordPress extends MultiDexApplication implements HasAndroidInjector {
    public static final String SITE = "SITE";
    public static final String LOCAL_SITE_ID = "LOCAL_SITE_ID";
    public static final String REMOTE_SITE_ID = "REMOTE_SITE_ID";
    public static String versionName;
    public static WordPressDB wpDB;
    public static boolean sAppIsInTheBackground = true;

    @Inject DispatchingAndroidInjector<Object> mDispatchingAndroidInjector;

    @Inject AppInitializer mAppInitializer;

    public static RequestQueue sRequestQueue;
    public static FluxCImageLoader sImageLoader;

    protected AppComponent mAppComponent;

    public AppComponent component() {
        return mAppComponent;
    }

    public static BitmapLruCache getBitmapCache() {
        return AppInitializer.Companion.getBitmapCache();
    }

    public static Context getContext() {
        return AppInitializer.Companion.getContext();
    }

    protected void initDaggerComponent() {
        mAppComponent = DaggerAppComponent.builder()
                                          .application(this)
                                          .build();
    }

    public static void updateContextLocale() {
        AppInitializer.Companion.updateContextLocale();
    }

    public static RestClientUtils getRestClientUtils() {
        return AppInitializer.Companion.getRestClientUtils();
    }

    public static RestClientUtils getRestClientUtilsV1_1() {
        return AppInitializer.Companion.getRestClientUtilsV1_1();
    }

    public static RestClientUtils getRestClientUtilsV1_2() {
        return AppInitializer.Companion.getRestClientUtilsV1_2();
    }

    public static RestClientUtils getRestClientUtilsV1_3() {
        return AppInitializer.Companion.getRestClientUtilsV1_3();
    }

    public static RestClientUtils getRestClientUtilsV2() {
        return AppInitializer.Companion.getRestClientUtilsV2();
    }

    public static RestClientUtils getRestClientUtilsV0() {
        return AppInitializer.Companion.getRestClientUtilsV0();
    }

    public static String getDefaultUserAgent() {
        return AppInitializer.Companion.getDefaultUserAgent();
    }

    public static String getUserAgent() {
        return AppInitializer.Companion.getUserAgent();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Init Dagger
        initDaggerComponent();
        component().inject(this);

        mAppInitializer.init();
    }

    public static final String USER_AGENT_APPNAME = "wp-android";

    public void wordPressComSignOut() {
        mAppInitializer.wordPressComSignOut();
    }

    /**
     * Gets a field from the project's BuildConfig using reflection. This is useful when flavors
     * are used at the project level to set custom fields.
     * based on: https://code.google.com/p/android/issues/detail?id=52962#c38
     *
     * @param application Used to find the correct file
     * @param fieldName The name of the field-to-access
     * @return The value of the field, or {@code null} if the field is not found.
     */
    public static Object getBuildConfigValue(Application application, String fieldName) {
        try {
            String packageName = application.getClass().getPackage().getName();
            Class<?> clazz = Class.forName(packageName + ".BuildConfig");
            Field field = clazz.getField(fieldName);
            return field.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Gets a field from the project's BuildConfig using reflection. This is useful when flavors
     * are used at the project level to set custom fields.
     * based on: https://code.google.com/p/android/issues/detail?id=52962#c38
     *
     * @param activity Used to get the Application instance
     * @param configValueName The name of the field-to-access
     * @return The string value of the field, or empty string if the field is not found.
     */
    public static String getBuildConfigString(Activity activity, String configValueName) {
        if (!BuildConfig.DEBUG) {
            return "";
        }

        String value = (String) WordPress.getBuildConfigValue(activity.getApplication(), configValueName);
        if (!TextUtils.isEmpty(value)) {
            AppLog.d(AppLog.T.NUX, "Auto-filled from build config: " + configValueName);
            return value;
        }

        return "";
    }

    public StoryNotificationTrackerProvider getStoryNotificationTrackerProvider() {
        return mAppInitializer.getStoryNotificationTrackerProvider();
    }

    @Override public AndroidInjector<Object> androidInjector() {
        return mDispatchingAndroidInjector;
    }
}
