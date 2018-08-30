package org.wordpress.android.fluxc.store

import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.Payload
import org.wordpress.android.fluxc.action.ListAction
import org.wordpress.android.fluxc.annotations.action.Action
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.list.ListItemDataSource
import org.wordpress.android.fluxc.model.list.ListItemModel
import org.wordpress.android.fluxc.model.list.ListManager
import org.wordpress.android.fluxc.model.list.ListModel
import org.wordpress.android.fluxc.model.list.ListModel.ListType
import org.wordpress.android.fluxc.model.list.ListState
import org.wordpress.android.fluxc.network.BaseRequest.BaseNetworkError
import org.wordpress.android.fluxc.persistence.ListItemSqlUtils
import org.wordpress.android.fluxc.persistence.ListSqlUtils
import org.wordpress.android.util.AppLog
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListStore @Inject constructor(
    private val listSqlUtils: ListSqlUtils,
    private val listItemSqlUtils: ListItemSqlUtils,
    dispatcher: Dispatcher
) : Store(dispatcher) {
    @Subscribe(threadMode = ThreadMode.ASYNC)
    override fun onAction(action: Action<*>) {
        val actionType = action.type as? ListAction ?: return

        when (actionType) {
            ListAction.FETCH_LIST -> fetchList(action.payload as FetchListPayload)
            ListAction.UPDATE_LIST -> updateList(action.payload as UpdateListPayload)
        }
    }

    override fun onRegister() {
        AppLog.d(AppLog.T.API, ListStore::class.java.simpleName + " onRegister")
    }

    fun <T> getListManager(site: SiteModel, listType: ListType, dataSource: ListItemDataSource<T>): ListManager<T> {
        val listModel = getListModel(site.id, listType)
        val listItems = if (listModel != null) {
            listItemSqlUtils.getListItems(listModel.id)
        } else emptyList()
        return ListManager(mDispatcher, site, listType, listModel?.getState(), listItems, dataSource)
    }

    private fun fetchList(payload: FetchListPayload) {
        val listModel = getListModel(payload.site.id, payload.listType)
        val state = listModel?.getState()
        if (payload.loadMore && state?.canLoadMore() != true) {
            // We can't load more right now, ignore
            return
        } else if (!payload.loadMore && state?.isFetchingFirstPage() == true) {
            // If we are already fetching the first page, ignore
            return
        }
        val newState = if (payload.loadMore) ListState.LOADING_MORE else ListState.FETCHING_FIRST_PAGE
        listSqlUtils.insertOrUpdateList(payload.site.id, payload.listType, newState)
        emitChange(OnListChanged(payload.site.id, payload.listType, null))

        when(payload.listType) {
            ListModel.ListType.POSTS_ALL -> TODO()
            ListModel.ListType.POSTS_SCHEDULED -> TODO()
        }
    }

    private fun updateList(payload: UpdateListPayload) {
        if (!payload.isError) {
            if (!payload.loadedMore) {
                deleteListItems(payload.localSiteId, payload.listType)
            }
            val state = if (payload.canLoadMore) ListState.CAN_LOAD_MORE else ListState.FETCHED
            listSqlUtils.insertOrUpdateList(payload.localSiteId, payload.listType, state)
            val listModel = getListModel(payload.localSiteId, payload.listType)
            if (listModel != null) { // Sanity check
                // Ensure the listId is set correctly for ListItemModels
                listItemSqlUtils.insertItemList(payload.remoteItemIds.map { remoteItemId ->
                    val listItemModel = ListItemModel()
                    listItemModel.listId  = listModel.id
                    listItemModel.remoteItemId = remoteItemId
                    return@map listItemModel
                })
            }
        } else {
            listSqlUtils.insertOrUpdateList(payload.localSiteId, payload.listType, ListState.ERROR)
        }
        emitChange(OnListChanged(payload.localSiteId, payload.listType, payload.error))
    }

    private fun getListModel(localSiteId: Int, listType: ListType): ListModel? =
            listSqlUtils.getList(localSiteId, listType)

    private fun deleteListItems(localSiteId: Int, listType: ListType) {
        getListModel(localSiteId, listType)?.let {
            listItemSqlUtils.deleteItems(it.id)
        }
    }

    class OnListChanged(
        val localSiteId: Int,
        val listType: ListType,
        error: UpdateListError?
    ) : Store.OnChanged<UpdateListError>() {
        init {
            this.error = error
        }
    }

    class FetchListPayload(
        val site: SiteModel,
        val listType: ListType,
        val loadMore: Boolean = false
    ) : Payload<BaseNetworkError>()

    class UpdateListPayload(
        val localSiteId: Int,
        val listType: ListType,
        val remoteItemIds: List<Long>,
        val loadedMore: Boolean,
        val canLoadMore: Boolean,
        error: UpdateListError?
    ) : Payload<UpdateListError>() {
        init {
            this.error = error
        }
    }

    class UpdateListError(val type: UpdateListErrorType, val message: String? = null) : Store.OnChangedError

    enum class UpdateListErrorType {
        GENERIC_ERROR
    }
}
