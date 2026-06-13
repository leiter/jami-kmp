package net.jami.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import jami_kmp.shared.generated.resources.Res
import jami_kmp.shared.generated.resources.accept
import jami_kmp.shared.generated.resources.block
import jami_kmp.shared.generated.resources.decline
import jami_kmp.shared.generated.resources.invitation_card_title
import net.jami.di.getViewModel
import net.jami.ui.components.content.AvatarSize
import net.jami.ui.components.content.JamiAvatar
import net.jami.ui.theme.JamiTheme
import net.jami.ui.viewmodel.PendingRequestItem
import net.jami.ui.viewmodel.PendingRequestsViewModel
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingRequestsScreen(
    onBack: () -> Unit,
    onConversationOpened: (String) -> Unit = {},
) {
    val viewModel = getViewModel<PendingRequestsViewModel>()
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.invitation_card_title),
                        style = JamiTheme.typography.titleMedium,
                        color = JamiTheme.colors.onSurface,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = JamiTheme.colors.onSurface,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = JamiTheme.colors.surface,
                ),
            )
        }
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            state.requests.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No pending invitations",
                    style = JamiTheme.typography.bodyLarge,
                    color = JamiTheme.colors.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                items(state.requests, key = { it.ringId }) { item ->
                    PendingRequestRow(
                        item = item,
                        onAccept = {
                            viewModel.accept(item)
                            onConversationOpened(item.conversationUri.uri)
                        },
                        onRefuse = { viewModel.discard(item) },
                        onBlock = { viewModel.block(item) },
                    )
                    HorizontalDivider(color = JamiTheme.colors.outline.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
internal fun PendingRequestRow(
    item: PendingRequestItem,
    onAccept: () -> Unit,
    onRefuse: () -> Unit,
    onBlock: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = JamiTheme.spacing.l, vertical = JamiTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JamiAvatar(
            displayName = item.displayName,
            avatarBytes = item.avatarBytes,
            size = AvatarSize.Medium,
        )
        Spacer(modifier = Modifier.width(JamiTheme.spacing.m))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = JamiTheme.typography.titleSmall,
                color = JamiTheme.colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(JamiTheme.spacing.xxs))
            Text(
                text = item.ringId,
                style = JamiTheme.typography.bodySmall,
                color = JamiTheme.colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(JamiTheme.spacing.s))
        // Accept
        IconButton(onClick = onAccept) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(Res.string.accept),
                tint = JamiTheme.colors.positive,
            )
        }
        // Refuse
        IconButton(onClick = onRefuse) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = stringResource(Res.string.decline),
                tint = JamiTheme.colors.onSurfaceVariant,
            )
        }
        // Block
        IconButton(onClick = onBlock) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = stringResource(Res.string.block),
                tint = JamiTheme.colors.error,
            )
        }
    }
}
