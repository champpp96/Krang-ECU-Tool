package com.krang.ecutool

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Read-only import and verified backup for BIN/XDF files.
 * This class never modifies a selected source file and never writes to an ECU.
 */
data class TuneFileReport(
    val originalName: String,
    val fileType: String,
    val sizeBytes: Long,
    val sha256: String,
    val backupFileName: String,
    val binHeaderHex: String? = null,
    val xdfTitle: String? = null,
    val xdfTableCount: Int? = null,
    val xdfParseWarning: String? = null
)

private data class XdfSummary(
    val title: String?,
    val tableCount: Int,
    val warning: String? = null
)

object TuneFileRepository {
    private const val MAX_FILE_BYTES = 128L * 1024L * 1024L
    private const val BUFFER_SIZE = 64 * 1024

    fun importAndBackup(context: Context, uri: Uri): TuneFileReport {
        val resolver = context.contentResolver
        val originalName = queryDisplayName(context, uri)
        val extension = originalName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        require(extension == "bin" || extension == "xdf") {
            "รองรับเฉพาะไฟล์ .BIN และ .XDF เท่านั้น"
        }

        val reportedSize = querySize(context, uri)
        require(reportedSize == null || reportedSize <= MAX_FILE_BYTES) {
            "ไฟล์ใหญ่เกิน 128 MB ในรุ่นทดลองนี้"
        }

        val backupDirectory = File(context.filesDir, "ecu_backups")
        check(backupDirectory.exists() || backupDirectory.mkdirs()) {
            "สร้างโฟลเดอร์สำรองไฟล์ไม่สำเร็จ"
        }

        val safeStem = originalName.substringBeforeLast('.', originalName)
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(48)
            .ifBlank { "ecu-file" }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val unique = UUID.randomUUID().toString().take(8)
        val temporaryFile = File(backupDirectory, "$safeStem-$timestamp-$unique.part")
        val backupFile = File(backupDirectory, "$safeStem-$timestamp-$unique.$extension")

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            val input = resolver.openInputStream(uri)
                ?: throw IllegalStateException("เปิดไฟล์ต้นฉบับไม่ได้")

            input.use { source ->
                temporaryFile.outputStream().buffered(BUFFER_SIZE).use { target ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        require(totalBytes <= MAX_FILE_BYTES) {
                            "ไฟล์ใหญ่เกิน 128 MB ในรุ่นทดลองนี้"
                        }
                        digest.update(buffer, 0, count)
                        target.write(buffer, 0, count)
                    }
                    target.flush()
                }
            }

            require(totalBytes > 0L) { "ไฟล์ว่าง ไม่สามารถนำเข้าได้" }
            val sourceHash = digest.digest().toHex()
            check(temporaryFile.renameTo(backupFile)) { "บันทึกไฟล์สำรองไม่สำเร็จ" }

            val backupHash = sha256(backupFile)
            check(sourceHash == backupHash) {
                "ตรวจสอบสำเนาสำรองไม่ผ่าน ไฟล์ต้นฉบับกับสำเนาไม่ตรงกัน"
            }

            val headerHex: String?
            val xdfSummary: XdfSummary?
            if (extension == "bin") {
                headerHex = readHeader(backupFile)
                xdfSummary = null
            } else {
                headerHex = null
                xdfSummary = try {
                    inspectXdf(backupFile)
                } catch (error: Exception) {
                    XdfSummary(
                        title = null,
                        tableCount = 0,
                        warning = error.message ?: "อ่านโครงสร้าง XML ของ XDF ไม่ได้"
                    )
                }
            }

            return TuneFileReport(
                originalName = originalName,
                fileType = extension.uppercase(Locale.ROOT),
                sizeBytes = totalBytes,
                sha256 = sourceHash,
                backupFileName = backupFile.name,
                binHeaderHex = headerHex,
                xdfTitle = xdfSummary?.title,
                xdfTableCount = xdfSummary?.tableCount,
                xdfParseWarning = xdfSummary?.warning
            )
        } catch (error: Exception) {
            temporaryFile.delete()
            backupFile.delete()
            throw error
        }
    }

    fun exportBackup(context: Context, report: TuneFileReport, destination: Uri) {
        val backupFile = File(File(context.filesDir, "ecu_backups"), report.backupFileName)
        check(backupFile.isFile) { "ไม่พบไฟล์สำรองในพื้นที่ของแอป" }

        val digest = MessageDigest.getInstance("SHA-256")
        val output = context.contentResolver.openOutputStream(destination, "w")
            ?: throw IllegalStateException("เปิดปลายทางสำหรับบันทึกไม่ได้")

        backupFile.inputStream().buffered(BUFFER_SIZE).use { source ->
            output.buffered(BUFFER_SIZE).use { target ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                    target.write(buffer, 0, count)
                }
                target.flush()
            }
        }

        check(digest.digest().toHex() == report.sha256) {
            "ไฟล์ที่ส่งออกไม่ผ่านการตรวจ SHA-256 โปรดตรวจสอบปลายทาง"
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: "selected.bin"
    }

    private fun querySize(context: Context, uri: Uri): Long? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (index >= 0 && !cursor.isNull(index)) return cursor.getLong(index)
                }
            }
        return null
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(BUFFER_SIZE).use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().toHex()
    }

    private fun readHeader(file: File): String {
        val bytes = ByteArray(16)
        val count = file.inputStream().use { it.read(bytes) }
        return bytes.take(count.coerceAtLeast(0)).joinToString(" ") {
            "%02X".format(Locale.ROOT, it.toInt() and 0xFF)
        }
    }

    /** Parse only basic XDF metadata; never evaluate expressions or apply table values. */
    private fun inspectXdf(file: File): XdfSummary {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        var title: String? = null
        var tableCount = 0
        file.inputStream().use { input ->
            parser.setInput(input, null)
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when {
                        parser.name.equals("XDFTABLE", ignoreCase = true) -> tableCount++
                        title == null && parser.name.equals("title", ignoreCase = true) -> {
                            title = parser.nextText().trim().takeIf { it.isNotEmpty() }
                        }
                    }
                }
                event = parser.next()
            }
        }
        return XdfSummary(title = title, tableCount = tableCount)
    }

    private fun ByteArray.toHex(): String = joinToString("") {
        "%02x".format(Locale.ROOT, it.toInt() and 0xFF)
    }
}
