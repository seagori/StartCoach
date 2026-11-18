package com.seagori.hgr.desktop

import com.seagori.hgr.i18n.AppLang
import com.seagori.hgr.i18n.AppLanguage
import com.seagori.hgr.i18n.AppLocale
import com.seagori.hgr.i18n.resolveAppLang
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 중국어 정체/간체 선택과 시스템 로케일 판별 회귀 테스트. */
class LocalizationTest {
    @Test
    fun chineseVariantsAreIndependent() {
        val hans = AppLocale.stringsFor(AppLanguage.CHINESE_SIMPLIFIED)
        val hant = AppLocale.stringsFor(AppLanguage.CHINESE_TRADITIONAL)

        assertEquals(AppLang.ZH_HANS, hans.lang)
        assertEquals(AppLang.ZH_HANT, hant.lang)
        assertEquals("Simplified Chinese", hans.languagePromptName)
        assertEquals("Traditional Chinese", hant.languagePromptName)
        assertEquals("简体中文", hans.languageChineseSimplified)
        assertEquals("正體中文", hant.languageChineseTraditional)

        assertTrue(hans.homeInputHint.contains("输入"), hans.homeInputHint)
        assertTrue(hant.homeInputHint.contains("輸入"), hant.homeInputHint)
        assertTrue(hant.localOnlyNote.contains("裝置"), hant.localOnlyNote)
        assertTrue(hans.localOnlyNote.contains("设备"), hans.localOnlyNote)
    }

    @Test
    fun retryLaterSeparatesSentences() {
        val strings = AppLocale.stringsFor(AppLanguage.KOREAN)
        assertEquals(
            "제안을 받지 못했어요. 잠시 후 다시 시도해 주세요.",
            strings.retryLater("제안을 받지 못했어요")
        )
        // 이미 문장부호로 끝나면 이중 부호가 붙지 않는다.
        assertEquals(
            "연결 실패. 잠시 후 다시 시도해 주세요.",
            strings.retryLater("연결 실패.")
        )
    }

    @Test
    fun streakPartsComposeNaturallyPerLanguage() {
        val ko = AppLocale.stringsFor(AppLanguage.KOREAN)
        assertEquals("", ko.streakPrefix)
        assertEquals("일 연속", ko.streakSuffix)

        val hans = AppLocale.stringsFor(AppLanguage.CHINESE_SIMPLIFIED)
        assertEquals("连续", hans.streakPrefix)
        assertEquals("天", hans.streakSuffix)

        val hant = AppLocale.stringsFor(AppLanguage.CHINESE_TRADITIONAL)
        assertEquals("連續", hant.streakPrefix)
        assertEquals("天", hant.streakSuffix)
    }

    @Test
    fun systemLocaleResolvesChineseVariant() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("zh-Hant-TW"))
            assertEquals(AppLang.ZH_HANT, resolveAppLang(AppLanguage.SYSTEM))

            Locale.setDefault(Locale.forLanguageTag("zh-Hans-CN"))
            assertEquals(AppLang.ZH_HANS, resolveAppLang(AppLanguage.SYSTEM))

            // 스크립트가 생략된 로케일은 국가/지역으로 판별한다.
            Locale.setDefault(Locale.forLanguageTag("zh-TW"))
            assertEquals(AppLang.ZH_HANT, resolveAppLang(AppLanguage.SYSTEM))

            Locale.setDefault(Locale.forLanguageTag("zh-HK"))
            assertEquals(AppLang.ZH_HANT, resolveAppLang(AppLanguage.SYSTEM))

            Locale.setDefault(Locale.forLanguageTag("zh-SG"))
            assertEquals(AppLang.ZH_HANS, resolveAppLang(AppLanguage.SYSTEM))
        } finally {
            Locale.setDefault(original)
        }
    }
}
