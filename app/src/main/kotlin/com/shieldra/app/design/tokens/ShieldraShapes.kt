package com.shieldra.app.design.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ShieldraShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(14.dp),
    large      = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Pill — used by chips and badges. */
val ShieldraPillShape = RoundedCornerShape(percent = 50)

/** Default card corner. */
val ShieldraCardShape = RoundedCornerShape(14.dp)

/** Hero / modal corner. */
val ShieldraHeroShape = RoundedCornerShape(20.dp)
