package de.mik.kabuproxy.service;

import de.mik.kabuproxy.persistence.entities.CalendarDayEntity;
import de.mik.kabuproxy.persistence.entities.DayKind;
import de.mik.kabuproxy.persistence.entities.LessonEntity;
import de.mik.kabuproxy.persistence.entities.LessonStatus;
import de.mik.kabuproxy.persistence.entities.PeriodSlotEntity;
import de.mik.kabuproxy.persistence.repository.CalendarRepository;
import de.mik.kabuproxy.web.model.CalendarEntryView;
import de.mik.kabuproxy.web.model.LessonView;
import de.mik.kabuproxy.web.model.MonthView;
import de.mik.kabuproxy.web.model.PeriodView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimetableQueryServiceTest
{
    @Mock private CalendarRepository calendarRepository;

    @InjectMocks private TimetableQueryService service;

    @Test
    void defaultWeekIsNextWeekOnWeekends()
    {
        assertEquals(LocalDate.of(2026, 9, 21), TimetableQueryService.defaultMonday(LocalDate.of(2026, 9, 24)));
        assertEquals(LocalDate.of(2026, 9, 28), TimetableQueryService.defaultMonday(LocalDate.of(2026, 9, 26)));
        assertEquals(LocalDate.of(2026, 9, 28), TimetableQueryService.defaultMonday(LocalDate.of(2026, 9, 27)));
    }

    @Test
    void mergesSchoolRangesAcrossWeekendsAndDropsPlainNoSchoolDays()
    {
        List<CalendarDayEntity> days = new ArrayList<>();
        // plain no-school days are left out
        days.add(day(2026, 10, 1, DayKind.NO_SCHOOL, null));
        days.add(day(2026, 10, 2, DayKind.NO_SCHOOL, null));
        // Mon 05.10.-Fri 09.10., weekend, Mon 12.10.-Tue 13.10. school -> one range, exam day listed within it
        for (int d = 5; d <= 9; d++)
        {
            days.add(day(2026, 10, d, DayKind.SCHOOL, d == 7 ? "SchA D" : null));
        }
        days.add(day(2026, 10, 10, DayKind.HOLIDAY, null));
        days.add(day(2026, 10, 11, DayKind.HOLIDAY, null));
        days.add(day(2026, 10, 12, DayKind.SCHOOL, null));
        days.add(day(2026, 10, 13, DayKind.SCHOOL, null));
        days.add(day(2026, 10, 14, DayKind.NO_SCHOOL, null));
        // holidays with a name form their own range
        days.add(day(2026, 11, 2, DayKind.HOLIDAY, "Herbstferien"));
        days.add(day(2026, 11, 3, DayKind.HOLIDAY, "Herbstferien"));
        when(calendarRepository.findBetween(anyLong(), any(), any())).thenReturn(days);

        List<MonthView> months = service.loadCalendar(1L, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30));

        assertEquals(2, months.size());
        List<CalendarEntryView> october = months.getFirst().entries();
        assertEquals(2, october.size());
        assertTrue(october.get(0).schoolRange());
        assertEquals(LocalDate.of(2026, 10, 5), october.get(0).from());
        assertEquals(LocalDate.of(2026, 10, 13), october.get(0).to());
        assertEquals("Unterricht", october.get(0).text());
        assertEquals("SchA D", october.get(1).text());
        assertTrue(october.get(1).exam());

        CalendarEntryView autumn = months.get(1).entries().getFirst();
        assertEquals("Herbstferien", autumn.text());
        assertEquals(LocalDate.of(2026, 11, 3), autumn.to());
    }

    @Test
    void splitsLessonsAtBreaks()
    {
        // periods 1-4 of 45 minutes from 8:30, break 10:00-10:15 before period 3 (grid row 4)
        Map<Integer, PeriodSlotEntity> slots = Map.of(1, slot(1, 8, 30), 2, slot(2, 9, 15), 3, slot(3, 10, 15), 4, slot(4, 11, 0));
        Map<Integer, Integer> rows = Map.of(1, 1, 2, 2, 3, 4, 4, 5);
        Map<Integer, PeriodView> breaks = Map.of(3, new PeriodView(3, 4, true, "10:00", "10:15", "11:00"));
        List<LessonEntity> lessons = List.of(lesson(1, 1, "D"), lesson(2, 3, "M"), lesson(4, 4, "E"));

        List<LessonView> views = TimetableQueryService.dayViews(lessons, (subject, teacher) -> false, slots, rows, breaks);

        assertEquals(List.of("D", "M", "M", "E"), views.stream().map(LessonView::subject).toList());
        LessonView beforeBreak = views.get(1);
        assertEquals("9:15–10:00", beforeBreak.timeLabel());
        assertEquals(2, beforeBreak.rowTo());
        assertNull(beforeBreak.breakBefore());
        LessonView afterBreak = views.get(2);
        assertEquals(3, afterBreak.periodFrom());
        assertEquals(4, afterBreak.rowFrom());
        assertEquals("10:00–10:15", afterBreak.breakBefore());
        assertNull(views.get(3).breakBefore());
    }

    @Test
    void noBreakMarkerBeforeTheFirstLesson()
    {
        Map<Integer, PeriodSlotEntity> slots = Map.of(3, slot(3, 10, 15));
        Map<Integer, PeriodView> breaks = Map.of(3, new PeriodView(3, 4, true, "10:00", "10:15", "11:00"));

        List<LessonView> views = TimetableQueryService.dayViews(List.of(lesson(3, 3, "M")), (subject, teacher) -> false, slots, Map.of(3, 4), breaks);

        assertEquals(1, views.size());
        assertNull(views.getFirst().breakBefore());
    }

    @Test
    void parallelLessonsCloseTheGapOfAHiddenOne()
    {
        // periods 1-2: A | B (B hidden) -> A full width; periods 3-4: C | D | E, D hidden -> C and E halves
        LessonEntity a = lesson(1, 2, "A", 0, 2);
        LessonEntity b = lesson(1, 2, "B", 1, 2);
        LessonEntity c = lesson(3, 4, "C", 0, 3);
        LessonEntity d = lesson(3, 4, "D", 1, 3);
        LessonEntity e = lesson(3, 4, "E", 2, 3);
        // period 5: F | G, nothing hidden -> unchanged
        LessonEntity f = lesson(5, 5, "F", 0, 2);
        LessonEntity g = lesson(5, 5, "G", 1, 2);

        Map<LessonEntity, TimetableQueryService.Lane> lanes = TimetableQueryService.visibleLanes(List.of(a, b, c, d, e, f, g),
            (subject, teacher) -> "B".equals(subject) || "D".equals(subject));

        assertEquals(List.of(a, c, e, f, g), List.copyOf(lanes.keySet()));
        assertEquals(new TimetableQueryService.Lane(0, 1), lanes.get(a));
        assertEquals(new TimetableQueryService.Lane(0, 2), lanes.get(c));
        assertEquals(new TimetableQueryService.Lane(1, 2), lanes.get(e));
        assertEquals(new TimetableQueryService.Lane(0, 2), lanes.get(f));
        assertEquals(new TimetableQueryService.Lane(1, 2), lanes.get(g));
    }

    private static PeriodSlotEntity slot(int period, int hour, int minute)
    {
        PeriodSlotEntity slot = new PeriodSlotEntity();
        slot.setPeriod(period);
        slot.setStartTime(LocalTime.of(hour, minute));
        slot.setEndTime(LocalTime.of(hour, minute).plusMinutes(45));
        return slot;
    }

    private static LessonEntity lesson(int from, int to, String subject)
    {
        LessonEntity lesson = new LessonEntity();
        lesson.setPeriodFrom(from);
        lesson.setPeriodTo(to);
        lesson.setLaneCount(1);
        lesson.setSubject(subject);
        lesson.setStatus(LessonStatus.REGULAR);
        return lesson;
    }

    private static LessonEntity lesson(int from, int to, String subject, int lane, int laneCount)
    {
        LessonEntity lesson = lesson(from, to, subject);
        lesson.setLane(lane);
        lesson.setLaneCount(laneCount);
        return lesson;
    }

    private static CalendarDayEntity day(int year, int month, int dayOfMonth, DayKind kind, String text)
    {
        CalendarDayEntity entity = new CalendarDayEntity();
        entity.setDate(LocalDate.of(year, month, dayOfMonth));
        entity.setKind(kind);
        entity.setText(text);
        return entity;
    }
}
