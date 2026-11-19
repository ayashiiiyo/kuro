package com.ayashii.kuro.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ayashii.kuro.data.model.Track
import com.ayashii.kuro.ui.theme.KuroAppTheme
import com.ayashii.kuro.ui.theme.Typography
import com.ayashii.kuro.viewmodel.MusicViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsBottomSheet(
    track: Track,
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = KuroAppTheme.colors
    val context = LocalContext.current
    val commentsResult by viewModel.trackComments.collectAsState()
    val isLoading by viewModel.isLoadingComments.collectAsState()
    val isPosting by viewModel.isPostingComment.collectAsState()
    val currentAccount by viewModel.authState.collectAsState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var commentDraft by remember { mutableStateOf("") }
    var statusBanner by remember { mutableStateOf<String?>(null) }
    val likedCommentIds = remember(track.id) { mutableStateListOf<String>() }

    LaunchedEffect(track.id) {
        viewModel.loadCommentsForCurrentTrack(track.id, force = false)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.textPrimary,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.textMuted.copy(alpha = 0.4f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Comments",
                        style = Typography.titleLarge.copy(fontSize = 19.sp, fontWeight = FontWeight.Bold),
                        color = colors.textPrimary
                    )
                    if (commentsResult.commentCountText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = commentsResult.commentCountText,
                            style = Typography.bodyMedium,
                            color = colors.textMuted
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { viewModel.loadCommentsForCurrentTrack(track.id, force = true) },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Comments",
                            tint = colors.textMuted,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = colors.border.copy(alpha = 0.5f))

            if (!statusBanner.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.card)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = statusBanner!!,
                        style = Typography.bodySmall,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Dismiss",
                        style = Typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = colors.accent,
                        modifier = Modifier
                            .clickable { statusBanner = null }
                            .padding(start = 8.dp)
                    )
                }
                HorizontalDivider(color = colors.border.copy(alpha = 0.4f))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isLoading && commentsResult.comments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = colors.textPrimary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                } else if (commentsResult.comments.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 56.dp, horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No comments available for this track.",
                            style = Typography.bodyMedium,
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                                .clickable { viewModel.loadCommentsForCurrentTrack(track.id, force = true) }
                                .padding(horizontal = 16.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "Retry",
                                style = Typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(commentsResult.comments, key = { it.id }) { comment ->
                            val isLikedLocal = comment.id in likedCommentIds
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(colors.card)
                                        .border(1.dp, colors.border.copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (comment.authorAvatarUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = comment.authorAvatarUrl,
                                            contentDescription = comment.authorName,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Text(
                                            text = comment.authorName.removePrefix("@").take(1).uppercase(),
                                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = colors.textPrimary
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = comment.authorName,
                                            style = Typography.labelMedium.copy(
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            ),
                                            color = colors.textSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (comment.publishedTimeText.isNotBlank()) {
                                            Text(
                                                text = "• ${comment.publishedTimeText}",
                                                style = Typography.labelSmall.copy(fontSize = 11.sp),
                                                color = colors.textMuted,
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = comment.text,
                                        style = Typography.bodyMedium.copy(
                                            fontSize = 14.sp,
                                            lineHeight = 19.sp
                                        ),
                                        color = colors.textPrimary
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable {
                                                    if (isLikedLocal) {
                                                        likedCommentIds.remove(comment.id)
                                                    } else {
                                                        likedCommentIds.add(comment.id)
                                                    }
                                                }
                                                .padding(vertical = 2.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isLikedLocal) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                                contentDescription = "Like comment",
                                                tint = if (isLikedLocal) colors.textPrimary else colors.textMuted,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            if (comment.likeCountText.isNotBlank() || isLikedLocal) {
                                                Text(
                                                    text = if (isLikedLocal && comment.likeCountText.isBlank()) "1" else comment.likeCountText,
                                                    style = Typography.labelSmall.copy(fontSize = 11.sp),
                                                    color = if (isLikedLocal) colors.textPrimary else colors.textMuted
                                                )
                                            }
                                        }
                                        if (comment.isHearted) {
                                            Icon(
                                                imageVector = Icons.Default.Favorite,
                                                contentDescription = "Liked by artist",
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.border.copy(alpha = 0.55f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentAccount == null) {
                    Text(
                        text = "Sign in to your Google account in Kuro to post comments.",
                        style = Typography.bodySmall,
                        color = colors.textMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                } else {
                    val submitCommentAction = {
                        if (commentDraft.isNotBlank() && !isPosting) {
                            keyboardController?.hide()
                            val toSend = commentDraft
                            statusBanner = null
                            viewModel.postCommentOnCurrentTrack(toSend) { ok ->
                                if (ok) {
                                    commentDraft = ""
                                    statusBanner = "Comment posted to YouTube."
                                    Toast.makeText(context, "Comment posted", Toast.LENGTH_SHORT).show()
                                } else {
                                    statusBanner = "Could not post comment (comments may be restricted on this official audio track)."
                                    Toast.makeText(context, "Could not post comment on this track", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.card)
                            .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (commentDraft.isEmpty()) {
                            Text(
                                text = "Add a comment as ${currentAccount?.name ?: "You"}…",
                                style = Typography.bodyMedium.copy(fontSize = 14.sp),
                                color = colors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        BasicTextField(
                            value = commentDraft,
                            onValueChange = { commentDraft = it },
                            singleLine = false,
                            maxLines = 3,
                            textStyle = Typography.bodyMedium.copy(
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            ),
                            cursorBrush = SolidColor(colors.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { submitCommentAction() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (commentDraft.isNotBlank()) colors.accent else colors.card)
                            .clickable(enabled = commentDraft.isNotBlank() && !isPosting) {
                                submitCommentAction()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPosting) {
                            CircularProgressIndicator(
                                color = colors.background,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Send,
                                contentDescription = "Send Comment",
                                tint = if (commentDraft.isNotBlank()) colors.background else colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
