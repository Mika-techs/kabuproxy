package de.mik.kabuproxy.web.model;

import de.mik.kabuproxy.persistence.entities.DayKind;
import de.mik.kabuproxy.web.I18n;

import java.time.LocalDate;
import java.util.List;

/**
 * @param calendarText entry of the exam plan for that day (exam, holiday name), may be null
 */
public record DayView(LocalDate date, boolean today, List<LessonView> lessons, DayKind kind, String calendarText)
{
    public String weekday()
    {
        return Formats.weekdayShort(date);
    }

    public String weekdayLong()
    {
        return Formats.weekdayLong(date);
    }

    public String dateLabel()
    {
        return Formats.dayMonth(date);
    }

    public String iso()
    {
        return date.toString();
    }

    public boolean free()
    {
        return lessons.isEmpty();
    }

    public boolean holiday()
    {
        return kind == DayKind.HOLIDAY || kind == DayKind.NO_SCHOOL;
    }

    /**
     * Calendar entry shown in the day header (exam, event); free days show it as their label instead.
     */
    public String event()
    {
        return free() ? null : calendarText;
    }

    public boolean exam()
    {
        return kind == DayKind.SCHOOL && CalendarEntryView.isExam(calendarText);
    }

    public String freeLabel()
    {
        if (calendarText != null)
        {
            return calendarText;
        }
        if (kind == DayKind.NO_SCHOOL)
        {
            return I18n.text("day.noSchool");
        }
        return I18n.text("day.noLessons");
    }
}
