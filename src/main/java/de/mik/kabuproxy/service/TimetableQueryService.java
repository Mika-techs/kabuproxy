package de.mik.kabuproxy.service;

import de.mik.kabuproxy.config.KabuConfig;
import de.mik.kabuproxy.persistence.entities.CalendarDayEntity;
import de.mik.kabuproxy.persistence.entities.DayKind;
import de.mik.kabuproxy.persistence.entities.LessonChangeEntity;
import de.mik.kabuproxy.persistence.entities.LessonEntity;
import de.mik.kabuproxy.persistence.entities.PeriodSlotEntity;
import de.mik.kabuproxy.persistence.repository.CalendarRepository;
import de.mik.kabuproxy.persistence.repository.LessonRepository;
import de.mik.kabuproxy.persistence.repository.SchoolClassRepository;
import de.mik.kabuproxy.web.I18n;
import de.mik.kabuproxy.web.model.CalendarEntryView;
import de.mik.kabuproxy.web.model.ChangeDayView;
import de.mik.kabuproxy.web.model.ChangeView;
import de.mik.kabuproxy.web.model.DayView;
import de.mik.kabuproxy.web.model.Formats;
import de.mik.kabuproxy.web.model.LessonView;
import de.mik.kabuproxy.web.model.MonthView;
import de.mik.kabuproxy.web.model.PeriodView;
import de.mik.kabuproxy.web.model.WeekView;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

@ApplicationScoped
public class TimetableQueryService
{
    private static final int SCHOOL_DAYS = 5;
    private static final int DEFAULT_PERIODS = 10;

    @Inject private SchoolClassRepository schoolClassRepository;
    @Inject private LessonRepository lessonRepository;
    @Inject private CalendarRepository calendarRepository;

