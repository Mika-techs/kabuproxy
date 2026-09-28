package de.mik.kabuproxy.web.model;

import de.mik.kabuproxy.persistence.entities.LessonKey;
import de.mik.kabuproxy.persistence.entities.ThemeMode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserSettingsTest
{
    @Test
    void normalizesColors()
    {
        assertEquals("#aabbcc", UserSettings.normalizeColor(" #AABBCC "));
        assertNull(UserSettings.normalizeColor("#abc"));
        assertNull(UserSettings.normalizeColor("red"));
        assertNull(UserSettings.normalizeColor("#aabbcc; background: url(x)"));
        assertNull(UserSettings.normalizeColor(null));
    }

    @Test
    void invalidAccentFallsBackToDefault()
    {
        UserSettings settings = new UserSettings(null, "javascript:alert(1)", null, null, null);
        assertEquals(ThemeMode.SYSTEM, settings.themeMode());
        assertFalse(settings.hasAccent());
        assertEquals(UserSettings.DEFAULT_ACCENT, settings.effectiveAccent());
    }

    @Test
    void themeAttribute()
    {
        assertEquals("", UserSettings.DEFAULT.themeAttr());
        assertEquals("light", new UserSettings(ThemeMode.LIGHT, null, null, null, null).themeAttr());
        assertEquals("dark", new UserSettings(ThemeMode.DARK, null, null, null, null).themeAttr());
    }

    @Test
    void picksReadableTextOnAccent()
    {
        assertEquals("#ffffff", UserSettings.DEFAULT.accentText());
        assertEquals("#ffffff", new UserSettings(ThemeMode.SYSTEM, "#15803d", null, null, null).accentText());
        assertEquals("#1d2130", new UserSettings(ThemeMode.SYSTEM, "#facc15", null, null, null).accentText());
    }

    @Test
    void keepsOnlyKnownValidNonDefaultColors()
    {
        UserSettings settings = new UserSettings(ThemeMode.SYSTEM, null, Map.of(
            "dark-bg", "#000000",
            "light-bg", "#F5F6FA",
            "light-surface", "red; background: url(x)",
            "evil", "#123456"), null, null);
        assertEquals(Map.of("dark-bg", "#000000"), settings.colors());
        assertEquals("#000000", settings.color("dark-bg"));
        assertEquals(ThemeColor.BG.getLightDefault(), settings.color("light-bg"));
    }

    @Test
    void buildsInlineStyle()
    {
        assertEquals("", UserSettings.DEFAULT.style());
        UserSettings settings = new UserSettings(ThemeMode.SYSTEM, "#15803d", Map.of("light-text", "#000000", "dark-bg", "#101010"), null, null);
        assertEquals("--user-accent: #15803d; --user-accent-text: #ffffff; --u-dark-bg: #101010; --u-light-text: #000000;", settings.style());
        assertEquals("--u-dark-bg: #101010;", new UserSettings(null, null, Map.of("dark-bg", "#101010"), null, null).style());
    }

    @Test
    void keepsColorsWhenSwitchingMode()
    {
        UserSettings settings = new UserSettings(ThemeMode.SYSTEM, "#15803d", Map.of("dark-bg", "#101010"), Map.of(LessonKey.of("Mathe"), "#aabbcc"), null)
            .withThemeMode(ThemeMode.DARK);
        assertEquals(ThemeMode.DARK, settings.themeMode());
        assertEquals("#15803d", settings.accentColor());
        assertEquals(Map.of("dark-bg", "#101010"), settings.colors());
        assertEquals(Map.of(LessonKey.of("Mathe"), "#aabbcc"), settings.lessonColors());
    }

    @Test
    void keepsOnlyValidLessonColors()
    {
        Map<LessonKey, String> input = new HashMap<>();
        input.put(new LessonKey(" Mathe ", " RAU "), "#AABBCC");
        input.put(LessonKey.of("Deutsch"), "");
        input.put(LessonKey.of("Englisch"), "red; background: url(x)");
        input.put(LessonKey.of("  "), "#123456");
        input.put(null, "#123456");
        input.put(LessonKey.of("x".repeat(101)), "#123456");
        input.put(new LessonKey("Mathe", "x".repeat(101)), "#123456");
        UserSettings settings = new UserSettings(null, null, null, input, null);
        assertEquals(Map.of(new LessonKey("Mathe", "RAU"), "#aabbcc"), settings.lessonColors());
    }

    @Test
    void resolvesTeacherThenSubjectColor()
    {
        UserSettings settings = new UserSettings(null, null, null, Map.of(
            LessonKey.of("Mathe"), "#111111",
            new LessonKey("Mathe", "RAU"), "#222222",
            new LessonKey("Deutsch", "HEC"), "#333333"), null);
        assertEquals("#222222", settings.lessonColor("Mathe", "RAU"));
        assertEquals("#222222", settings.lessonColor(" Mathe", "RAU "));
        assertEquals("#111111", settings.lessonColor("Mathe", "IRL"));
        assertEquals("#111111", settings.lessonColor("Mathe", null));
        assertEquals("#333333", settings.lessonColor("Deutsch", "HEC"));
        assertNull(settings.lessonColor("Deutsch", "MOL"));
        assertNull(settings.lessonColor(null, "HEC"));
    }

    @Test
    void buildsLessonStyle()
    {
        UserSettings settings = new UserSettings(null, null, null, Map.of(LessonKey.of("Mathe"), "#aabbcc"), null);
        assertEquals("--lesson-color: #aabbcc;", settings.lessonStyle("Mathe", "RAU"));
        assertEquals("", settings.lessonStyle("Deutsch", null));
        assertEquals("", UserSettings.DEFAULT.lessonStyle(null, null));
        assertEquals("", UserSettings.lessonColorStyle("#aabbcc; color: red"));
    }

    @Test
    void keepsOnlyValidLessonNames()
    {
        Map<LessonKey, String> input = new HashMap<>();
        input.put(new LessonKey(" AEuP ", " RAH "), "  Web ");
        input.put(new LessonKey("AEuP", "SCH"), "AEuP");
        input.put(LessonKey.of("Deutsch"), "Deutsch");
        input.put(LessonKey.of("Englisch"), "   ");
        input.put(LessonKey.of("Mathe"), null);
        input.put(LessonKey.of("Sport"), "x".repeat(101));
        input.put(LessonKey.of("  "), "Leer");
        input.put(null, "Nichts");
        UserSettings settings = new UserSettings(null, null, null, null, input);
        assertEquals(Map.of(new LessonKey("AEuP", "RAH"), "Web", new LessonKey("AEuP", "SCH"), "AEuP"), settings.lessonNames());
    }

    @Test
    void resolvesTeacherThenSubjectName()
    {
        UserSettings settings = new UserSettings(null, null, null, null, Map.of(
            LessonKey.of("AEuP"), "Anwendungsentwicklung",
            new LessonKey("AEuP", "RAH"), "Web"));
        assertEquals("Web", settings.lessonName("AEuP", "RAH"));
        assertEquals("Web", settings.lessonName(" AEuP", "RAH "));
        assertEquals("Anwendungsentwicklung", settings.lessonName("AEuP", "SCH"));
        assertEquals("Anwendungsentwicklung", settings.lessonName("AEuP", null));
        assertEquals("Deutsch", settings.lessonName("Deutsch", "HEC"));
        assertNull(settings.lessonName(null, "RAH"));
    }

    @Test
    void groupsLessonsByNameOrSubjectAndTeacher()
    {
        UserSettings settings = new UserSettings(null, null, null, null, Map.of(
            LessonKey.of("AEuP"), "Anwendungsentwicklung",
            new LessonKey("AEuP", "RAH"), "Web",
            new LessonKey("IT", "HEC"), "Web"));
        assertEquals(settings.lessonGroup("AEuP", "RAH"), settings.lessonGroup("IT", "HEC"));
        assertEquals(settings.lessonGroup("AEuP", "SCH"), settings.lessonGroup("AEuP", "MUE"));
        assertEquals(settings.lessonGroup("Deutsch", "HEC"), settings.lessonGroup(" Deutsch", "HEC "));
        assertNotEquals(settings.lessonGroup("Deutsch", "HEC"), settings.lessonGroup("Deutsch", "MUE"));
        assertNotEquals(settings.lessonGroup("IT", "HEC"), settings.lessonGroup("IT", "MUE"));
        assertEquals("", settings.lessonGroup(null, "RAH"));
    }
}
