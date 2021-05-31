package org.wordpress.android.fluxc.store

import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.single
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.wordpress.android.fluxc.model.BloggingRemindersMapper
import org.wordpress.android.fluxc.model.BloggingRemindersModel
import org.wordpress.android.fluxc.model.BloggingRemindersModel.Day.MONDAY
import org.wordpress.android.fluxc.persistence.BloggingRemindersDao
import org.wordpress.android.fluxc.persistence.BloggingRemindersDao.BloggingReminders
import org.wordpress.android.fluxc.test

@RunWith(MockitoJUnitRunner::class)
class BloggingRemindersStoreTest {
    @Mock lateinit var bloggingRemindersDao: BloggingRemindersDao
    @Mock lateinit var mapper: BloggingRemindersMapper
    private lateinit var store: BloggingRemindersStore
    private val siteId = 1

    @Before
    fun setUp() {
        store = BloggingRemindersStore(bloggingRemindersDao, mapper)
    }

    @Test
    fun `maps items emitted from dao`() = test {
        val dbEntity = BloggingReminders(siteId, monday = true)
        val domainModel = BloggingRemindersModel(siteId, setOf(MONDAY))
        whenever(bloggingRemindersDao.getBySiteId(siteId)).thenReturn(flowOf(dbEntity))
        whenever(mapper.toDomainModel(dbEntity)).thenReturn(domainModel)

        assertThat(store.bloggingRemindersModel(siteId).single()).isEqualTo(domainModel)
    }

    @Test
    fun `maps items stored to dao`() = test {
        val dbEntity = BloggingReminders(siteId, monday = true)
        val domainModel = BloggingRemindersModel(siteId, setOf(MONDAY))
        whenever(mapper.toDatabaseModel(domainModel)).thenReturn(dbEntity)

        store.updateBloggingReminders(domainModel)

        verify(bloggingRemindersDao).insert(dbEntity)
    }
}
