package de.mik.kabuproxy.digikabu.parser;

import de.mik.kabuproxy.Fixtures;
import de.mik.kabuproxy.digikabu.DigikabuException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbsenceParserTest
{
    @Test
    void parsesSummaryAndDetails() throws Exception
    {
        ParsedAbsences absences = AbsenceParser.parse(Fixtures.load("absences.html"));

        assertEquals(1, absences.fullDays());
        assertEquals(1, absences.fullDaysUnexcused());
        assertEquals(0, absences.hours());
        assertEquals(0, absences.hoursUnexcused());
        assertEquals(1, absences.entries().size());
        assertEquals(new ParsedAbsence(LocalDate.of(2026, 9, 23), "Anfang", "Ende", "Arzttermin", "Fehlzeit", null), absences.entries().getFirst());
    }

    @Test
    void acceptsUsDates() throws Exception
    {
        String html = Fixtures.load("absences.html").replace("<td>23.09.2026</td>", "<td>09/23/2026</td>");
        assertEquals(LocalDate.of(2026, 9, 23), AbsenceParser.parse(html).entries().getFirst().date());
    }

    @Test
    void keepsAbsenceReasonOutOfDateError() throws Exception
    {
        String html = Fixtures.load("absences.html").replace("<td>23.09.2026</td>", "<td>2026-09-23</td>");
        DigikabuException.ParseFailed e = assertThrows(DigikabuException.ParseFailed.class, () -> AbsenceParser.parse(html));
        assertFalse(e.getMessage().contains("Arzttermin"));
    }

    @Test
    void recognisesCheckMarkAsExcused() throws Exception
    {
        ParsedAbsences absences = AbsenceParser.parse(Fixtures.load("absences_excused.html"));

        assertEquals(1, absences.fullDays());
        assertEquals(0, absences.fullDaysUnexcused());
        assertEquals(ParsedAbsence.EXCUSED_MARK, absences.entries().getFirst().excused());
    }

    @Test
    void acceptsMissingDetailsWhenThereAreNoAbsences() throws Exception
    {
        ParsedAbsences absences = AbsenceParser.parse(Fixtures.load("absences_none.html"));

        assertEquals(0, absences.fullDays());
        assertEquals(0, absences.hours());
        assertTrue(absences.entries().isEmpty());
    }

    @Test
    void rejectsMissingDetailsWhenAbsencesAreCounted()
    {
        assertThrows(DigikabuException.ParseFailed.class, () -> AbsenceParser.parse(withoutDetails("1")));
    }

    @Test
    void rejectsPageWithoutSummary()
    {
        assertThrows(DigikabuException.ParseFailed.class, () -> AbsenceParser.parse("<html><body><table class='table-striped'></table></body></html>"));
    }

    private static String withoutDetails(String fullDays) throws Exception
    {
        String html = Fixtures.load("absences.html")
            .replace("bold\">1</span> (davon 1 unentschuldigt)", "bold\">" + fullDays + "</span>");
        int start = html.indexOf("<h3>Details</h3>");
        int end = html.indexOf("</table>", start) + "</table>".length();
        return html.substring(0, start) + html.substring(end);
    }
}
