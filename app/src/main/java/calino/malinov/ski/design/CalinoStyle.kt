package calino.malinov.ski.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** Typography and shapes, independent of a theme's colour palette. */
@Immutable
data class CalinoStyle(
    val id: String,
    val typography: Typography,
    val shapes: CalinoShapeSet,
    val materialShapes: Shapes,
)

object CalinoStyles {
    /** The editorial look the app has always had. */
    val Paper = CalinoStyle(
        id = "paper",
        typography = PaperTypography,
        shapes = CalinoShapeSet(
            Chip = 6.dp,
            Row = 11.dp,
            DayBlock = 10.dp,
            Button = 15.dp,
            Fab = 16.dp,
            Card = 22.dp,
            Sheet = 26.dp,
            Field = 16.dp,
            Pill = 999.dp,
        ),
        materialShapes = Shapes(
            small = RoundedCornerShape(6.dp),
            medium = RoundedCornerShape(11.dp),
            large = RoundedCornerShape(22.dp),
        ),
    )
}