    /**
     * Monday of the week to show by default: this week, or next week on weekends.
     */
    public static LocalDate defaultMonday(LocalDate today)
    {
        if (today.getDayOfWeek() == DayOfWeek.SATURDAY || today.getDayOfWeek() == DayOfWeek.SUNDAY)
        {
            return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        }
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    @Transactional
    public WeekView loadWeek(long classId, LocalDate monday)
    {
        LocalDate friday = monday.plusDays(SCHOOL_DAYS - 1);
        LocalDate today = LocalDate.now(KabuConfig.ZONE);

        List<PeriodSlotEntity> slots = schoolClassRepository.findPeriods(classId);
        Map<Integer, PeriodSlotEntity> slotByPeriod = slots.stream().collect(Collectors.toMap(PeriodSlotEntity::getPeriod, s -> s));

        Map<LocalDate, List<LessonEntity>> lessonsByDay = lessonRepository.findBetween(classId, monday, friday).stream()
            .collect(Collectors.groupingBy(LessonEntity::getDate));
        Map<LocalDate, CalendarDayEntity> calendar = calendarRepository.findBetween(classId, monday, friday).stream()
            .collect(Collectors.toMap(CalendarDayEntity::getDate, d -> d));

        int maxPeriod = lessonsByDay.values().stream().flatMap(List::stream).mapToInt(LessonEntity::getPeriodTo).max().orElse(0);
        int periodCount = Math.max(maxPeriod, slots.isEmpty() ? DEFAULT_PERIODS : slots.getLast().getPeriod());

        List<PeriodView> periods = new ArrayList<>();
        Map<Integer, Integer> rowByPeriod = new HashMap<>();
        int row = 0;
        for (int period = 1; period <= periodCount; period++)
        {
            PeriodSlotEntity slot = slotByPeriod.get(period);
            PeriodSlotEntity previous = slotByPeriod.get(period - 1);
            // a gap between two periods is a break - it gets its own (small) grid row
            boolean breakBefore = slot != null && previous != null && slot.getStartTime().isAfter(previous.getEndTime());
            row += breakBefore ? 2 : 1;
            rowByPeriod.put(period, row);
            periods.add(new PeriodView(period, row, breakBefore, breakBefore ? Formats.time(previous.getEndTime()) : "",
                slot == null ? "" : Formats.time(slot.getStartTime()), slot == null ? "" : Formats.time(slot.getEndTime())));
        }
        Map<Integer, PeriodView> breaks = periods.stream().filter(PeriodView::breakBefore)
            .collect(Collectors.toMap(PeriodView::period, p -> p));

        List<DayView> days = new ArrayList<>();
        boolean hasLessons = false;
        for (int i = 0; i < SCHOOL_DAYS; i++)
        {
            LocalDate date = monday.plusDays(i);
            List<LessonView> lessons = dayViews(lessonsByDay.getOrDefault(date, List.of()), slotByPeriod, rowByPeriod, breaks);
            hasLessons |= !lessons.isEmpty();
            CalendarDayEntity calendarDay = calendar.get(date);
            days.add(new DayView(date, date.equals(today), lessons,
                calendarDay == null ? null : calendarDay.getKind(),
                calendarDay == null ? null : calendarDay.getText()));
        }
        return new WeekView(monday, periods, days, hasLessons);
    }

    /**
     * The class's subjects with the teachers of their regular lessons, both sorted for display.
     */
    @Transactional
    public Map<String, List<String>> subjectTeachers(long classId)
    {
        Map<String, TreeSet<String>> teachers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        lessonRepository.findSubjects(classId).stream()
            .map(String::trim)
            .filter(subject -> !subject.isEmpty())
            .forEach(subject -> teachers.computeIfAbsent(subject, k -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
        for (Object[] row : lessonRepository.findRegularTeachers(classId))
        {
            String teacher = ((String) row[1]).trim();
            TreeSet<String> subjectTeachers = teachers.get(((String) row[0]).trim());
            if (subjectTeachers != null && !teacher.isEmpty())
            {
                subjectTeachers.add(teacher);
            }
        }
        Map<String, List<String>> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        teachers.forEach((subject, names) -> result.put(subject, List.copyOf(names)));
        return result;
    }

    /**
     * Changes to today's or future lessons detected after {@code since}.
     */
    @Transactional
    public List<ChangeView> changesSince(long classId, Instant since)
    {
        if (since == null)
        {
            return List.of();
        }
        LocalDate today = LocalDate.now(KabuConfig.ZONE);
        Map<Integer, PeriodSlotEntity> slots = periodSlots(classId);
        return lessonRepository.findChanges(classId, since, today).stream()
            .map(change -> changeView(change, slots, true))
            .toList();
    }

    /**
     * The class's change log grouped by lesson day: from today on, or with {@code includePast} everything (latest days
     * first). Changes detected after {@code seenAt} are marked unseen (none while the user never marked any as seen,
     * like the timetable's box).
     */
    @Transactional
    public List<ChangeDayView> changeLog(long classId, boolean includePast, Instant seenAt)
    {
        LocalDate today = LocalDate.now(KabuConfig.ZONE);
        Map<Integer, PeriodSlotEntity> slots = periodSlots(classId);
        Map<LocalDate, List<ChangeView>> byDay = new LinkedHashMap<>();
        for (LessonChangeEntity change : lessonRepository.findChangeLog(classId, includePast ? null : today))
        {
            boolean unseen = seenAt != null && change.getDetectedAt().isAfter(seenAt);
            byDay.computeIfAbsent(change.getDate(), k -> new ArrayList<>()).add(changeView(change, slots, unseen));
        }
        return byDay.entrySet().stream()
            .map(e -> new ChangeDayView(Formats.weekdayLong(e.getKey()) + ", " + Formats.date(e.getKey()), e.getKey().isBefore(today),
                e.getKey().equals(today), e.getValue()))
            .toList();
    }

    private Map<Integer, PeriodSlotEntity> periodSlots(long classId)
    {
        return schoolClassRepository.findPeriods(classId).stream()
            .collect(Collectors.toMap(PeriodSlotEntity::getPeriod, s -> s));
    }

    private static ChangeView changeView(LessonChangeEntity change, Map<Integer, PeriodSlotEntity> slots, boolean unseen)
    {
        String period = change.getPeriodFrom() == change.getPeriodTo()
            ? I18n.text("timetable.period", change.getPeriodFrom())
            : I18n.text("timetable.periods", change.getPeriodFrom(), change.getPeriodTo());
        PeriodSlotEntity slot = slots.get(change.getPeriodFrom());
        if (slot != null)
        {
            period += " · " + Formats.time(slot.getStartTime());
        }
        return new ChangeView(Formats.weekdayShort(change.getDate()) + " " + Formats.dayMonth(change.getDate()), period, change.getChangeType(),
            change.getBeforeText(), change.getAfterText(), Formats.relative(change.getDetectedAt()), unseen);
    }

    /**
     * Calendar grouped by month; consecutive school days are merged into "school" ranges (days with text are listed
     * on their own as well), holidays and named no-school stretches into ranges, plain no-school days are left out.
     */
    @Transactional
    public List<MonthView> loadCalendar(long classId, LocalDate from, LocalDate to)
    {
        LocalDate today = LocalDate.now(KabuConfig.ZONE);
        List<CalendarEntryView> entries = new ArrayList<>();
        CalendarDayEntity blockStart = null;
        CalendarDayEntity blockEnd = null;
        CalendarDayEntity rangeStart = null;
        CalendarDayEntity rangeEnd = null;
        for (CalendarDayEntity day : calendarRepository.findBetween(classId, from, to))
        {
            // weekends are noise (always "free") - skip them, and let ranges continue across them
            if (isWeekend(day.getDate()))
            {
                continue;
            }
            if (day.getKind() == DayKind.SCHOOL)
            {
                if (rangeStart != null)
                {
                    entries.add(entry(rangeStart, rangeEnd, today, false));
                    rangeStart = null;
                }
                if (blockStart != null && !day.getDate().equals(nextSchoolDay(blockEnd.getDate())))
                {
                    entries.add(entry(blockStart, blockEnd, today, true));
                    blockStart = null;
                }
                if (blockStart == null)
                {
                    blockStart = day;
                }
                blockEnd = day;
                if (day.getText() != null)
                {
                    entries.add(entry(day, day, today, false));
                }
                continue;
            }
            if (blockStart != null)
            {
                entries.add(entry(blockStart, blockEnd, today, true));
                blockStart = null;
            }
            if (rangeStart != null && day.getKind() == rangeStart.getKind() && Objects.equals(day.getText(), rangeStart.getText())
                && day.getDate().equals(nextSchoolDay(rangeEnd.getDate())))
            {
                rangeEnd = day;
                continue;
            }
            if (rangeStart != null)
            {
                entries.add(entry(rangeStart, rangeEnd, today, false));
                rangeStart = null;
            }
            // a plain no-school day is just the gap between two school ranges
            if (day.getKind() == DayKind.HOLIDAY || day.getText() != null)
            {
                rangeStart = day;
                rangeEnd = day;
            }
        }
        if (blockStart != null)
        {
            entries.add(entry(blockStart, blockEnd, today, true));
        }
        if (rangeStart != null)
        {
            entries.add(entry(rangeStart, rangeEnd, today, false));
        }
        // a school range is added when it ends - move it in front of the days listed within it
        entries.sort(Comparator.comparing(CalendarEntryView::from).thenComparing(e -> !e.schoolRange()));

        Map<String, List<CalendarEntryView>> byMonth = new LinkedHashMap<>();
        for (CalendarEntryView entry : entries)
        {
            String title = Formats.monthYear(entry.from());
            byMonth.computeIfAbsent(title, k -> new ArrayList<>()).add(entry);
        }
        return byMonth.entrySet().stream().map(e -> new MonthView(e.getKey(), e.getValue())).toList();
    }

    private static boolean isWeekend(LocalDate date)
    {
        return date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static LocalDate nextSchoolDay(LocalDate date)
    {
        LocalDate next = date.plusDays(1);
        while (isWeekend(next))
        {
            next = next.plusDays(1);
        }
        return next;
    }

    private static CalendarEntryView entry(CalendarDayEntity start, CalendarDayEntity end, LocalDate today, boolean schoolRange)
    {
        boolean containsToday = !today.isBefore(start.getDate()) && !today.isAfter(end.getDate());
        String text = schoolRange ? I18n.text("day.school") : start.getText();
        return new CalendarEntryView(start.getDate(), end.getDate(), start.getKind(), text, schoolRange, end.getDate().isBefore(today), containsToday);
    }

    /**
     * The day's lessons, split at breaks so every part gets its own box, time and countdown; ordered by period again.
     */
    static List<LessonView> dayViews(List<LessonEntity> lessons, Map<Integer, PeriodSlotEntity> slots, Map<Integer, Integer> rowByPeriod,
        Map<Integer, PeriodView> breaks)
    {
        List<Part> parts = new ArrayList<>();
        for (LessonEntity lesson : lessons)
        {
            int from = lesson.getPeriodFrom();
            for (int period = from + 1; period <= lesson.getPeriodTo(); period++)
            {
                if (breaks.containsKey(period))
                {
                    parts.add(new Part(lesson, from, period - 1));
                    from = period;
                }
            }
            parts.add(new Part(lesson, from, lesson.getPeriodTo()));
        }
        // stable: parallel lessons keep their lane order
        parts.sort(Comparator.comparingInt(Part::from));

        List<LessonView> views = new ArrayList<>();
        int lastPeriod = 0;
        for (Part part : parts)
        {
            // the mobile list shows a break between the last lesson before it and the first one after it
            String breakBefore = null;
            for (int period = lastPeriod + 1; lastPeriod > 0 && period <= part.from(); period++)
            {
                if (breaks.containsKey(period))
                {
                    breakBefore = breaks.get(period).breakLabel();
                }
            }
            lastPeriod = Math.max(lastPeriod, part.to());
            views.add(toView(part, slots, rowByPeriod, breakBefore));
        }
        return views;
    }

    private static LessonView toView(Part part, Map<Integer, PeriodSlotEntity> slots, Map<Integer, Integer> rowByPeriod, String breakBefore)
    {
        LessonEntity lesson = part.lesson();
        PeriodSlotEntity first = slots.get(part.from());
        PeriodSlotEntity last = slots.get(part.to());
        String time = first == null || last == null ? "" : Formats.time(first.getStartTime()) + "–" + Formats.time(last.getEndTime());
        return new LessonView(part.from(), part.to(), rowByPeriod.getOrDefault(part.from(), part.from()),
            rowByPeriod.getOrDefault(part.to(), part.to()), lesson.getLane(), lesson.getLaneCount(), lesson.getSubject(), lesson.getTeacher(),
            lesson.getRoom(), lesson.getStatus(), lesson.getHint(), lesson.getNote(), time, breakBefore);
    }

    /**
     * The periods {@code from}..{@code to} of a lesson, between two breaks.
     */
    private record Part(LessonEntity lesson, int from, int to)
    {
    }
}
