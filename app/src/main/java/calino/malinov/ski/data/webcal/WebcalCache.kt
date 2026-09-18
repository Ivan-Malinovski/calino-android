package calino.malinov.ski.data.webcal

import java.io.File

/** Last successful ICS document per subscription, so a cold start is not blank. */
interface WebcalCache {
    fun save(id: String, ics: String)
    fun load(id: String): String?
    fun delete(id: String)

    object None : WebcalCache {
        override fun save(id: String, ics: String) = Unit
        override fun load(id: String): String? = null
        override fun delete(id: String) = Unit
    }
}

class FileWebcalCache(private val directory: File) : WebcalCache {
    override fun save(id: String, ics: String) {
        directory.mkdirs()
        File(directory, fileName(id)).writeText(ics)
    }

    override fun load(id: String): String? {
        val file = File(directory, fileName(id))
        if (!file.isFile) return null
        return file.readText()
    }

    override fun delete(id: String) {
        File(directory, fileName(id)).delete()
    }

    private fun fileName(id: String): String = "$id.ics"
}
