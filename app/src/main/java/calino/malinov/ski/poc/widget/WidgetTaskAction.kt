package calino.malinov.ski.poc.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import calino.malinov.ski.poc.data.CalinoContainer
import calino.malinov.ski.poc.data.repository.WriteResult

/**
 * Completing a task from the home screen.
 *
 * The write goes through `CalinoRepository.setTaskDone`, the same call the task
 * surfaces make, which means it inherits the whole policy rather than
 * reimplementing a corner of it: the edit lands in [LocalOverlay] immediately,
 * the durable queue carries it to the server, and a 412 is rebased the way any
 * other task edit is. A tap with no network is queued, not lost, and it is
 * still queued if the process dies on the way.
 *
 * This is the one thing the widget does that reaches past the disk cache, and
 * it is deliberate: the "never put this process on the network" rule in
 * [CalinoAgendaWidget] is about *rendering*, which the launcher provokes
 * constantly and without anyone asking. A tap is a person asking.
 */
internal class ToggleTaskAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val id = parameters[TaskIdKey] ?: return
        val done = parameters[DoneKey] ?: return

        val container = CalinoContainer.get(context)
        // Fixture mode shows the prompt rather than rows, so there is nothing
        // to have tapped; bailing keeps a stray broadcast from writing to the
        // sample data.
        if (!container.hasAccounts) return

        // The launcher may have woken a process that has never read the cache,
        // in which case the repository does not yet know this task and the
        // write would be rejected as "no longer available". Same wait the first
        // render does.
        container.ensureCachedData()
        awaitCachedSnapshot(container)

        when (container.activeRepository.setTaskDone(id, done)) {
            // Applied and Queued both put the change in the overlay and
            // publish, so the row is already correct; the redraw below is what
            // gets that on screen in a process with no bridge attached.
            is WriteResult.Applied, is WriteResult.Queued -> CalinoWidgets.update(context)
            // A task that vanished between the render and the tap. Redrawing is
            // the honest response: it replaces the row with whatever is
            // actually there now.
            is WriteResult.Rejected -> CalinoWidgets.update(context)
        }
    }

    companion object {
        val TaskIdKey = ActionParameters.Key<String>("calino.widget.taskId")
        val DoneKey = ActionParameters.Key<Boolean>("calino.widget.done")
    }
}

/** The action a task row's marker carries: toggle this task to the other state. */
internal fun toggleTask(row: WidgetAgendaRow) = actionRunCallback<ToggleTaskAction>(
    actionParametersOf(
        ToggleTaskAction.TaskIdKey to row.recordId,
        ToggleTaskAction.DoneKey to !row.done,
    ),
)
