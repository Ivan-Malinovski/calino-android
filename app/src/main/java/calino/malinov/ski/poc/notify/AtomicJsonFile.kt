package calino.malinov.ski.poc.notify

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * A small file that must never be read half-written.
 *
 * The reminder schedule and the queued shade actions are both read by
 * receivers that may start in a process which did not write them, possibly
 * moments after the writer was killed. The rules are the ones
 * `FilePendingChangeStore` established for the CalDAV write queue: write a
 * temporary file in the same directory, fsync it, then rename it over the
 * destination, so a reader sees either the previous whole document or the next
 * whole document and never a truncated one. On the way in, a document that
 * cannot be parsed is treated as absent rather than thrown: a corrupt schedule
 * must cost the user their next reminder, not every launch of the app.
 *
 * The write queue keeps its own copy of this logic. It is the older, more
 * heavily tested one and is left alone deliberately; this is the shared
 * implementation for everything added since.
 */
internal class AtomicJsonFile(private val file: File) {

    private fun temporary(): File = File(file.absolutePath + ".tmp")

    /** The document text, or null when there is nothing usable to read. */
    fun read(): String? {
        readable(file)?.let {
            // A temporary file can survive a crash between the fsync and the
            // rename. Once the primary parses, it is a stale uncommitted write.
            temporary().delete()
            return it
        }
        // No usable primary: recover a temporary that was fsynced but never
        // renamed.
        val recovered = readable(temporary()) ?: run {
            temporary().delete()
            return null
        }
        runCatching { replace(temporary(), file) }
        return recovered
    }

    fun write(text: String) {
        val parent = file.absoluteFile.parentFile
            ?: throw IOException("No parent directory for $file")
        if (!parent.exists() && !parent.mkdirs() && !parent.isDirectory) {
            throw IOException("Could not create directory $parent")
        }
        val temporary = temporary()
        try {
            FileOutputStream(temporary).use { output ->
                output.write(text.toByteArray(StandardCharsets.UTF_8))
                output.flush()
                output.fd.sync()
            }
            replace(temporary, file)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun readable(candidate: File): String? {
        if (!candidate.isFile) return null
        return runCatching { candidate.readText(StandardCharsets.UTF_8) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }

    private fun replace(temporary: File, destination: File) {
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            fallbackRename(temporary, destination)
        } catch (_: UnsupportedOperationException) {
            fallbackRename(temporary, destination)
        } catch (_: IOException) {
            fallbackRename(temporary, destination)
        }
    }

    private fun fallbackRename(temporary: File, destination: File) {
        if (!temporary.renameTo(destination)) {
            throw IOException("Could not atomically replace $destination")
        }
    }
}
