package calino.malinov.ski.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector.Builder

/** The small Lucide-compatible subset used by the primitives (24dp, 2dp stroke). */
object CalinoIcons {
    val Plus: ImageVector by lazy { icon("plus") { moveTo(12f, 5f); verticalLineTo(19f); moveTo(5f, 12f); horizontalLineTo(19f) } }
    val Check: ImageVector by lazy { icon("check") { moveTo(5f, 12f); lineTo(10f, 17f); lineTo(19f, 7f) } }
    val ChevronLeft: ImageVector by lazy { icon("chevron-left") { moveTo(15f, 18f); lineTo(9f, 12f); lineTo(15f, 6f) } }
    val ChevronRight: ImageVector by lazy { icon("chevron-right") { moveTo(9f, 18f); lineTo(15f, 12f); lineTo(9f, 6f) } }
    val ChevronDown: ImageVector by lazy { icon("chevron-down") { moveTo(6f, 9.5f); lineTo(12f, 15.5f); lineTo(18f, 9.5f) } }
    val Search: ImageVector by lazy { icon("search") { circle(11f, 11f, 7f); moveTo(16f, 16f); lineTo(21f, 21f) } }
    val Camera: ImageVector by lazy { icon("camera") { moveTo(4f, 7f); curveTo(4f, 5.9f, 4.9f, 5f, 6f, 5f); horizontalLineTo(8f); lineTo(9.5f, 3f); horizontalLineTo(14.5f); lineTo(16f, 5f); horizontalLineTo(18f); curveTo(19.1f, 5f, 20f, 5.9f, 20f, 7f); verticalLineTo(18f); curveTo(20f, 19.1f, 19.1f, 20f, 18f, 20f); horizontalLineTo(6f); curveTo(4.9f, 20f, 4f, 19.1f, 4f, 18f); close(); circle(12f, 12f, 3f) } }
    val Calendar: ImageVector by lazy { icon("calendar") { moveTo(4f, 5f); horizontalLineTo(20f); verticalLineTo(21f); horizontalLineTo(4f); close(); moveTo(8f, 3f); verticalLineTo(7f); moveTo(16f, 3f); verticalLineTo(7f); moveTo(4f, 10f); horizontalLineTo(20f) } }
    /** The range calendar: the calendar glyph split into day columns, so it never reads as Month. */
    val CalendarRange: ImageVector by lazy { icon("calendar-range") { moveTo(4f, 5f); horizontalLineTo(20f); verticalLineTo(21f); horizontalLineTo(4f); close(); moveTo(8f, 3f); verticalLineTo(7f); moveTo(16f, 3f); verticalLineTo(7f); moveTo(4f, 10f); horizontalLineTo(20f); moveTo(9.5f, 10f); verticalLineTo(21f); moveTo(14.5f, 10f); verticalLineTo(21f) } }
    val X: ImageVector by lazy { icon("x") { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) } }
    val Clock: ImageVector by lazy { icon("clock") { circle(12f, 12f, 9f); moveTo(12f, 7f); verticalLineTo(12f); lineTo(15f, 14f) } }
    val Pin: ImageVector by lazy { icon("map-pin") { moveTo(20f, 10f); curveTo(20f, 15f, 12f, 22f, 12f, 22f); curveTo(12f, 22f, 4f, 15f, 4f, 10f); arcTo(8f, 8f, 0f, true, true, 20f, 10f); close(); circle(12f, 10f, 2.5f) } }
    val Repeat: ImageVector by lazy { icon("repeat") { moveTo(17f, 1f); lineTo(21f, 5f); lineTo(17f, 9f); moveTo(3f, 11f); verticalLineTo(9f); curveTo(3f, 6.8f, 4.8f, 5f, 7f, 5f); horizontalLineTo(21f); moveTo(7f, 23f); lineTo(3f, 19f); lineTo(7f, 15f); moveTo(21f, 13f); verticalLineTo(15f); curveTo(21f, 17.2f, 19.2f, 19f, 17f, 19f); horizontalLineTo(3f) } }
    val Note: ImageVector by lazy { icon("file-text") { moveTo(15f, 2f); horizontalLineTo(6f); curveTo(4.9f, 2f, 4f, 2.9f, 4f, 4f); verticalLineTo(20f); curveTo(4f, 21.1f, 4.9f, 22f, 6f, 22f); horizontalLineTo(18f); curveTo(19.1f, 22f, 20f, 21.1f, 20f, 20f); verticalLineTo(7f); close(); moveTo(14f, 2f); verticalLineTo(8f); horizontalLineTo(20f); moveTo(8f, 13f); horizontalLineTo(16f); moveTo(8f, 17f); horizontalLineTo(16f); moveTo(8f, 9f); horizontalLineTo(10f) } }
    val Users: ImageVector by lazy { icon("users") { pathWithoutFill { moveTo(17f, 21f); verticalLineTo(19f); curveTo(17f, 16.8f, 15.2f, 15f, 13f, 15f); horizontalLineTo(7f); curveTo(4.8f, 15f, 3f, 16.8f, 3f, 19f); verticalLineTo(21f); moveTo(10f, 11f); curveTo(12.2f, 11f, 14f, 9.2f, 14f, 7f); curveTo(14f, 4.8f, 12.2f, 3f, 10f, 3f); curveTo(7.8f, 3f, 6f, 4.8f, 6f, 7f); curveTo(6f, 9.2f, 7.8f, 11f, 10f, 11f); moveTo(21f, 21f); verticalLineTo(19f); curveTo(21f, 16.8f, 19.2f, 15f, 17f, 15f); moveTo(17f, 3.1f); curveTo(18.7f, 3.5f, 20f, 5.1f, 20f, 7f); curveTo(20f, 8.9f, 18.7f, 10.5f, 17f, 10.9f) } } }
    val Edit: ImageVector by lazy { icon("pencil") { pathWithoutFill { moveTo(12f, 20f); horizontalLineTo(21f); moveTo(16.5f, 3.5f); curveTo(17.3f, 2.7f, 18.7f, 2.7f, 19.5f, 3.5f); lineTo(20.5f, 4.5f); curveTo(21.3f, 5.3f, 21.3f, 6.7f, 20.5f, 7.5f); lineTo(8f, 20f); lineTo(3f, 21f); lineTo(4f, 16f); close(); lineTo(16.5f, 3.5f) } } }
    val Trash: ImageVector by lazy { icon("trash-2") { moveTo(3f, 6f); horizontalLineTo(21f); moveTo(8f, 6f); verticalLineTo(4f); curveTo(8f, 2.9f, 8.9f, 2f, 10f, 2f); horizontalLineTo(14f); curveTo(15.1f, 2f, 16f, 2.9f, 16f, 4f); verticalLineTo(6f); moveTo(19f, 6f); lineTo(18f, 20f); curveTo(17.9f, 21.1f, 17.1f, 22f, 16f, 22f); horizontalLineTo(8f); curveTo(6.9f, 22f, 6.1f, 21.1f, 6f, 20f); lineTo(5f, 6f); moveTo(10f, 11f); verticalLineTo(17f); moveTo(14f, 11f); verticalLineTo(17f) } }
    val Bell: ImageVector by lazy { icon("bell") { pathWithoutFill { pathMove { moveTo(18f, 8f); curveTo(18f, 4.7f, 15.3f, 2f, 12f, 2f); curveTo(8.7f, 2f, 6f, 4.7f, 6f, 8f); curveTo(6f, 13f, 4f, 14f, 4f, 16f); horizontalLineTo(20f); curveTo(20f, 14f, 18f, 13f, 18f, 8f); moveTo(10f, 20f); curveTo(10.5f, 21.2f, 11.2f, 22f, 12f, 22f); curveTo(12.8f, 22f, 13.5f, 21.2f, 14f, 20f) } } } }
    val Filter: ImageVector by lazy { icon("filter") { moveTo(22f, 3f); horizontalLineTo(2f); lineTo(10f, 12.5f); verticalLineTo(19f); lineTo(14f, 21f); verticalLineTo(12.5f); close() } }
    val More: ImageVector by lazy { icon("more-vertical") { circle(12f, 5f, 1f); circle(12f, 12f, 1f); circle(12f, 19f, 1f) } }
    val BookOpen: ImageVector by lazy { icon("book-open") { pathWithoutFill { moveTo(2f, 4f); curveTo(6f, 4f, 9f, 5f, 12f, 7f); curveTo(15f, 5f, 18f, 4f, 22f, 4f); verticalLineTo(19f); curveTo(18f, 19f, 15f, 20f, 12f, 22f); curveTo(9f, 20f, 6f, 19f, 2f, 19f); close(); moveTo(12f, 7f); verticalLineTo(22f) } } }
    val Settings: ImageVector by lazy { icon("sliders-horizontal") { moveTo(14f, 4f); horizontalLineTo(21f); moveTo(3f, 4f); horizontalLineTo(10f); moveTo(14f, 2f); verticalLineTo(6f); moveTo(8f, 12f); horizontalLineTo(21f); moveTo(3f, 12f); horizontalLineTo(4f); moveTo(8f, 10f); verticalLineTo(14f); moveTo(16f, 20f); horizontalLineTo(21f); moveTo(3f, 20f); horizontalLineTo(12f); moveTo(16f, 18f); verticalLineTo(22f) } }
    val ListChecks: ImageVector by lazy { icon("list-checks") { pathWithoutFill { moveTo(11f, 6f); horizontalLineTo(21f); moveTo(11f, 12f); horizontalLineTo(21f); moveTo(11f, 18f); horizontalLineTo(21f); moveTo(3f, 6f); lineTo(4.5f, 7.5f); lineTo(7f, 4.5f); moveTo(3f, 12f); lineTo(4.5f, 13.5f); lineTo(7f, 10.5f); moveTo(3f, 18f); lineTo(4.5f, 19.5f); lineTo(7f, 16.5f) } } }

    val AgendaList: ImageVector by lazy { icon("list") { moveTo(9f, 6f); horizontalLineTo(21f); moveTo(9f, 12f); horizontalLineTo(21f); moveTo(9f, 18f); horizontalLineTo(21f); moveTo(4f, 6f); horizontalLineTo(4.01f); moveTo(4f, 12f); horizontalLineTo(4.01f); moveTo(4f, 18f); horizontalLineTo(4.01f) } }

    val Refresh: ImageVector by lazy { icon("refresh") { pathWithoutFill { moveTo(23f, 4f); verticalLineTo(10f); horizontalLineTo(17f); moveTo(20.5f, 9f); curveTo(19.1f, 5.5f, 15.8f, 3f, 12f, 3f); curveTo(7f, 3f, 3f, 7f, 3f, 12f); moveTo(1f, 20f); verticalLineTo(14f); horizontalLineTo(7f); moveTo(3.5f, 15f); curveTo(4.9f, 18.5f, 8.2f, 21f, 12f, 21f); curveTo(17f, 21f, 21f, 17f, 21f, 12f) } } }

    val Menu: ImageVector by lazy { icon("menu") { moveTo(3f, 6f); horizontalLineTo(21f); moveTo(3f, 12f); horizontalLineTo(21f); moveTo(3f, 18f); horizontalLineTo(21f) } }

    private fun icon(name: String, block: PathBuilder.() -> Unit): ImageVector = Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, fill = null, pathBuilder = block)
    }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
        moveTo(cx + radius, cy)
        arcTo(radius, radius, 0f, true, false, cx - radius, cy)
        arcTo(radius, radius, 0f, true, false, cx + radius, cy)
    }

    private fun PathBuilder.pathWithoutFill(block: PathBuilder.() -> Unit) = block()
    private fun PathBuilder.pathMove(block: PathBuilder.() -> Unit) = block()
}
