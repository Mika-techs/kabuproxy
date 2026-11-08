package de.mik.kabuproxy.digikabu.parser;

import de.mik.kabuproxy.Fixtures;
import de.mik.kabuproxy.digikabu.DigikabuException;
import de.mik.kabuproxy.persistence.entities.DayKind;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamPlanParserTest
{
    @Test
    void parsesKindsTextsAndYearRollover() throws Exception
    {
        List<ParsedCalendarDay> days = ExamPlanParser.parse(Fixtures.load("examplan.html"));
        Map<LocalDate, ParsedCalendarDay> byDate = days.stream().collect(Collectors.toMap(ParsedCalendarDay::date, Function.identity()));

        ParsedCalendarDay holiday = byDate.get(LocalDate.of(2026, 9, 1));
        assertEquals(DayKind.HOLIDAY, holiday.kind());
        assertEquals("Sommerferien", holiday.text());

        ParsedCalendarDay noSchool = byDate.get(LocalDate.of(2026, 9, 14));
        assertEquals(DayKind.NO_SCHOOL, noSchool.kind());
        assertNull(noSchool.text());

        ParsedCalendarDay exam = byDate.get(LocalDate.of(2026, 10, 1));
        assertEquals(DayKind.SCHOOL, exam.kind());
        assertEquals("SchA ITP ANT HECS", exam.text());

        assertEquals(DayKind.SCHOOL, byDate.get(LocalDate.of(2026, 9, 24)).kind());
        assertTrue(byDate.keySet().stream().anyMatch(d -> d.getYear() == 2027));
        assertEquals(days.size(), byDate.size());
    }

    @Test
    void acceptsMonthWithReplacementCharacter() throws Exception
    {
        String html = "<table class=\"saplan\">"
            + "<tr><td colspan=\"3\"><h4>M&#xFFFD;rz 2027</h4></td></tr>"
            + "<tr><td>1.3.</td><td>Mo</td><td>SchA ITP</td></tr>"
            + "</table>";
        List<ParsedCalendarDay> days = ExamPlanParser.parse(html);
        assertEquals(LocalDate.of(2027, 3, 1), days.get(0).date());
        assertEquals("SchA ITP", days.get(0).text());
    }

    @Test
    void rejectsUnknownMonthHeader()
    {
        String html = "<table class=\"saplan\"><tr><td colspan=\"3\"><h4>Foo 2027</h4></td></tr></table>";
        assertThrows(DigikabuException.ParseFailed.class, () -> ExamPlanParser.parse(html));
    }

    @Test
    void rejectsPageWithoutTable()
    {
        assertThrows(DigikabuException.ParseFailed.class, () -> ExamPlanParser.parse("<html><body>Wartung</body></html>"));
    }
}
