package me.rerere.rikkahub.ui.pages.travel

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.db.entity.FavoriteEntity
import me.rerere.rikkahub.data.datastore.TRAVEL_PLANNER_ASSISTANT_ID
import me.rerere.rikkahub.data.favorite.NodeFavoriteAdapter
import me.rerere.rikkahub.data.files.FilesManager
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.FavoriteType
import me.rerere.rikkahub.data.model.TravelItineraryDay
import me.rerere.rikkahub.data.model.TravelItineraryItem
import me.rerere.rikkahub.data.model.TravelPlan
import me.rerere.rikkahub.data.model.TravelPoi
import me.rerere.rikkahub.data.model.TravelRecommendationCategory
import me.rerere.rikkahub.data.model.TravelRecommendationItem
import me.rerere.rikkahub.data.model.TravelSearchSuggestion
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.rikkahub.data.repository.FavoriteRepository
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.webview.WebView
import me.rerere.rikkahub.ui.components.webview.rememberWebViewState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.chat.ChatVM
import me.rerere.rikkahub.ui.pages.chat.TravelHubUiState
import me.rerere.rikkahub.utils.base64Decode
import me.rerere.rikkahub.utils.stripMarkdown
import org.json.JSONObject
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.uuid.Uuid
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

private const val ZHITU_ASSET_URL = "file:///android_asset/zhitu.html"
private const val ZHITU_DEBUG_TAG = "ZhituTravelHub"
private const val ZHITU_ENABLE_RUNTIME_PROBES = false
private const val ZHITU_PROJECT_PATCH_SCRIPT = """
(function () {
    function updateImeLayout() {
        var input = document.getElementById('chat-input');
        var chatContainer = document.getElementById('chat-container');
        var bottomBar = document.getElementById('zhitu-bottom-shell') || (input ? input.closest('.absolute') : null);
        var root = document.documentElement;
        var viewport = window.visualViewport;
        var keyboardInset = 0;
        var appHeight = Math.max(320, Math.round(window.innerHeight || (viewport && viewport.height) || 0));

        if (viewport) {
            keyboardInset = Math.max(0, window.innerHeight - viewport.height - viewport.offsetTop);
        }

        if (root && root.style) {
            root.style.setProperty('--zhitu-app-height', appHeight + 'px');
        }

        if (bottomBar) {
            var bottomBarHeight = Math.ceil(bottomBar.getBoundingClientRect ? (bottomBar.getBoundingClientRect().height || 0) : 0);
            var basePaddingBottom = Math.max(bottomBarHeight + 24, 128);
            var extraPaddingBottom = keyboardInset > 0 ? keyboardInset + 24 : 0;
            if (root && root.style) {
                root.style.setProperty('--zhitu-bottom-bar-height', bottomBarHeight + 'px');
                root.style.setProperty('--zhitu-chat-bottom-padding', (basePaddingBottom + extraPaddingBottom) + 'px');
            }
            bottomBar.style.transition = 'transform 180ms ease-out';
            bottomBar.style.transform = keyboardInset > 0 ? 'translateY(-' + keyboardInset + 'px)' : 'translateY(0)';
            bottomBar.style.bottom = '0';
        }

        if (chatContainer) {
            chatContainer.style.minHeight = '0';
            chatContainer.style.paddingBottom = 'var(--zhitu-chat-bottom-padding)';
            if (document.activeElement === input) {
                requestAnimationFrame(function () {
                    chatContainer.scrollTop = chatContainer.scrollHeight;
                });
            }
        }
    }

    window.__ZHITU_APPLY_PROJECT_PATCHES__ = function () {
        updateImeLayout();
    };

    if (!window.__ZHITU_PROJECT_PATCH_BOUND__) {
        window.__ZHITU_PROJECT_PATCH_BOUND__ = true;

        var observer = new MutationObserver(function () {
            requestAnimationFrame(function () {
                if (window.__ZHITU_APPLY_PROJECT_PATCHES__) {
                    window.__ZHITU_APPLY_PROJECT_PATCHES__();
                }
            });
        });

        if (document.body) {
            observer.observe(document.body, {
                childList: true,
                subtree: true,
                characterData: true
            });
        }

        if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', updateImeLayout);
            window.visualViewport.addEventListener('scroll', updateImeLayout);
        }

        window.addEventListener('resize', updateImeLayout);

        var input = document.getElementById('chat-input');
        if (input && !input.dataset.zhituImeBound) {
            input.dataset.zhituImeBound = '1';
            input.addEventListener('focus', function () {
                setTimeout(updateImeLayout, 80);
            });
            input.addEventListener('blur', function () {
                setTimeout(updateImeLayout, 80);
            });
            input.addEventListener('click', function () {
                setTimeout(updateImeLayout, 80);
            });
        }
    }

    if (window.__ZHITU_APPLY_PROJECT_PATCHES__) {
        window.__ZHITU_APPLY_PROJECT_PATCHES__();
    }
})();
"""

private val zhituJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private enum class ZhituShellTab(val value: String) {
    Home("home"),
    Ai("ai"),
    Map("map"),
    Itinerary("itinerary"),
    Hotel("hotel"),
    Food("food"),
    Activity("activity"),
    Profile("profile"),
}

private fun normalizeShellTab(value: String): ZhituShellTab = when (value.lowercase()) {
    "map" -> ZhituShellTab.Map
    "plan", "trip", "itinerary", "schedule" -> ZhituShellTab.Itinerary
    "hotel", "hotels" -> ZhituShellTab.Hotel
    "food", "foods" -> ZhituShellTab.Food
    "activity", "activities" -> ZhituShellTab.Activity
    "mine", "my", "profile" -> ZhituShellTab.Profile
    "ai", "assistant" -> ZhituShellTab.Ai
    else -> ZhituShellTab.Home
}

private class ZhituAndroidBridge(
    private val mainHandler: Handler,
    private val onAction: (action: String, payload: JSONObject?) -> Unit,
) {
    @JavascriptInterface
    fun postMessage(message: String) {
        runCatching { JSONObject(message) }
            .onSuccess { envelope ->
                if (envelope.optString("type") != "action") return
                val action = envelope.optString("action")
                if (action.isBlank()) return
                val payload = envelope.optJSONObject("payload")
                mainHandler.post {
                    onAction(action, payload)
                }
            }
    }
}

@Serializable
private data class ZhituShellState(
    val version: Int = 1,
    val context: String = "android",
    val currentTab: String,
    val conversation: ZhituConversationState,
    val assistantRecommendation: ZhituAssistantRecommendationState? = null,
    val travelPlan: ZhituTravelPlanState? = null,
    val travelUiState: ZhituTravelUiStateState? = null,
    val user: ZhituUserState,
    val historyConversations: List<ZhituHistoryConversationState> = emptyList(),
    val historyTrips: List<ZhituHistoryTripState> = emptyList(),
    val favoriteItems: List<ZhituFavoriteItemState> = emptyList(),
    val currentTripSummary: ZhituCurrentTripSummaryState? = null,
    val profileUiState: ZhituProfileUiState = ZhituProfileUiState(),
    val availableActions: ZhituAvailableActionsState = ZhituAvailableActionsState(),
    val navigationTargets: ZhituNavigationTargetsState = ZhituNavigationTargetsState(),
)

