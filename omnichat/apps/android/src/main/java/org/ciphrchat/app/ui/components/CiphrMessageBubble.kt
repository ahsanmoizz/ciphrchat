package org.ciphrchat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.ciphrchat.app.ui.theme.CiphrBorder
import org.ciphrchat.app.ui.theme.CiphrPrimarySoft
import org.ciphrchat.app.ui.theme.CiphrSurfaceMuted
import org.ciphrchat.app.ui.theme.CiphrText
import org.ciphrchat.app.ui.theme.CiphrTextSecondary

@Composable
fun CiphrMessageBubble(
    text: String,
    time: String,
    isOutgoing: Boolean,
    statusLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
    }

    val bubbleColor = if (isOutgoing) CiphrPrimarySoft else CiphrSurfaceMuted
    val alignment = if (isOutgoing) Alignment.End else Alignment.Start

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleColor, bubbleShape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = text,
                    color = CiphrText,
                    style = MaterialTheme.typography.bodyLarge
                )
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
