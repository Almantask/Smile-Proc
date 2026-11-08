package com.example.smileproc.data

import android.content.Context
import com.example.smileproc.SafeLog
import com.example.smileproc.core.CanonicalJson
import com.example.smileproc.core.ErrorLogEntry
import com.example.smileproc.core.IsoInstantSerializer
import com.example.smileproc.core.crypto.SealException
import com.example.smileproc.core.crypto.Sealer
import com.example.smileproc.core.export.ExportException
import kotlinx.serialization.builtins.ListSerializer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.Clock
import java.util.Locale
import java.util.UUID

/**
 * Encrypted local storage and exporter for application error logs.
 *
 * All stored records are AES-256-GCM sealed at rest using [Sealer] with row ID AAD.
 * When exported, error logs are decrypted into canonical JSON for diagnostic review.
 */
class ErrorLogStore(
    context: Context,
    private val sealer: Sealer,
    private val clock: Clock = Clock.systemUTC(),
    private val baseDir: File = context.applicationContext.filesDir,
    private val fileName: String = FILE_NAME,
) {
    private val lock = Any()
    private val file: File get() = File(baseDir, fileName)

    @Volatile
    private var isRecording = false
    private val pendingUnsealed = mutableListOf<ErrorLogEntry>()

    fun record(event: SafeLog.Event, error: Throwable? = null) {
        if (isRecording) return
        val now = clock.instant()
        val entryId = UUID.randomUUID().toString().lowercase(Locale.ROOT)
        val errorClassName = error?.javaClass?.name ?: "None"
        val safeDetails = when (error) {
            is SealException -> error.reason.name
            is ExportException -> error.reason.name
            else -> null
        }
        val entry = ErrorLogEntry(
            id = entryId,
            timestamp = IsoInstantSerializer.format(now),
            event = event.name,
            errorClass = errorClassName,
            details = safeDetails,
        )

        synchronized(lock) {
            try {
                isRecording = true
                if (sealer.isKeyAvailable()) {
                    flushPendingLocked()
                    persistEntryLocked(entry)
                } else {
                    pendingUnsealed.add(entry)
                    if (pendingUnsealed.size > MAX_ENTRIES) {
                        pendingUnsealed.removeAt(0)
                    }
                }
            } catch (_: Throwable) {
                // If sealing fails (e.g. temporary key issue), keep in memory
                pendingUnsealed.add(entry)
            } finally {
                isRecording = false
            }
        }
    }

    fun getAllDecrypted(): List<ErrorLogEntry> = synchronized(lock) {
        val result = mutableListOf<ErrorLogEntry>()
        if (file.exists() && sealer.isKeyAvailable()) {
            flushPendingLocked()
            try {
                FileInputStream(file).use { fis ->
                    DataInputStream(fis).use { dis ->
                        while (dis.available() > 0) {
                            val idLength = dis.readInt()
                            val idBytes = ByteArray(idLength)
                            dis.readFully(idBytes)
                            val id = String(idBytes, Charsets.UTF_8)

                            val payloadLength = dis.readInt()
                            val payload = ByteArray(payloadLength)
                            dis.readFully(payload)

                            try {
                                val json = sealer.openUtf8(id, payload)
                                val decoded = CanonicalJson.json.decodeFromString(ErrorLogEntry.serializer(), json)
                                result.add(decoded)
                            } catch (_: Exception) {
                                // Ignore corrupted individual row
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore read issues
            }
        }
        result.addAll(pendingUnsealed)
        result.sortedBy { it.timestamp }
    }

    fun count(): Int = synchronized(lock) {
        getAllDecrypted().size
    }

    fun clear() = synchronized(lock) {
        pendingUnsealed.clear()
        if (file.exists()) {
            file.delete()
        }
    }

    fun exportDecrypted(outputStream: OutputStream) {
        val entries = getAllDecrypted()
        val json = CanonicalJson.prettyJson.encodeToString(
            ListSerializer(ErrorLogEntry.serializer()),
            entries,
        )
        outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(json)
            writer.flush()
        }
    }

    private fun persistEntryLocked(entry: ErrorLogEntry) {
        val json = CanonicalJson.json.encodeToString(ErrorLogEntry.serializer(), entry)
        val sealed = sealer.sealUtf8(entry.id, json)
        val idBytes = entry.id.toByteArray(Charsets.UTF_8)

        FileOutputStream(file, true).use { fos ->
            DataOutputStream(fos).use { dos ->
                dos.writeInt(idBytes.size)
                dos.write(idBytes)
                dos.writeInt(sealed.size)
                dos.write(sealed)
                dos.flush()
            }
        }
        trimIfNeededLocked()
    }

    private fun flushPendingLocked() {
        if (pendingUnsealed.isEmpty() || !sealer.isKeyAvailable()) return
        val toFlush = ArrayList(pendingUnsealed)
        pendingUnsealed.clear()
        for (item in toFlush) {
            try {
                persistEntryLocked(item)
            } catch (_: Exception) {
                pendingUnsealed.add(item)
            }
        }
    }

    private fun trimIfNeededLocked() {
        if (!file.exists() || !sealer.isKeyAvailable()) return
        val all = mutableListOf<ErrorLogEntry>()
        try {
            FileInputStream(file).use { fis ->
                DataInputStream(fis).use { dis ->
                    while (dis.available() > 0) {
                        val idLength = dis.readInt()
                        val idBytes = ByteArray(idLength)
                        dis.readFully(idBytes)
                        val id = String(idBytes, Charsets.UTF_8)

                        val payloadLength = dis.readInt()
                        val payload = ByteArray(payloadLength)
                        dis.readFully(payload)

                        try {
                            val json = sealer.openUtf8(id, payload)
                            val decoded = CanonicalJson.json.decodeFromString(ErrorLogEntry.serializer(), json)
                            all.add(decoded)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } catch (_: Exception) {
            return
        }

        if (all.size > MAX_ENTRIES) {
            val keep = all.takeLast(KEEP_ENTRIES_ON_TRIM)
            file.delete()
            for (item in keep) {
                val json = CanonicalJson.json.encodeToString(ErrorLogEntry.serializer(), item)
                val sealed = sealer.sealUtf8(item.id, json)
                val idBytes = item.id.toByteArray(Charsets.UTF_8)
                FileOutputStream(file, true).use { fos ->
                    DataOutputStream(fos).use { dos ->
                        dos.writeInt(idBytes.size)
                        dos.write(idBytes)
                        dos.writeInt(sealed.size)
                        dos.write(sealed)
                        dos.flush()
                    }
                }
            }
        }
    }

    companion object {
        const val FILE_NAME = "error_logs.sealed"
        const val MAX_ENTRIES = 200
        const val KEEP_ENTRIES_ON_TRIM = 100
    }
}
