package org.wordpress.android.fluxc.module

import android.content.Context
import dagger.Module
import dagger.Provides
import org.wordpress.android.fluxc.persistence.BloggingRemindersDao
import org.wordpress.android.fluxc.persistence.PlanOffersDao
import org.wordpress.android.fluxc.persistence.WPAndroidDatabase
import org.wordpress.android.fluxc.persistence.WPAndroidDatabase.Companion.buildDb
import org.wordpress.android.fluxc.persistence.comments.CommentsDao
import javax.inject.Singleton

@Module
class DatabaseModule {
    @Singleton @Provides fun provideDatabase(context: Context): WPAndroidDatabase {
        return buildDb(context)
    }

    @Singleton @Provides fun provideBloggingRemindersDao(wpAndroidDatabase: WPAndroidDatabase): BloggingRemindersDao {
        return wpAndroidDatabase.bloggingRemindersDao()
    }

    @Singleton @Provides fun providePlanOffersDao(wpAndroidDatabase: WPAndroidDatabase): PlanOffersDao {
        return wpAndroidDatabase.planOffersDao()
    }

    @Singleton @Provides fun provideCommentsDao(wpAndroidDatabase: WPAndroidDatabase): CommentsDao {
        return wpAndroidDatabase.commentsDao()
    }
}
