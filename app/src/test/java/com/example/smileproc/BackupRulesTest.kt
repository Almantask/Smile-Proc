package com.example.smileproc

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.XmlResourceParser
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
class BackupRulesTest {

    private val expectedDomains = setOf(
        "root",
        "file",
        "database",
        "sharedpref",
        "external",
        "device_root",
        "device_file",
        "device_database",
        "device_sharedpref",
    )

    @Test
    fun allowBackupIsDisabledAndEveryDomainIsExcludedFromBackupAndDeviceTransfer() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val allowBackupFlag = context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP
        assertEquals("android:allowBackup must be false", 0, allowBackupFlag)

        val fullBackupExcludes = context.resources.getXml(R.xml.backup_rules).use { parser ->
            parseSectionExcludes(parser)["full-backup-content"].orEmpty()
        }
        assertEquals(expectedDomains, fullBackupExcludes)

        val extractionSections = context.resources.getXml(R.xml.data_extraction_rules).use { parser ->
            parseSectionExcludes(parser)
        }
        assertEquals(expectedDomains, extractionSections["cloud-backup"])
        assertEquals(expectedDomains, extractionSections["device-transfer"])
        assertTrue("No <include> rules may be present", !extractionSections.containsKey("__includes__"))
    }

    private fun parseSectionExcludes(parser: XmlResourceParser): Map<String, Set<String>> {
        val result = mutableMapOf<String, MutableSet<String>>()
        val tagStack = ArrayDeque<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name
                    when (name) {
                        "include" -> {
                            result.getOrPut("__includes__") { mutableSetOf() }.add(name)
                        }
                        "exclude" -> {
                            val parent = tagStack.lastOrNull() ?: ""
                            val domain = parser.getAttributeValue(null, "domain")
                            val path = parser.getAttributeValue(null, "path")
                            if (domain != null && path == ".") {
                                result.getOrPut(parent) { mutableSetOf() }.add(domain)
                            }
                        }
                    }
                    tagStack.addLast(name)
                }
                XmlPullParser.END_TAG -> {
                    if (tagStack.isNotEmpty()) {
                        tagStack.removeLast()
                    }
                }
            }
            event = parser.next()
        }
        return result
    }
}