@Serializable
private data class ZhituConversationState(
    val id: String,
    val title: String,
    val isGenerating: Boolean,
    val suggestions: List<String>,
    val messages: List<ZhituMessageState>,
)

@Serializable
private data class ZhituMessageState(
    val id: String,
    val role: String,
    val text: String,
    val createdAt: String,
)

@Serializable
private data class ZhituTravelUiStateState(
    val searchQuery: String,
    val weatherSummary: String,
    val selectedMapFilter: String,
    val selectedDestination: ZhituSuggestionState? = null,
    val suggestions: List<ZhituSuggestionState> = emptyList(),
)

@Serializable
private data class ZhituSuggestionState(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
private data class ZhituTravelPlanState(
    val brief: ZhituTravelBriefState? = null,
    val hotels: List<ZhituRecommendationState> = emptyList(),
    val foods: List<ZhituRecommendationState> = emptyList(),
    val activities: List<ZhituRecommendationState> = emptyList(),
    val pois: List<ZhituPoiState> = emptyList(),
    val itineraryDays: List<ZhituItineraryDayState> = emptyList(),
    val status: String = "",
)

@Serializable
private data class ZhituTravelBriefState(
    val destination: String = "",
    val origin: String = "",
    val dateRange: String = "",
    val days: Int? = null,
    val travelerCount: Int? = null,
    val budgetText: String = "",
    val budgetLevel: String = "",
    val travelStyleTags: List<String> = emptyList(),
    val transportPreferences: List<String> = emptyList(),
    val userIntentSummary: String = "",
)

@Serializable
private data class ZhituRecommendationState(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val tags: List<String> = emptyList(),
    val reason: String = "",
    val priceHint: String = "",
    val ratingText: String = "",
    val area: String = "",
    val inventoryHint: String = "",
    val bookingUrl: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
    val distanceText: String = "",
)

@Serializable
private data class ZhituAssistantRecommendationState(
    val category: String,
    val title: String,
    val message: String,
    val activeSort: String = "recommended",
    val sortOptions: List<ZhituRecommendationSortOptionState> = emptyList(),
    val items: List<ZhituRecommendationState> = emptyList(),
    val emptyTitle: String = "",
    val emptyDescription: String = "",
)

@Serializable
private data class ZhituRecommendationSortOptionState(
    val key: String,
    val label: String,
    val active: Boolean = false,
)

@Serializable
private data class ZhituPoiState(
    val id: String,
    val name: String,
    val category: String = "",
    val address: String = "",
    val lat: Double? = null,
    val lon: Double? = null,
)

@Serializable
private data class ZhituItineraryDayState(
    val dayIndex: Int,
    val title: String,
    val dateText: String = "",
    val weatherHint: String = "",
    val items: List<ZhituItineraryItemState> = emptyList(),
)

@Serializable
private data class ZhituItineraryItemState(
    val id: String,
    val timeSlot: String = "",
    val title: String,
    val description: String = "",
    val category: String = "",
    val estimatedCost: String = "",
    val transportHint: String = "",
)

@Serializable
private data class ZhituUserState(
    val name: String,
    val subtitle: String = "",
    val stats: List<ZhituStatState> = emptyList(),
)

@Serializable
private data class ZhituHistoryConversationState(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val preview: String = "",
    val updatedAt: String = "",
)

@Serializable
private data class ZhituHistoryTripState(
    val id: String,
    val conversationId: String,
    val title: String,
    val destination: String = "",
    val dateRange: String = "",
    val days: Int? = null,
    val summary: String = "",
    val updatedAt: String = "",
    val status: String = "",
)

@Serializable
private data class ZhituFavoriteItemState(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val category: String = "",
    val reason: String = "",
    val conversationId: String? = null,
    val nodeId: String? = null,
)

@Serializable
private data class ZhituCurrentTripSummaryState(
    val conversationId: String? = null,
    val title: String,
    val destination: String = "",
    val summary: String = "",
    val days: Int? = null,
    val dateRange: String = "",
    val status: String = "",
)

@Serializable
private data class ZhituProfileUiState(
    val activeTab: String = "history",
)

@Serializable
private data class ZhituStatState(
    val label: String,
    val value: String,
)

@Serializable
private data class ZhituAvailableActionsState(
    val sendMessage: Boolean = true,
    val generatePlan: Boolean = true,
    val openMap: Boolean = true,
    val openRecommendations: Boolean = true,
    val openLegacyPanel: Boolean = true,
    val exportConversation: Boolean = false,
)

@Serializable
private data class ZhituNavigationTargetsState(
    val legacyPanel: Boolean = true,
    val conversationList: Boolean = false,
    val workbench: Boolean = false,
)

@Composable
fun TravelHubHtmlPage(
    id: Uuid,
    text: String? = null,
    files: List<android.net.Uri> = emptyList(),
    nodeId: Uuid? = null,
    startTab: String = "home",
    highlightPoiId: String? = null,
    preferredMapFilter: String? = null,
) {
    val vm: ChatVM = koinViewModel(parameters = { parametersOf(id.toString()) })
    val filesManager: FilesManager = koinInject()
    val conversationRepo: ConversationRepository = koinInject()
    val favoriteRepository: FavoriteRepository = koinInject()
    val nav = LocalNavController.current
    val context = LocalContext.current
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val historyConversationCount by vm.historyConversationCount.collectAsStateWithLifecycle()
    val assistantConversations by conversationRepo
        .getConversationsOfAssistant(conversation.assistantId)
        .collectAsStateWithLifecycle(emptyList())
    val favoriteEntities by favoriteRepository
        .listByType(FavoriteType.NODE)
        .collectAsStateWithLifecycle(emptyList())
    val ui = vm.travelHubUiState
    var currentTab by rememberSaveable(id.toString(), startTab) {
        mutableStateOf(normalizeShellTab(startTab))
    }
    var profileTab by rememberSaveable(id.toString()) {
        mutableStateOf("history")
    }
    var handled by rememberSaveable(id.toString()) { mutableStateOf(false) }
    var currentHighlightPoiId by rememberSaveable(id.toString(), highlightPoiId) {
        mutableStateOf(highlightPoiId)
    }
    var inlineRecommendationCategory by rememberSaveable(id.toString()) {
        mutableStateOf<String?>(null)
    }
    var inlineRecommendationSort by rememberSaveable(id.toString()) {
        mutableStateOf("recommended")
    }

    fun clearInlineRecommendations() {
        inlineRecommendationCategory = null
        inlineRecommendationSort = "recommended"
    }

    fun showInlineRecommendations(category: String, sort: String = "recommended") {
        inlineRecommendationCategory = normalizeRecommendationCategory(category) ?: return
        inlineRecommendationSort = normalizeRecommendationSort(sort)
        currentTab = ZhituShellTab.Ai
    }

    LaunchedEffect(conversation.travelPlanningState) {
        if (conversation.travelPlanningState.name == "Generated" && currentTab != ZhituShellTab.Map) {
            currentTab = ZhituShellTab.Itinerary
        }
    }

    LaunchedEffect(conversation.travelPlan?.brief?.destination) {
        conversation.travelPlan?.brief?.destination?.takeIf { it.isNotBlank() }?.let(vm::ensureTravelDestinationContext)
    }

    LaunchedEffect(currentTab, preferredMapFilter) {
        if (currentTab == ZhituShellTab.Map && !preferredMapFilter.isNullOrBlank()) {
            vm.selectTravelMapFilter(preferredMapFilter)
        }
    }

    LaunchedEffect(currentTab, inlineRecommendationCategory) {
        if (inlineRecommendationCategory == null && currentTab in setOf(
                ZhituShellTab.Hotel,
                ZhituShellTab.Food,
                ZhituShellTab.Activity,
            )
        ) {
            showInlineRecommendations(currentTab.value)
        }
    }

    LaunchedEffect(text, files, conversation.messageNodes.size) {
        if (handled || conversation.messageNodes.isNotEmpty()) return@LaunchedEffect
        val decoded = runCatching { text?.base64Decode().orEmpty() }.getOrDefault(text.orEmpty())
        val localFiles = if (files.isNotEmpty()) filesManager.createChatFilesByContents(files) else emptyList()
        val contentTypes = files.mapNotNull { filesManager.getFileMimeType(it) }
        val parts = buildList {
            localFiles.forEachIndexed { index, file ->
                when {
                    contentTypes.getOrNull(index)?.startsWith("image/") == true -> add(UIMessagePart.Image(file.toString()))
                    contentTypes.getOrNull(index)?.startsWith("video/") == true -> add(UIMessagePart.Video(file.toString()))
                    contentTypes.getOrNull(index)?.startsWith("audio/") == true -> add(UIMessagePart.Audio(file.toString()))
                    else -> add(
                        UIMessagePart.Document(
                            url = file.toString(),
                            fileName = "attachment",
                            mime = contentTypes.getOrNull(index) ?: "application/octet-stream",
                        ),
                    )
                }
            }
            if (decoded.isNotBlank()) add(UIMessagePart.Text(decoded))
        }

        if (parts.isNotEmpty()) {
            if (localFiles.isEmpty() && decoded.isNotBlank()) {
                vm.startTravelPlanning(decoded)
            } else {
                vm.ensureTravelAssistantSelected()
                vm.handleMessageSend(parts)
            }
            currentTab = ZhituShellTab.Ai
        }
        handled = true
    }

    val weatherSummary = vm.homeWeatherSummary(conversation)
    val visibleMapPois = vm.visibleMapPois(conversation, currentHighlightPoiId)
    val shellHistoryConversations = remember(assistantConversations) {
        assistantConversations
            .sortedByDescending { it.updateAt }
            .take(6)
            .map { it.toZhituHistoryConversationState() }
    }
    val shellHistoryTrips = remember(assistantConversations) {
        assistantConversations
            .sortedByDescending { it.updateAt }
            .mapNotNull { it.toZhituHistoryTripState() }
            .take(6)
    }
    val shellFavoriteItems = remember(favoriteEntities, conversation) {
        favoriteEntities
            .mapNotNull { it.toZhituFavoriteItemState() }
            .ifEmpty { conversation.travelPlan.toFallbackFavoriteItems() }
            .take(8)
    }
    val currentTripSummary = remember(conversation) {
        conversation.toZhituCurrentTripSummaryState()
    }
    val openNativeMap = rememberUpdatedState<(JSONObject?) -> Unit> { payload ->
        payload?.optString("filter")?.takeIf { it.isNotBlank() }?.let(vm::selectTravelMapFilter)
        val nextHighlightPoiId = payload?.optString("poiId")?.takeIf { it.isNotBlank() } ?: currentHighlightPoiId
        currentHighlightPoiId = nextHighlightPoiId
        nav.navigate(Screen.ChatMap(id.toString(), nodeId = nextHighlightPoiId)) { launchSingleTop = true }
    }
    val exportTripMarkdown = rememberUpdatedState {
        val file = exportTripMarkdownFile(context, conversation)
        Toast.makeText(context, "宸插鍑哄埌 ${file.absolutePath}", Toast.LENGTH_LONG).show()
    }
    val shareTripMarkdown = rememberUpdatedState {
        shareTripMarkdownFile(context, conversation)
    }

    val latestActionHandler = rememberUpdatedState<(String, JSONObject?) -> Unit> { action, payload ->
        when (action) {
            "open_profile" -> {
                profileTab = "history"
            }

            "close_profile" -> Unit

            "switch_profile_tab" -> {
                profileTab = if (payload?.optString("tab") == "favorites") "favorites" else "history"
            }

            "send_message" -> {
                val input = payload?.optString("text").orEmpty().trim()
                if (input.isBlank()) return@rememberUpdatedState
                clearInlineRecommendations()
                vm.startTravelPlanning(input)
                currentTab = ZhituShellTab.Ai
            }

            "switch_tab" -> {
                val nextTab = normalizeShellTab(payload?.optString("tab").orEmpty())
                when (nextTab) {
                    ZhituShellTab.Hotel,
                    ZhituShellTab.Food,
                    ZhituShellTab.Activity -> showInlineRecommendations(nextTab.value)

                    else -> {
                        clearInlineRecommendations()
                        currentTab = nextTab
                    }
                }
            }

            "request_recommendations" -> {
                val category = payload?.optString("category").orEmpty()
                showInlineRecommendations(category, payload?.optString("sort").orEmpty())
            }

            "sort_recommendations" -> {
                val category = payload?.optString("category").orEmpty()
                    .ifBlank { inlineRecommendationCategory.orEmpty() }
                showInlineRecommendations(category, payload?.optString("sort").orEmpty())
            }

            "select_destination" -> {
                val name = payload?.optString("name").orEmpty().trim()
                if (name.isNotBlank()) {
                    clearInlineRecommendations()
                    vm.ensureTravelDestinationContext(name)
                    currentTab = ZhituShellTab.Home
                }
            }

            "generate_plan" -> {
                clearInlineRecommendations()
                vm.generateTravelPlan()
                currentTab = ZhituShellTab.Itinerary
            }

            "open_map" -> {
                openNativeMap.value(payload)
            }

            "open_recommendation" -> {
                val category = payload?.optString("category").orEmpty()
                val fallbackCategory = inlineRecommendationCategory ?: ZhituShellTab.Activity.value
                showInlineRecommendations(category.ifBlank { fallbackCategory }, payload?.optString("sort").orEmpty())
            }

            "open_favorite_item" -> {
                val conversationId = payload?.optString("conversationId").orEmpty()
                val nodeIdValue = payload?.optString("nodeId").orEmpty()
                val category = payload?.optString("category").orEmpty()
                when {
                    conversationId.isNotBlank() && nodeIdValue.isNotBlank() -> {
                        nav.navigate(Screen.Chat(conversationId, nodeId = nodeIdValue)) { launchSingleTop = true }
                    }

                    category == "hotel" || category == "food" || category == "activity" -> {
                        showInlineRecommendations(category)
                    }
                    else -> nav.navigate(Screen.Favorite) { launchSingleTop = true }
                }
            }

            "open_itinerary" -> {
                clearInlineRecommendations()
                currentTab = ZhituShellTab.Itinerary
            }

            "update_itinerary_item" -> {
                clearInlineRecommendations()
                val dayIndex = payload?.optInt("dayIndex", Int.MIN_VALUE) ?: Int.MIN_VALUE
                val itemId = payload?.optString("itemId").orEmpty()
                vm.updateItineraryItem(
                    dayIndex = dayIndex,
                    itemId = itemId,
                    timeSlot = payload?.optString("timeSlot").orEmpty(),
                    title = payload?.optString("title").orEmpty(),
                    description = payload?.optString("description").orEmpty(),
                    transportHint = payload?.optString("transportHint").orEmpty(),
                    estimatedCost = payload?.optString("estimatedCost").orEmpty(),
                )
                currentTab = ZhituShellTab.Itinerary
            }

            "resume_history_session" -> {
                clearInlineRecommendations()
                val conversationId = payload?.optString("conversationId").orEmpty()
                if (conversationId.isNotBlank()) {
                    nav.navigate(Screen.TravelHub(conversationId, startTab = "ai")) { launchSingleTop = true }
                }
            }

            "view_trip" -> {
                clearInlineRecommendations()
                val conversationId = payload?.optString("conversationId").orEmpty()
                if (conversationId.isNotBlank()) {
                    nav.navigate(Screen.TravelHub(conversationId, startTab = "itinerary")) { launchSingleTop = true }
                } else {
                    currentTab = ZhituShellTab.Itinerary
                }
            }

            "replan_trip" -> {
                val conversationId = payload?.optString("conversationId").orEmpty()
                if (conversationId.isNotBlank() && conversationId != id.toString()) {
                    clearInlineRecommendations()
                    nav.navigate(Screen.TravelHub(conversationId, startTab = "ai")) { launchSingleTop = true }
                } else {
                    clearInlineRecommendations()
                    currentTab = ZhituShellTab.Ai
                    vm.retryTravelPlanGeneration()
                }
            }

            "export_itinerary" -> {
                exportTripMarkdown.value.invoke()
            }

            "share_itinerary" -> {
                shareTripMarkdown.value.invoke()
            }

            "open_settings_or_more" -> {
                nav.navigate(Screen.Setting) { launchSingleTop = true }
            }

            "open_history" -> {
                nav.navigate(Screen.History) { launchSingleTop = true }
            }

            "open_detail_page" -> {
                when (payload?.optString("page").orEmpty()) {
                    "favorites" -> nav.navigate(Screen.Favorite) { launchSingleTop = true }
                    "history" -> nav.navigate(Screen.History) { launchSingleTop = true }
                    else -> Toast.makeText(context, "褰撳墠椤靛凡淇濈暀瑙嗚灞傦紝瀹屾暣鑳藉姏绋嶅悗琛ラ綈", Toast.LENGTH_SHORT).show()
                }
            }

            "open_advanced_chat_controls" -> {
                currentTab = ZhituShellTab.Ai
                Toast.makeText(context, "宸插垏鎹㈠埌 AI 瀵硅瘽闈㈡澘", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val bridge = remember {
        ZhituAndroidBridge(mainHandler) { action, payload ->
            latestActionHandler.value(action, payload)
        }
    }

    val assistantRecommendation = remember(
        conversation,
        ui,
        inlineRecommendationCategory,
        inlineRecommendationSort,
    ) {
        buildAssistantRecommendationState(
            conversation = conversation,
            uiState = ui,
            category = inlineRecommendationCategory,
            sort = inlineRecommendationSort,
        )
    }

    val shellStateJson = remember(
        conversation,
        ui,
        currentTab,
        assistantRecommendation,
        weatherSummary,
        visibleMapPois,
        historyConversationCount,
        shellHistoryConversations,
        shellHistoryTrips,
        shellFavoriteItems,
        currentTripSummary,
        profileTab,
    ) {
        zhituJson.encodeToString(
            ZhituShellState(
                currentTab = currentTab.value,
                conversation = conversation.toZhituConversationState(assistantRecommendation),
                assistantRecommendation = assistantRecommendation,
                travelPlan = conversation.travelPlan?.toZhituTravelPlanState(visibleMapPois),
                travelUiState = ui.toZhituTravelUiStateState(weatherSummary),
                user = conversation.toZhituUserState(historyConversationCount),
                historyConversations = shellHistoryConversations,
                historyTrips = shellHistoryTrips,
                favoriteItems = shellFavoriteItems,
                currentTripSummary = currentTripSummary,
                profileUiState = ZhituProfileUiState(activeTab = profileTab),
                availableActions = ZhituAvailableActionsState(exportConversation = conversation.currentMessages.isNotEmpty()),
            )
        )
    }

    val webViewState = rememberWebViewState(
        url = ZHITU_ASSET_URL,
        interfaces = mapOf("RikkaZhituBridge" to bridge),
        settings = {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            blockNetworkLoads = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
        }
    )

    LaunchedEffect(webViewState.webView, webViewState.isLoading) {
        val webView = webViewState.webView ?: return@LaunchedEffect
        if (webViewState.isLoading) return@LaunchedEffect
        webView.evaluateJavascript(ZHITU_PROJECT_PATCH_SCRIPT, null)
        if (ZHITU_ENABLE_RUNTIME_PROBES) {
            webView.evaluateJavascript(
                """
                (function () {
                    var chat = document.getElementById('chat-container');
                    var send = document.getElementById('send-btn');
                    return JSON.stringify({
                        readyState: document.readyState,
                        hasReceiveState: typeof window.__ZHITU_RECEIVE_STATE__,
                        hasChatContainer: !!chat,
                        hasSendButton: !!send,
                        bodyText: ((document.body && document.body.innerText) || '').slice(0, 200)
                    });
                })();
                """.trimIndent()
            ) { result ->
                Log.d(ZHITU_DEBUG_TAG, "page-ready=$result")
            }
        }
    }

    LaunchedEffect(shellStateJson, webViewState.webView, webViewState.isLoading) {
        val webView = webViewState.webView ?: return@LaunchedEffect
        if (webViewState.isLoading) return@LaunchedEffect
        val quotedState = JSONObject.quote(shellStateJson)
        webView.evaluateJavascript(
            """
            (function pushZhituState(retryCount) {
                window.__ZHITU_EMBEDDED__ = true;
                if (typeof window.__ZHITU_RECEIVE_STATE__ === 'function') {
                    window.__ZHITU_RECEIVE_STATE__($quotedState);
                    window.__ZHITU_APPLY_PROJECT_PATCHES__ && window.__ZHITU_APPLY_PROJECT_PATCHES__();
                    return;
                }
                if ((retryCount || 0) >= 20) {
                    return;
                }
                setTimeout(function () {
                    pushZhituState((retryCount || 0) + 1);
                }, 80);
            })(0);
            """.trimIndent(),
            null,
        )
        if (ZHITU_ENABLE_RUNTIME_PROBES) {
            mainHandler.postDelayed({
                webView.evaluateJavascript(
                    """
                    (function () {
                        var chat = document.getElementById('chat-container');
                        return JSON.stringify({
                            readyState: document.readyState,
                            hasReceiveState: typeof window.__ZHITU_RECEIVE_STATE__,
                            hasState: !!window.__ZHITU_STATE__,
                            currentTab: window.__ZHITU_STATE__ && window.__ZHITU_STATE__.currentTab || null,
                            chatLength: chat && chat.innerHTML ? chat.innerHTML.length : -1,
                            chatPreview: chat && chat.innerText ? chat.innerText.slice(0, 200) : '',
                            bodyPreview: document.body && document.body.innerText ? document.body.innerText.slice(0, 200) : ''
                        });
                    })();
                    """.trimIndent()
                ) { result ->
                    Log.d(ZHITU_DEBUG_TAG, "post-state=$result")
                }
            }, 1200)
        }
    }

    Box(Modifier.fillMaxSize()) {
        WebView(
            state = webViewState,
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
        )

        if (nav.canPop) {
            BackButton(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 12.dp, top = 12.dp)
            )
        }
    }
}

private fun normalizeRecommendationCategory(value: String?): String? = when (value?.lowercase()) {
    "hotel", "hotels" -> "hotel"
    "food", "foods" -> "food"
    "activity", "activities" -> "activity"
    else -> null
}

private fun normalizeRecommendationSort(value: String?): String = when (value?.lowercase()) {
    "price", "rating", "distance" -> value.lowercase()
    else -> "recommended"
}

private fun buildAssistantRecommendationState(
    conversation: Conversation,
    uiState: TravelHubUiState,
    category: String?,
    sort: String,
): ZhituAssistantRecommendationState? {
    val normalizedCategory = normalizeRecommendationCategory(category) ?: return null
    val normalizedSort = normalizeRecommendationSort(sort)
    val plan = conversation.travelPlan
    val destination = plan?.brief?.destination
        ?.ifBlank { uiState.selectedDestination?.name.orEmpty() }
        ?: uiState.selectedDestination?.name.orEmpty()

    val baseItems = when (normalizedCategory) {
        "hotel" -> plan?.hotels.orEmpty().ifEmpty { uiState.hotels }
        "food" -> plan?.foods.orEmpty().ifEmpty { uiState.foods }
        else -> plan?.activities.orEmpty()
            .ifEmpty { uiState.activities }
            .ifEmpty { plan?.pois.orEmpty().toActivityFallbackRecommendations() }
    }
    val origin = uiState.selectedDestination?.lat?.let { lat ->
        uiState.selectedDestination.lon?.let { lon -> lat to lon }
    }

    val mappedItems = baseItems.map { item ->
        val distanceText = origin?.let { calculateDistanceText(it.first, it.second, item.lat, item.lon) }.orEmpty()
        item.toZhituRecommendationState().copy(distanceText = distanceText)
    }
    val sortedItems = sortRecommendationItems(mappedItems, normalizedSort, origin)

    val categoryLabel = when (normalizedCategory) {
        "hotel" -> "住宿"
        "food" -> "餐饮"
        else -> "活动"
    }
    val title = buildString {
        if (destination.isNotBlank()) {
            append(destination).append(" ").append(categoryLabel).append("推荐")
        } else {
            append("当前行程").append(categoryLabel).append("推荐")
        }
    }
    val message = if (sortedItems.isNotEmpty()) {
        buildString {
            if (destination.isNotBlank()) {
                append("以下是 ").append(destination).append(" 的")
            } else {
                append("以下是当前行程的")
            }
            append(categoryLabel)
            append("推荐，共 ")
            append(sortedItems.size)
            append(" 条。")
        }
    } else {
        "当前行程暂时没有可用的${categoryLabel}推荐。"
    }

    return ZhituAssistantRecommendationState(
        category = normalizedCategory,
        title = title,
        message = message,
        activeSort = normalizedSort,
        sortOptions = buildRecommendationSortOptions(normalizedCategory, normalizedSort),
        items = sortedItems,
        emptyTitle = "暂无${categoryLabel}推荐",
        emptyDescription = "当前行程尚未产出可用的${categoryLabel}数据，请补充偏好或重新规划后重试。",
    )
}
private fun buildRecommendationSortOptions(
    category: String,
    activeSort: String,
): List<ZhituRecommendationSortOptionState> {
    val labels = when (category) {
        "hotel" -> listOf(
            "recommended" to "综合推荐",
            "price" to "价格",
            "rating" to "评分",
            "distance" to "距离",
        )

        "food" -> listOf(
            "recommended" to "热门优先",
            "rating" to "评分",
            "distance" to "距离",
            "price" to "价格",
        )

        else -> listOf(
            "recommended" to "热门优先",
            "distance" to "距离",
            "price" to "价格",
            "rating" to "评分",
        )
    }
    return labels.map { (key, label) ->
        ZhituRecommendationSortOptionState(
            key = key,
            label = label,
            active = key == activeSort,
        )
    }
}

private fun sortRecommendationItems(
    items: List<ZhituRecommendationState>,
    sort: String,
    origin: Pair<Double, Double>?,
): List<ZhituRecommendationState> = when (sort) {
    "price" -> items.sortedBy { extractFirstNumber(it.priceHint) ?: Double.MAX_VALUE }
    "rating" -> items.sortedByDescending { extractFirstNumber(it.ratingText) ?: Double.MIN_VALUE }
    "distance" -> if (origin != null) {
        items.sortedBy {
            calculateDistanceMeters(origin.first, origin.second, it.lat, it.lon) ?: Double.MAX_VALUE
        }
    } else {
        items
    }

    else -> items
}

private fun List<TravelPoi>.toActivityFallbackRecommendations(): List<TravelRecommendationItem> {
    return take(6).map { poi ->
        TravelRecommendationItem(
            id = poi.linkedRecommendationId ?: poi.id,
            category = TravelRecommendationCategory.activity,
            title = poi.name,
            subtitle = poi.address,
            tags = listOfNotNull(
                poi.category.takeIf { it.isNotBlank() },
                "行程回填",
            ),
            reason = "该点位已关联到当前行程，可作为活动推荐的回填结果。",
            area = poi.address,
            lat = poi.lat,
            lon = poi.lon,
        )
    }
}

private fun calculateDistanceText(
    originLat: Double,
    originLon: Double,
    targetLat: Double?,
    targetLon: Double?,
): String {
    val meters = calculateDistanceMeters(originLat, originLon, targetLat, targetLon) ?: return ""
    return if (meters < 1000) {
        "${meters.roundToInt()}m"
    } else {
        String.format("%.1fkm", meters / 1000.0)
    }
}

private fun calculateDistanceMeters(
    originLat: Double,
    originLon: Double,
    targetLat: Double?,
    targetLon: Double?,
): Double? {
    val lat = targetLat ?: return null
    val lon = targetLon ?: return null
    val earthRadiusMeters = 6_371_000.0
    val latDistance = Math.toRadians(lat - originLat)
    val lonDistance = Math.toRadians(lon - originLon)
    val a = sin(latDistance / 2) * sin(latDistance / 2) +
        cos(Math.toRadians(originLat)) * cos(Math.toRadians(lat)) *
        sin(lonDistance / 2) * sin(lonDistance / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return earthRadiusMeters * c
}

private fun extractFirstNumber(value: String): Double? {
    val match = Regex("""(\d+(?:\.\d+)?)""").find(value.replace(",", ""))
    return match?.groupValues?.getOrNull(1)?.toDoubleOrNull()
}

private fun Conversation.toZhituConversationState(
    assistantRecommendation: ZhituAssistantRecommendationState? = null,
): ZhituConversationState {
    val isStreamingAssistantMessage = if (assistantId == TRAVEL_PLANNER_ASSISTANT_ID) {
        travelPlanningState.name == "ExtractingBrief" || travelPlanningState.name == "GeneratingPlan"
    } else {
        currentMessages.lastOrNull()?.role == MessageRole.ASSISTANT &&
            currentMessages.lastOrNull()?.finishedAt == null
    }
    val visibleMessages = if (assistantId == TRAVEL_PLANNER_ASSISTANT_ID) {
        currentMessages.filter { it.role == MessageRole.USER }
    } else if (isStreamingAssistantMessage) {
        currentMessages.filterNot { message ->
            message.role == MessageRole.ASSISTANT && message.finishedAt == null
        }
    } else {
        currentMessages
    }
    val mappedMessages = visibleMessages.map { message ->
        ZhituMessageState(
            id = message.id.toString(),
            role = message.role.name,
            text = sanitizeTravelDisplayText(message.toText()),
            createdAt = message.createdAt.toString(),
        )
    }
    val plannerSummaryMessage = if (assistantId == TRAVEL_PLANNER_ASSISTANT_ID) {
        buildTravelPlannerSummaryMessage()
    } else {
        null
    }
    val syntheticMessage = assistantRecommendation?.let {
        ZhituMessageState(
            id = "assistant-recommendation-${it.category}",
            role = MessageRole.ASSISTANT.name,
            text = sanitizeTravelDisplayText(it.message),
            createdAt = updateAt.toString(),
        )
    }

    return ZhituConversationState(
        id = id.toString(),
        title = title.ifBlank { travelPlan?.brief?.destination?.ifBlank { "新的行程对话" } ?: "新的行程对话" },
        isGenerating = isStreamingAssistantMessage,
        suggestions = chatSuggestions,
        messages = listOfNotNull(*(mappedMessages.toTypedArray()), plannerSummaryMessage, syntheticMessage),
    )
}

private fun Conversation.buildTravelPlannerSummaryMessage(): ZhituMessageState? {
    val plan = travelPlan
    val brief = plan?.brief
    val destination = brief?.destination?.ifBlank { title }?.ifBlank { "当前行程" } ?: title.ifBlank { "当前行程" }
    val dayCount = brief?.days ?: plan?.itineraryDays?.size ?: 0
    val budget = sanitizeTravelDisplayText(brief?.budgetText.orEmpty())
    val travelers = brief?.travelerCount
    val summary = when (travelPlanningState.name) {
        "ExtractingBrief" -> "正在提取你的旅行需求，并校验目的地、天数、预算和偏好。"
        "GeneratingPlan" -> buildString {
            append("正在按")
            if (dayCount > 0) {
                append("${dayCount}天")
            } else {
                append("你的要求")
            }
            append("生成${destination}行程，并补全住宿、餐饮和活动推荐。")
        }
        "DraftBrief" -> buildString {
            append("我已记录当前需求。")
            if (destination.isNotBlank()) append("目的地：${destination}。")
            if (dayCount > 0) append("计划天数：${dayCount}天。")
            append("如果你继续补充预算、人数或偏好，行程会更准确。")
        }
        "Generated" -> buildGeneratedTravelPlannerSummary(destination, dayCount, travelers, budget, plan)
        "Failed" -> "行程生成失败，请重新描述目的地、天数和预算，我会重新规划。"
        else -> null
    } ?: return null
    return ZhituMessageState(
        id = "travel-planner-summary-${id}-$generationVersionSafe",
        role = MessageRole.ASSISTANT.name,
        text = summary,
        createdAt = updateAt.toString(),
    )
}

private fun buildGeneratedTravelPlannerSummary(
    destination: String,
    dayCount: Int,
    travelers: Int?,
    budget: String,
    plan: TravelPlan?,
): String {
    return buildString {
        append("已为你生成")
        if (dayCount > 0) append("${dayCount}天")
        append(destination)
        append("行程。")
        travelers?.let { append("${it}人出行。") }
        if (budget.isNotBlank()) append("${budget}。")

        val highlights = plan?.itineraryDays
            .orEmpty()
            .take(3)
            .mapNotNull { day ->
                val items = day.items
                    .mapNotNull { item ->
                        sanitizeTravelDisplayText(item.title)
                            .takeIf { it.isNotBlank() }
                    }
                    .take(3)
                if (items.isEmpty()) {
                    null
                } else {
                    "第${day.dayIndex}天：${items.joinToString(" → ")}"
                }
            }

        if (highlights.isNotEmpty()) {
            append("\n")
            highlights.forEach { line ->
                append("- ")
                append(line)
                append("\n")
            }
        }

        val hotelCount = plan?.hotels?.size ?: 0
        val foodCount = plan?.foods?.size ?: 0
        val activityCount = plan?.activities?.size ?: 0
        if (hotelCount > 0 || foodCount > 0 || activityCount > 0) {
            append("当前已同步")
            val parts = listOfNotNull(
                hotelCount.takeIf { it > 0 }?.let { "${it}条住宿推荐" },
                foodCount.takeIf { it > 0 }?.let { "${it}条餐饮推荐" },
                activityCount.takeIf { it > 0 }?.let { "${it}条活动推荐" },
            )
            append(parts.joinToString("、"))
            append("。")
        }

        append("你可以继续让我调整某一天节奏、预算分配、住宿区域或餐饮偏好。")
    }.trim()
}

private val Conversation.generationVersionSafe: Int
    get() = travelPlan?.generationVersion ?: 0

private fun sanitizeTravelDisplayText(text: String): String {
    return text
        .replace(Regex("""(?i)\[(user|assistant|system)\]\s*:?\s*"""), "")
        .replace(Regex("""(?im)^(user|assistant|system)\s*:?\s*"""), "")
        .replace(Regex("""(?is)<think>.*?</think>"""), " ")
        .replace(Regex("""(?im)^thinking\s*:?\s*"""), "")
        .replace("```json", "")
        .replace("```JSON", "")
        .replace("```", "")
        .stripMarkdown()
        .replace(Regex("""\\([`*_#>\-\[\]\(\)])"""), "$1")
        .replace(Regex("""[*#>`~_]+"""), " ")
        .replace(Regex("""<[^>]+>"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

private fun TravelHubUiState.toZhituTravelUiStateState(weatherSummary: String): ZhituTravelUiStateState {
    return ZhituTravelUiStateState(
        searchQuery = searchQuery,
        weatherSummary = weatherSummary,
        selectedMapFilter = selectedMapFilter,
        selectedDestination = selectedDestination?.toZhituSuggestionState(),
        suggestions = suggestions.map(TravelSearchSuggestion::toZhituSuggestionState),
    )
}

private fun TravelSearchSuggestion.toZhituSuggestionState(): ZhituSuggestionState {
    return ZhituSuggestionState(
        id = id,
        name = name,
        subtitle = listOf(district, address).filter { it.isNotBlank() }.joinToString(" "),
        lat = lat,
        lon = lon,
    )
}

private fun TravelPlan.toZhituTravelPlanState(visibleMapPois: List<TravelPoi>): ZhituTravelPlanState {
    return ZhituTravelPlanState(
        brief = brief?.let {
            ZhituTravelBriefState(
                destination = sanitizeTravelDisplayText(it.destination),
                origin = sanitizeTravelDisplayText(it.origin),
                dateRange = sanitizeTravelDisplayText(it.dateRange),
                days = it.days,
                travelerCount = it.travelerCount,
                budgetText = sanitizeTravelDisplayText(it.budgetText),
                budgetLevel = sanitizeTravelDisplayText(it.budgetLevel),
                travelStyleTags = it.travelStyleTags.map(::sanitizeTravelDisplayText),
                transportPreferences = it.transportPreferences.map(::sanitizeTravelDisplayText),
                userIntentSummary = sanitizeTravelDisplayText(it.userIntentSummary),
            )
        },
        hotels = hotels.map(TravelRecommendationItem::toZhituRecommendationState),
        foods = foods.map(TravelRecommendationItem::toZhituRecommendationState),
        activities = activities.map(TravelRecommendationItem::toZhituRecommendationState),
        pois = visibleMapPois.map(TravelPoi::toZhituPoiState),
        itineraryDays = itineraryDays.map(TravelItineraryDay::toZhituItineraryDayState),
        status = status.name,
    )
}

private fun TravelRecommendationItem.toZhituRecommendationState(): ZhituRecommendationState {
    return ZhituRecommendationState(
        id = id,
        title = sanitizeTravelDisplayText(title),
        subtitle = sanitizeTravelDisplayText(subtitle),
        tags = tags.map(::sanitizeTravelDisplayText),
        reason = sanitizeTravelDisplayText(reason),
        priceHint = sanitizeTravelDisplayText(priceHint),
        ratingText = sanitizeTravelDisplayText(ratingText),
        area = sanitizeTravelDisplayText(area),
        inventoryHint = sanitizeTravelDisplayText(inventoryHint),
        bookingUrl = bookingUrl,
        lat = lat,
        lon = lon,
        distanceText = "",
    )
}

private fun TravelPoi.toZhituPoiState(): ZhituPoiState {
    return ZhituPoiState(
        id = id,
        name = sanitizeTravelDisplayText(name),
        category = sanitizeTravelDisplayText(category),
        address = sanitizeTravelDisplayText(address),
        lat = lat,
        lon = lon,
    )
}

private fun TravelItineraryDay.toZhituItineraryDayState(): ZhituItineraryDayState {
    return ZhituItineraryDayState(
        dayIndex = dayIndex,
        title = sanitizeTravelDisplayText(title),
        dateText = sanitizeTravelDisplayText(dateText),
        weatherHint = sanitizeTravelDisplayText(weatherHint),
        items = items.map(TravelItineraryItem::toZhituItineraryItemState),
    )
}

private fun TravelItineraryItem.toZhituItineraryItemState(): ZhituItineraryItemState {
    return ZhituItineraryItemState(
        id = id,
        timeSlot = timeSlot,
        title = sanitizeTravelDisplayText(title),
        description = sanitizeTravelDisplayText(description),
        category = category.name,
        estimatedCost = sanitizeTravelDisplayText(estimatedCost),
        transportHint = sanitizeTravelDisplayText(transportHint),
    )
}

private fun Conversation.toZhituHistoryConversationState(): ZhituHistoryConversationState {
    val preview = currentMessages.lastOrNull()?.toText().orEmpty().trim()
    return ZhituHistoryConversationState(
        id = id.toString(),
        title = title.ifBlank { travelPlan?.brief?.destination?.ifBlank { "历史会话" } ?: "历史会话" },
        subtitle = travelPlan.toTravelSummaryText(),
        preview = preview,
        updatedAt = updateAt.toString(),
    )
}

private fun Conversation.toZhituHistoryTripState(): ZhituHistoryTripState? {
    val plan = travelPlan ?: return null
    return ZhituHistoryTripState(
        id = "${id}-trip",
        conversationId = id.toString(),
        title = title.ifBlank { plan.brief?.destination?.ifBlank { "历史行程" } ?: "历史行程" },
        destination = plan.brief?.destination.orEmpty(),
        dateRange = plan.brief?.dateRange.orEmpty(),
        days = plan.brief?.days ?: plan.itineraryDays.size,
        summary = plan.toTravelSummaryText(),
        updatedAt = updateAt.toString(),
        status = travelPlanningState.name,
    )
}

private fun FavoriteEntity.toZhituFavoriteItemState(): ZhituFavoriteItemState? {
    val ref = NodeFavoriteAdapter.decodeRef(this) ?: return null
    val meta = NodeFavoriteAdapter.decodeMeta(this)
    return ZhituFavoriteItemState(
        id = id,
        title = meta?.title?.ifBlank { "收藏消息" } ?: "收藏消息",
        subtitle = meta?.previewText.orEmpty(),
        category = "favorite",
        conversationId = ref.conversationId.toString(),
        nodeId = ref.nodeId.toString(),
    )
}

private fun TravelPlan?.toFallbackFavoriteItems(): List<ZhituFavoriteItemState> {
    val plan = this ?: return emptyList()

    fun items(category: String, list: List<TravelRecommendationItem>): List<ZhituFavoriteItemState> {
        return list.take(2).map { item ->
            ZhituFavoriteItemState(
                id = "$category-${item.id}",
                title = item.title,
                subtitle = item.subtitle.ifBlank { item.area.ifBlank { item.priceHint.ifBlank { item.ratingText } } },
                category = category,
                reason = item.reason,
                conversationId = plan.conversationId,
            )
        }
    }

    return buildList {
        addAll(items("hotel", plan.hotels))
        addAll(items("food", plan.foods))
        addAll(items("activity", plan.activities))
    }
}

private fun Conversation.toZhituCurrentTripSummaryState(): ZhituCurrentTripSummaryState? {
    val plan = travelPlan ?: return null
    return ZhituCurrentTripSummaryState(
        conversationId = id.toString(),
        title = title.ifBlank { plan.brief?.destination?.ifBlank { "当前行程" } ?: "当前行程" },
        destination = plan.brief?.destination.orEmpty(),
        summary = plan.toTravelSummaryText(),
        days = plan.brief?.days ?: plan.itineraryDays.size,
        dateRange = plan.brief?.dateRange.orEmpty(),
        status = travelPlanningState.name,
    )
}

private fun TravelPlan?.toTravelSummaryText(): String {
    val plan = this ?: return ""
    val brief = plan.brief
    val parts = buildList {
        brief?.destination?.takeIf { it.isNotBlank() }?.let(::add)
        brief?.dateRange?.takeIf { it.isNotBlank() }?.let(::add)
        val dayCount = brief?.days ?: plan.itineraryDays.size
        if (dayCount > 0) add("${dayCount}天")
    }
    return if (parts.isNotEmpty()) parts.joinToString(" / ") else "行程尚未完成"
}

private fun exportTripMarkdownFile(
    context: android.content.Context,
    conversation: Conversation,
): File {
    val exportsDir = File(context.cacheDir, "zhitu-exports").apply { mkdirs() }
    val safeName = (conversation.title.ifBlank { conversation.travelPlan?.brief?.destination ?: "zhitu-travel-plan" })
        .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "-")
        .trim('-')
        .ifBlank { "zhitu-travel-plan" }
    val file = File(exportsDir, "$safeName.md")
    file.writeText(buildTripMarkdown(conversation))
    return file
}

private fun shareTripMarkdownFile(
    context: android.content.Context,
    conversation: Conversation,
) {
    val file = exportTripMarkdownFile(context, conversation)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/markdown"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, buildTripMarkdown(conversation))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "分享行程"))
}

private fun buildTripMarkdown(conversation: Conversation): String {
    val plan = conversation.travelPlan
    val brief = plan?.brief
    return buildString {
        appendLine("# ${conversation.title.ifBlank { brief?.destination ?: "旅行行程" }}")
        appendLine()
        if (brief != null) {
            appendLine("## 行程摘要")
            brief.destination.takeIf { it.isNotBlank() }?.let { appendLine("- 目的地：$it") }
            brief.origin.takeIf { it.isNotBlank() }?.let { appendLine("- 出发地：$it") }
            brief.dateRange.takeIf { it.isNotBlank() }?.let { appendLine("- 日期：$it") }
            brief.days?.let { appendLine("- 天数：$it") }
            brief.budgetText.takeIf { it.isNotBlank() }?.let { appendLine("- 预算：$it") }
            brief.userIntentSummary.takeIf { it.isNotBlank() }?.let { appendLine("- 备注：$it") }
            appendLine()
        }

        plan?.itineraryDays?.takeIf { it.isNotEmpty() }?.let { days ->
            appendLine("## 每日行程")
            days.forEach { day ->
                appendLine("### 第${day.dayIndex}天：${day.title}")
                day.dateText.takeIf { it.isNotBlank() }?.let { appendLine("- 日期：$it") }
                day.weatherHint.takeIf { it.isNotBlank() }?.let { appendLine("- 天气：$it") }
                day.items.forEach { item ->
                    appendLine("- ${item.timeSlot.ifBlank { "待定" }} - ${item.title}")
                    item.description.takeIf { it.isNotBlank() }?.let { appendLine("  - 说明：$it") }
                    item.transportHint.takeIf { it.isNotBlank() }?.let { appendLine("  - 交通：$it") }
                    item.estimatedCost.takeIf { it.isNotBlank() }?.let { appendLine("  - 费用：$it") }
                }
                appendLine()
            }
        }

        fun appendRecommendationBlock(title: String, items: List<TravelRecommendationItem>) {
            if (items.isEmpty()) return
            appendLine("## $title")
            items.forEach { item ->
                appendLine("- ${item.title}")
                item.subtitle.takeIf { it.isNotBlank() }?.let { appendLine("  - Location: $it") }
                item.reason.takeIf { it.isNotBlank() }?.let { appendLine("  - Reason: $it") }
                item.priceHint.takeIf { it.isNotBlank() }?.let { appendLine("  - 价格：$it") }
                item.ratingText.takeIf { it.isNotBlank() }?.let { appendLine("  - 评分：$it") }
            }
            appendLine()
        }

        appendRecommendationBlock("住宿推荐", plan?.hotels.orEmpty())
        appendRecommendationBlock("餐饮推荐", plan?.foods.orEmpty())
        appendRecommendationBlock("活动推荐", plan?.activities.orEmpty())
    }
}

private fun Conversation.toZhituUserState(historyConversationCount: Int): ZhituUserState {
    val destination = travelPlan?.brief?.destination?.ifBlank { "行程进行中" } ?: "行程进行中"
    return ZhituUserState(
        name = "旅行者",
        subtitle = destination,
        stats = listOf(
            ZhituStatState("历史行程", "$historyConversationCount"),
            ZhituStatState("收藏项目", "${travelPlan?.hotels.orEmpty().size + travelPlan?.foods.orEmpty().size + travelPlan?.activities.orEmpty().size}"),
            ZhituStatState("地图点位", "${travelPlan?.pois.orEmpty().size}"),
        ),
    )
}

