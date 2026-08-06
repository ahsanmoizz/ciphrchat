package org.ciphrchat.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val CiphrCardShape = RoundedCornerShape(20.dp)
val CiphrControlShape = RoundedCornerShape(14.dp)
val CiphrButtonShape = RoundedCornerShape(18.dp)
val CiphrPillShape = RoundedCornerShape(999.dp)

val CiphrShapes = Shapes(
    small = CiphrControlShape,
    medium = CiphrCardShape,
    large = CiphrCardShape
)
