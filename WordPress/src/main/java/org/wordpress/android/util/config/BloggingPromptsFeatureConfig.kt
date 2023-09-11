package org.wordpress.android.util.config

import org.wordpress.android.BuildConfig
import javax.inject.Inject

class BloggingPromptsFeatureConfig @Inject constructor() {
    fun isEnabled(): Boolean {
        return BuildConfig.IS_JETPACK_APP
    }
}
