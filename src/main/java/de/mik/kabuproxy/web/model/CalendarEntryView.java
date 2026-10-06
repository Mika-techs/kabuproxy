package de.mik.kabuproxy.web.model;

import de.mik.kabuproxy.persistence.entities.DayKind;

import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * One line of the "Termine" page; consecutive school days (or days with the same holiday text) are merged into a range.
 */
public record CalendarEntryView(LocalDate from, LocalDate to, DayKind kind, String text, boolean schoolRange, boolean past, boolean today)
{
    /**
     * "SchA ITP", "1.SA AEuP", "Ex D", "KA", "Prüfung", "Test" (abbreviations case-sensitive, so the weekday "Sa" never matches).
     */
    private static final Pattern EXAM = Pattern.compile("\\b(SchA|SA|Ex|KA)\\b|(?i:prüfung|klausur|\\btest\\b)");

    public String dateLabel()
    {
        if (from.equals(to))
        {
            return Formats.weekdayShort(from) + " " + Formats.dayMonth(from);
        }
        return Formats.dayMonth(from) + " – " + Formats.dayMonth(to);
    }

    public String cssClass()
    {
        String base = switch (kind)
        {
            case SCHOOL -> schoolRange ? "entry entry--school" : "entry entry--event";
            case HOLIDAY -> "entry entry--holiday";
            case NO_SCHOOL -> "entry entry--noschool";
        };
        return base + (past ? " entry--past" : "") + (today ? " entry--today" : "");
    }

    public boolean exam()
    {
        return kind == DayKind.SCHOOL && !schoolRange && isExam(text);
    }

    public static boolean isExam(String text)
    {
        return text != null && EXAM.matcher(text).find();
    }
}
