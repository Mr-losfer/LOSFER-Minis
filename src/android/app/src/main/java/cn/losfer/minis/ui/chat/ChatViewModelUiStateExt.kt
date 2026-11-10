package cn.losfer.minis.ui.chat

// [T-android-split-chat] Small UI-state toggle methods extracted from
// ChatViewModel as extension functions (verbatim): tool-detail sheet,
// browser sheet, memory sheet, attachment list. The 4 backing state fields
// were flipped private->internal. No logic change.

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.lazy.LazyListState
import cn.losfer.minis.agent.Level
import cn.losfer.minis.agent.ToolLoopDetector
import cn.losfer.minis.browser.BrowserActionInput
import cn.losfer.minis.browser.BrowserTabPool
import cn.losfer.minis.data.db.MessageEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Extension
import cn.losfer.minis.data.BPETokenizer
import cn.losfer.minis.data.ContextOffload
import cn.losfer.minis.data.ContextPolicy
import cn.losfer.minis.logging.AppLogger
import cn.losfer.minis.data.FileMentionIndex
import cn.losfer.minis.data.db.CompactMarkerEntity
import cn.losfer.minis.data.model.AgentContentPart
import cn.losfer.minis.data.model.AgentToolDefinition
import cn.losfer.minis.data.model.LLMMessage
import cn.losfer.minis.data.model.LLMModel
import cn.losfer.minis.data.model.LLMStreamChunk
import cn.losfer.minis.data.model.LLMUsage
import cn.losfer.minis.data.model.ModelGroup
import cn.losfer.minis.data.model.ThinkingLevel
import cn.losfer.minis.R
import cn.losfer.minis.data.repository.ChatRepository
import cn.losfer.minis.data.repository.MemoryRepository
import cn.losfer.minis.data.repository.ProviderRepository
import cn.losfer.minis.provider.ImageBudget
import cn.losfer.minis.provider.LLMProvider
import cn.losfer.minis.provider.ProviderFactory
import cn.losfer.minis.sandbox.ExecutionCoordinator
import cn.losfer.minis.terminal.MinisOpenUrlBroker
import cn.losfer.minis.terminal.MinisUrlMarker
import cn.losfer.minis.tools.AgentTools
import cn.losfer.minis.tools.FileEditTool
import cn.losfer.minis.tools.FileReadTool
import cn.losfer.minis.tools.FileWriteTool
import cn.losfer.minis.tools.MemoryTools
import cn.losfer.minis.tools.ReadImageTool
import cn.losfer.minis.tools.ToolExecutionResult
import cn.losfer.minis.offload.OffloadPermissionManager
import cn.losfer.minis.service.SessionActivityTracker
import cn.losfer.minis.service.SessionConcurrencyManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal fun ChatViewModel.openToolDetail(toolBlockId: String) {
    _selectedToolDetailId.value = toolBlockId
}

internal fun ChatViewModel.closeToolDetail() {
    _selectedToolDetailId.value = null
}

internal fun ChatViewModel.toggleBrowserSheet() {
    val opening = !_showBrowserSheet.value
    if (opening) browserTabPool.ensureTabForUI()
    _showBrowserSheet.value = opening
}

internal fun ChatViewModel.dismissBrowserSheet() {
    _showBrowserSheet.value = false
}

/**
 * Open the session browser sheet, focused on the tab whose URL matches
 * [url]. If no pool tab currently has that URL, a new tab is created and
 * loaded. Used by the tool-call preview's globe button so the agent's
 * existing browser_use page is reused when available instead of spawning
 * a duplicate tab.
 */
internal fun ChatViewModel.openBrowserSheetForUrl(url: String) {
    if (url.isBlank()) {
        browserTabPool.ensureTabForUI()
    } else {
        browserTabPool.selectOrCreateTabForURL(url)
    }
    _showBrowserSheet.value = true
}

internal fun ChatViewModel.toggleMemorySheet() {
    _showMemorySheet.value = !_showMemorySheet.value
}

internal fun ChatViewModel.dismissMemorySheet() {
    _showMemorySheet.value = false
}

internal fun ChatViewModel.addAttachment(attachment: InputAttachment) {
    _attachments.value = _attachments.value + attachment
}

internal fun ChatViewModel.removeAttachment(id: String) {
    _attachments.value = _attachments.value.filter { it.id != id }
}

internal fun ChatViewModel.clearAttachments() {
    _attachments.value = emptyList()
}
