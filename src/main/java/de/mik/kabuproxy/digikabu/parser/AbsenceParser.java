package de.mik.kabuproxy.digikabu.parser;

import de.mik.kabuproxy.digikabu.DigikabuException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code GET /Fehlzeiten}: a summary ("Ganztags: 1 (davon 1 unentschuldigt)") and a details table (missing
 * when there are no absences).
 */
public final class AbsenceParser
{
    private static final Pattern SUMMARY = Pattern.compile("^(\\d+)\\s*(?:\\(davon\\s+(\\d+)\\s+unentschuldigt\\))?");
    /**
     * digikabu used German dates and switched to US-style ones in November 2026; accept both.
     */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("[dd.MM.yyyy][MM/dd/yyyy]");
    private static final int DETAIL_COLUMNS = 6;
    private static final int COL_DATE = 0;
    private static final int COL_FROM = 1;
    private static final int COL_TO = 2;
    private static final int COL_REMARK = 3;
    private static final int COL_KIND = 4;
    private static final int COL_EXCUSED = 5;

    private AbsenceParser()
    {
    }

    public static ParsedAbsences parse(String html) throws DigikabuException.ParseFailed
    {
        Document doc = Jsoup.parse(html);
        int[] fullDays = null;
        int[] hours = null;
        for (Element row : doc.select("table tr"))
        {
            Elements cells = row.select("> td");
            if (cells.size() != 2)
            {
                continue;
            }
            String label = cells.get(0).text().trim();
            if (label.startsWith("Ganztags"))
            {
                fullDays = parseSummary(cells.get(1).text(), html);
            }
            else if (label.startsWith("Stundenweise"))
            {
                hours = parseSummary(cells.get(1).text(), html);
            }
        }
        if (fullDays == null || hours == null)
        {
            throw new DigikabuException.ParseFailed("absence summary not found", null, html);
        }

        Element details = doc.selectFirst("table.table-striped");
        if (details == null && fullDays[0] == 0 && hours[0] == 0)
        {
            // digikabu leaves out the details table when there is nothing to list
            return new ParsedAbsences(0, 0, 0, 0, List.of());
        }
        if (details == null)
        {
            throw new DigikabuException.ParseFailed("absence details table missing", null, html);
        }
        List<ParsedAbsence> entries = new ArrayList<>();
        for (Element row : details.select("tbody tr"))
        {
            Elements cells = row.select("> td");
            if (cells.size() < DETAIL_COLUMNS)
            {
                continue;
            }
            LocalDate date;
            try
            {
                date = LocalDate.parse(cells.get(COL_DATE).text().trim(), DATE);
            }
            catch (DateTimeParseException e)
            {
                // only the date cell - the row holds the absence reason, which must not reach logs or admins
                throw new DigikabuException.ParseFailed("absence date not understood: " + cells.get(COL_DATE).text(), null, html);
            }
            entries.add(new ParsedAbsence(
                date,
                blankToNull(cells.get(COL_FROM).text()),
                blankToNull(cells.get(COL_TO).text()),
                blankToNull(cells.get(COL_REMARK).text()),
                blankToNull(cells.get(COL_KIND).text()),
                excused(cells.get(COL_EXCUSED))));
        }
        return new ParsedAbsences(fullDays[0], fullDays[1], hours[0], hours[1], entries);
    }

    private static int[] parseSummary(String text, String html) throws DigikabuException.ParseFailed
    {
        Matcher matcher = SUMMARY.matcher(text.trim());
        if (!matcher.find())
        {
            throw new DigikabuException.ParseFailed("absence summary not understood: " + text, null, html);
        }
        int total = Integer.parseInt(matcher.group(1));
        int unexcused = matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2));
        return new int[] {total, unexcused};
    }

    /**
     * The excused column holds either text or just a check-mark icon ({@code glyphicon-ok}) without any text.
     */
    private static String excused(Element cell)
    {
        String text = blankToNull(cell.text());
        if (text == null && cell.selectFirst(".glyphicon-ok") != null)
        {
            return ParsedAbsence.EXCUSED_MARK;
        }
        return text;
    }

    private static String blankToNull(String value)
    {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
