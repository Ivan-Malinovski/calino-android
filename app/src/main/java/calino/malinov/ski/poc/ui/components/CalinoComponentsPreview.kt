package calino.malinov.ski.poc.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import calino.malinov.ski.poc.data.model.CalTask
import calino.malinov.ski.poc.design.CalinoColors
import calino.malinov.ski.poc.design.CalinoTheme

@Preview(showBackground = true, backgroundColor = 0xFFFAF8F3)
@Composable
private fun ComponentPreview() {
    CalinoTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EventChip("Design review", CalinoColors.Blue, variant = EventChipVariant.Tint)
            EventChip("Buy groceries", CalinoColors.Green, variant = EventChipVariant.Task)
            AgendaRow("Design review", CalinoColors.Blue, "10:30", "Studio 4")
            TaskRow(CalTask("preview", "Send the invite", 0xFF5D9A78, null, category = "WORK"))
            SegmentedControl(listOf("Event", "Task", "Journal"), 0, {})
            ZoomHandle(1, "PULL AGAIN FOR DETAIL")
        }
    }
}
