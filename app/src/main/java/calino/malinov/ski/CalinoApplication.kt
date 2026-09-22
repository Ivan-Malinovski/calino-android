package calino.malinov.ski

import android.app.Application
import calino.malinov.ski.data.CalinoContainer

/** Process owner for bridges which must also work when no Activity exists. */
class CalinoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val container = CalinoContainer.get(this)
        container.ensureCachedData()
        container.startWearBridge()
    }
}
