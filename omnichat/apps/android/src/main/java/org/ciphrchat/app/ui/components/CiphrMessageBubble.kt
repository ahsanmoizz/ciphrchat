package org.ciphrchat.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ciphrchat.app.files.FileTransferProgress
import org.ciphrchat.app.messaging.TransportPresentationPolicy
import org.ciphrchat.app.ui.theme.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CiphrMessageBubble(
    text: String,
    time: String,
    isOutgoing: Boolean,
    statusLabel: String? = null,
    selectedTransport: String? = null,
    attachmentFileName: String? = null,
    attachmentMimeType: String? = null,
    attachmentSizeBytes: Long = 0L,
    attachmentStoragePath: String? = null,
    isForwarded: Boolean = false,
    transferProgress: FileTransferProgress? = null,
    onOpenAttachment: (() -> Unit)? = null,
    onCancelTransfer: (() -> Unit)? = null,
    onResumeTransfer: (() -> Unit)? = null,
    onRetryTransfer: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val route = TransportPresentationPolicy.forName(selectedTransport)
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    val isLargeFile = (attachmentStoragePath?.startsWith("large_file:") == true) ||
            (attachmentSizeBytes > 5 * 1024 * 1024)

    val bubbleColor = if (isOutgoing) CiphrPrimarySoft else CiphrSurfaceMuted

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(bubbleColor)
                .combinedClickable(
                    onClick = {
                        if (attachmentFileName != null && onOpenAttachment != null && !isLargeFile) {
                            onOpenAttachment()
                        }
                    },
                    onLongClick = onLongClick
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                if (isForwarded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 3.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forwarded",
                            tint = CiphrTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Forwarded",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = CiphrTextSecondary
                        )
                    }
                }
                if (attachmentFileName != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attachment", tint = CiphrText)
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(attachmentFileName, color = CiphrText, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "${attachmentMimeType ?: "file"} · ${formatBytes(attachmentSizeBytes)}",
                                color = CiphrTextSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    if (isLargeFile) {
                        Spacer(modifier = Modifier.height(6.dp))
                        when (transferProgress) {
                            is FileTransferProgress.WaitingForReceiver -> {
                                Text(
                                    text = "Waiting for receiver…",
                                    color = CiphrTextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (onCancelTransfer != null) {
                                    TextButton(
                                        onClick = onCancelTransfer,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Cancel", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            is FileTransferProgress.Uploading -> {
                                val frac = (transferProgress.uploadedBytes.toFloat() / transferProgress.totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                val pct = (frac * 100).toInt()
                                Text(
                                    text = "Sending ($pct%)",
                                    color = CiphrText,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "${formatBytes(transferProgress.uploadedBytes)} / ${formatBytes(transferProgress.totalBytes)} · chunk ${transferProgress.currentChunk}/${transferProgress.totalChunks}",
                                    color = CiphrTextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { frac },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                )
                                if (onCancelTransfer != null) {
                                    TextButton(
                                        onClick = onCancelTransfer,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Cancel", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            is FileTransferProgress.Downloading -> {
                                val frac = (transferProgress.downloadedBytes.toFloat() / transferProgress.totalBytes.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                val pct = (frac * 100).toInt()
                                Text(
                                    text = "Receiving ($pct%)",
                                    color = CiphrText,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Text(
                                    text = "${formatBytes(transferProgress.downloadedBytes)} / ${formatBytes(transferProgress.totalBytes)} · chunk ${transferProgress.currentChunk}/${transferProgress.totalChunks}",
                                    color = CiphrTextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { frac },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                                )
                                if (onCancelTransfer != null) {
                                    TextButton(
                                        onClick = onCancelTransfer,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Cancel", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            is FileTransferProgress.Completed -> {
                                Text(
                                    text = "Completed",
                                    color = CiphrSuccess,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (onOpenAttachment != null) {
                                    TextButton(
                                        onClick = onOpenAttachment,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Open file", color = CiphrPrimaryHover, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            is FileTransferProgress.Paused -> {
                                Text(
                                    text = "Paused (${formatBytes(transferProgress.transferredBytes)} / ${formatBytes(transferProgress.totalBytes)})",
                                    color = CiphrWarning,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (onResumeTransfer != null) {
                                    TextButton(
                                        onClick = onResumeTransfer,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Resume", color = CiphrPrimaryHover, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            is FileTransferProgress.Failed -> {
                                Text(
                                    text = "Failed: ${transferProgress.error}",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (onRetryTransfer != null) {
                                    TextButton(
                                        onClick = onRetryTransfer,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Retry", color = CiphrPrimaryHover, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            is FileTransferProgress.Cancelled -> {
                                Text(
                                    text = "Cancelled",
                                    color = CiphrTextSecondary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                                if (onRetryTransfer != null) {
                                    TextButton(
                                        onClick = onRetryTransfer,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Retry", color = CiphrPrimaryHover, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            null -> {
                                if (onOpenAttachment != null) {
                                    TextButton(
                                        onClick = onOpenAttachment,
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Download / Open", color = CiphrPrimaryHover, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    } else if (onOpenAttachment != null) {
                        Text(
                            text = "Open attachment",
                            color = CiphrTextSecondary,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                } else {
                    Text(text = text, color = CiphrText, style = MaterialTheme.typography.bodyLarge)
                }

                if (route != null) {
                    Row(
                        modifier = Modifier.align(Alignment.End).padding(top = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(routeDotColor(selectedTransport))
                        )
                        Text(
                            text = "${route.label} • ${route.operatingRange}",
                            color = CiphrTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = time,
                        color = CiphrTextSecondary,
                        fontSize = 11.sp
                    )
                    if (statusLabel != null) {
                        Text(
                            text = statusLabel,
                            color = CiphrTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}

private fun routeDotColor(name: String?) = when {
    name?.startsWith("INTERNET") == true -> CiphrSuccess
    name?.startsWith("BLUETOOTH") == true || name == "UWB_ASSIST" -> CiphrPrimaryHover
    else -> CiphrWarning
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 * 1024 -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
