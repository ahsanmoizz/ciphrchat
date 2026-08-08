package org.ciphrchat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.ciphrchat.app.ui.theme.CiphrSuccess
import org.ciphrchat.app.ui.theme.CiphrText
import org.ciphrchat.app.ui.theme.CiphrTextSecondary
import org.ciphrchat.app.ui.theme.CiphrWarning
import org.ciphrchat.app.ui.theme.CiphrDanger
import org.ciphrchat.app.ui.theme.CiphrPillShape
import org.ciphrchat.app.ui.theme.CiphrPrimarySoft
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
fun CiphrTransportRow(
    icon: ImageVector,
    name: String,
    status: String,
    statusColor: Color = CiphrTextSecondary,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = name,
                modifier = Modifier.size(22.dp),
                tint = CiphrText
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = CiphrText,
                modifier = Modifier.weight(1f)
            )
        }
        CiphrStatusPill(
            text = status,
            color = statusColor,
            modifier = Modifier.widthIn(max = 160.dp)
        )
    }
}

@Composable
fun CiphrStatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val bgColor = when (color) {
        CiphrSuccess -> Color(0xFFE8F5E9)
        CiphrWarning -> Color(0xFFFFF3E0)
        CiphrDanger -> Color(0xFFFFEBEE)
        else -> CiphrPrimarySoft
    }
    Box(
        modifier = modifier
            .background(bgColor, CiphrPillShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun CiphrSectionHeader(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = CiphrTextSecondary,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun CiphrEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = CiphrText
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = CiphrTextSecondary
        )
    }
}
