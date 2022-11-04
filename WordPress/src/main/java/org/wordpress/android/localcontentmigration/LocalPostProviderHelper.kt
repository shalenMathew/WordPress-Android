package org.wordpress.android.localcontentmigration

import org.wordpress.android.fluxc.store.PostStore
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.localcontentmigration.LocalContentEntityData.PostData
import org.wordpress.android.localcontentmigration.LocalContentEntityData.PostsData
import javax.inject.Inject

class LocalPostProviderHelper @Inject constructor(
        private val siteStore: SiteStore,
        private val postStore: PostStore,
    ): LocalDataProviderHelper {
    override fun getData(localSiteId: Int?, localEntityId: Int?): LocalContentEntityData {
        localEntityId?.let { localPostId ->
            val post = postStore.getPostByLocalPostId(localPostId)
            return PostData(post = post)
        } ?: run {
            requireNotNull(localSiteId) { "A local site id must be specified when querying site content." }
            val site = siteStore.getSiteByLocalId(localSiteId)
            return PostsData(localIds = postStore.getPostsForSite(site).mapNotNull { it.id })
        }
    }
}