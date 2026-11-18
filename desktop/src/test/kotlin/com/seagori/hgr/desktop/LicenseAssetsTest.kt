package com.seagori.hgr.desktop

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** 인앱 라이선스 화면이 읽는 리소스가 패키징되는지 검증한다. */
class LicenseAssetsTest {
    private fun load(path: String): String {
        val stream = LicenseAssetsTest::class.java.classLoader?.getResourceAsStream(path)
        assertNotNull(stream, "missing resource: $path")
        return stream.bufferedReader().use { it.readText() }
    }

    @Test
    fun licenseResourcesArePackaged() {
        val files = listOf(
            "licenses/NOTICE.txt",
            "licenses/GPL-3.0.txt",
            "licenses/CC-BY-SA-4.0.txt",
            "licenses/THIRD_PARTY_NOTICES.txt",
            "licenses/Apache-2.0.txt",
            "licenses/Bouncy-Castle.txt"
        )
        files.forEach { path ->
            assertTrue(load(path).isNotBlank(), "empty resource: $path")
        }
    }

    @Test
    fun licenseTextsContainExpectedHeaders() {
        assertTrue(load("licenses/GPL-3.0.txt").contains("GNU GENERAL PUBLIC LICENSE"))
        assertTrue(load("licenses/CC-BY-SA-4.0.txt").contains("Attribution-ShareAlike 4.0"))
        assertTrue(load("licenses/Apache-2.0.txt").contains("Apache License"))
        assertTrue(load("licenses/Bouncy-Castle.txt").contains("Bouncy Castle Licence"))
        val notices = load("licenses/THIRD_PARTY_NOTICES.txt")
        assertTrue(notices.contains("ZXing"), "third-party notices must list ZXing")
        assertTrue(notices.contains("Bouncy Castle"), "third-party notices must list Bouncy Castle")
        assertTrue(notices.contains("ML Kit is not included"), "ML Kit removal must be documented")
    }
}
