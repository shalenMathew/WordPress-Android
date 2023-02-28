package org.wordpress.android.ui.blaze.ui.blazewebview

import android.text.TextUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.store.AccountStore
import org.wordpress.android.fluxc.store.SiteStore
import org.wordpress.android.ui.WPWebViewActivity.getAuthenticationPostData
import org.wordpress.android.ui.blaze.BlazeActionEvent
import org.wordpress.android.ui.blaze.BlazeFeatureUtils
import org.wordpress.android.ui.blaze.BlazeFlowSource
import org.wordpress.android.ui.blaze.BlazeFlowStep
import org.wordpress.android.ui.blaze.BlazeUiState
import org.wordpress.android.ui.blaze.BlazeWebViewHeaderUiState
import org.wordpress.android.ui.blaze.BlazeWebViewContentUiState
import org.wordpress.android.ui.mysite.SelectedSiteRepository
import javax.inject.Inject

@HiltViewModel
class BlazeWebViewViewModel @Inject constructor(
    private val accountStore: AccountStore,
    private val blazeFeatureUtils: BlazeFeatureUtils,
    private val selectedSiteRepository: SelectedSiteRepository,
    private val siteStore: SiteStore
) : ViewModel() {
    private val hideCancelSteps = listOf(BLAZE_NON_DISMISSABLE_HASH)
    private lateinit var blazeFlowSource: BlazeFlowSource
    private lateinit var blazeFlowStep: BlazeFlowStep

    private val _actionEvents = Channel<BlazeActionEvent>(Channel.BUFFERED)
    val actionEvents = _actionEvents.receiveAsFlow()

    private val _blazeHeaderState = MutableStateFlow<BlazeWebViewHeaderUiState>(BlazeWebViewHeaderUiState.ShowAction())
    val blazeHeaderState: StateFlow<BlazeWebViewHeaderUiState> = _blazeHeaderState

    private val _model = MutableStateFlow(BlazeWebViewContentUiState())
    val model: StateFlow<BlazeWebViewContentUiState> = _model

    fun start(promoteScreen: BlazeUiState.PromoteScreen?, source: BlazeFlowSource) {
        blazeFlowSource = source
        val url = buildUrl(promoteScreen)
        blazeFlowStep = blazeFeatureUtils.extractCurrentStep(url)
        validateAndFinishIfNeeded()
        postScreenState(model.value.copy(url = url, addressToLoad = prepareUrl(url)))
    }

    private fun validateAndFinishIfNeeded() {
        if (accountStore.account.userName.isNullOrEmpty() || accountStore.accessToken.isNullOrEmpty()) {
            blazeFeatureUtils.trackFlowError(blazeFlowSource, blazeFlowStep)
            postActionEvent(BlazeActionEvent.FinishActivity)
        }
    }

    private fun buildUrl(promoteScreen: BlazeUiState.PromoteScreen?): String {
        val siteUrl = extractAndSanitizeSiteUrl()
        if (siteUrl.isEmpty()) {
            postActionEvent(BlazeActionEvent.FinishActivity)
        }

        val url = promoteScreen?.let {
            when (it) {
                is BlazeUiState.PromoteScreen.PromotePost -> {
                    BLAZE_CREATION_FLOW_POST.format(siteUrl, it.postUIModel.postId, blazeFlowSource.trackingName)
                }
                is BlazeUiState.PromoteScreen.Site -> BLAZE_CREATION_FLOW_SITE.format(
                    siteUrl,
                    blazeFlowSource.trackingName
                )
                is BlazeUiState.PromoteScreen.Page -> BLAZE_CREATION_FLOW_SITE.format(
                    siteUrl,
                    blazeFlowSource.trackingName
                )
            }
        } ?: BLAZE_CREATION_FLOW_SITE.format(siteUrl, blazeFlowSource.trackingName)
        return url
    }

    fun onHeaderActionClick() {
        blazeFeatureUtils.trackFlowCanceled(blazeFlowSource, blazeFlowStep)
        postActionEvent(BlazeActionEvent.FinishActivity)
    }

    private fun prepareUrl(url: String): String {
        val username = accountStore.account.userName
        val accessToken = accountStore.accessToken

        var addressToLoad = url

        // Custom domains are not properly authenticated due to a server side(?) issue, so this gets around that
        if (!addressToLoad.contains(WPCOM_DOMAIN)) {
            val wpComSites: List<SiteModel> = siteStore.wPComSites
            for (siteModel in wpComSites) {
                // Only replace the url if we know the unmapped url and if it's a custom domain
                if (!TextUtils.isEmpty(siteModel.unmappedUrl)
                    && !siteModel.url.contains(WPCOM_DOMAIN)
                ) {
                    addressToLoad = addressToLoad.replace(siteModel.url, siteModel.unmappedUrl)
                }
            }
        }
        // Call the public static method in WPWebViewActivity - no need to recreate functionality with a copy/paste
        return getAuthenticationPostData(WPCOM_LOGIN_URL, addressToLoad, username, "", accessToken)
    }

    private fun postHeaderUiState(state: BlazeWebViewHeaderUiState) {
        viewModelScope.launch {
            _blazeHeaderState.value = state
        }
    }

    private fun postScreenState(state: BlazeWebViewContentUiState) {
        viewModelScope.launch {
            _model.value = state
        }
    }

    private fun postActionEvent(actionEvent: BlazeActionEvent) {
        viewModelScope.launch {
            _actionEvents.send(actionEvent)
        }
    }

    private fun extractAndSanitizeSiteUrl(): String {
        return selectedSiteRepository.getSelectedSite()?.url?.replace(Regex(HTTP_PATTERN), "")?:""
    }

    fun hideOrShowCancelAction(url: String) {
        if (hideCancelSteps.any { url.contains(it) }) {
            postHeaderUiState(BlazeWebViewHeaderUiState.HideAction())
        } else {
            postHeaderUiState(BlazeWebViewHeaderUiState.ShowAction())
        }
    }

    fun onWebViewReceivedError() {
        blazeFeatureUtils.trackFlowError(blazeFlowSource, blazeFlowStep)
        postActionEvent(BlazeActionEvent.FinishActivity)
    }

    fun onRedirectToExternalBrowser(url: String) {
        postActionEvent(BlazeActionEvent.LaunchExternalBrowser(url))
    }

    fun updateBlazeFlowStep(url: String?) {
        url?.let {
            blazeFlowStep = blazeFeatureUtils.extractCurrentStep(it)
        }
    }

    companion object {
        const val WPCOM_LOGIN_URL = "https://wordpress.com/wp-login.php"
        const val WPCOM_DOMAIN = ".wordpress.com"

        private const val BASE_URL = "https://wordpress.com/advertising/"

        const val BLAZE_CREATION_FLOW_POST = "$BASE_URL%s?blazepress-widget=post-%d&_source=%s"
        const val BLAZE_CREATION_FLOW_SITE = "$BASE_URL%s?_source=%s"

        const val HTTP_PATTERN = "(https?://)"

        const val BLAZE_NON_DISMISSABLE_HASH = "step-4"
        const val BLAZE_COMPLETED_STEP_HASH = "step-5"
    }
}
