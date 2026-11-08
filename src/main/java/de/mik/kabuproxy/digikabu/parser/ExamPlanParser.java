package de.mik.kabuproxy.digikabu.parser;

import de.mik.kabuproxy.digikabu.DigikabuException;
import de.mik.kabuproxy.persistence.entities.DayKind;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code GET /SchulaufgabenPlan}: one table row per calendar day, grouped by "September 2026" month headers.
 * Row class {@code feiertag} = holiday, {@code keinunterr} = no school for this class ("***").
 * digikabu sends "März" as {@code M&#xFFFD;rz}, so U+FFFD in a month name matches any letter.
 */
public final class ExamPlanParser
{
    private static final Pattern MONTH_HEADER = Pattern.compile("^([\\p{L}\\uFFFD]+)\\s+(\\d{4})$");
    private static final Pattern DAY = Pattern.compile("^(\\d{1,2})\\.(\\d{1,2})\\.?$");
    private static final char REPLACEMENT_CHAR = '\uFFFD';
    private static final String NO_SCHOOL_PLACEHOLDER = "***";
    private static final Map<String, Month> MONTHS = Map.ofEntries(
        Map.entry("januar", Month.JANUARY),
        Map.entry("februar", Month.FEBRUARY),
        Map.entry("märz", Month.MARCH),
        Map.entry("april", Month.APRIL),
        Map.entry("mai", Month.MAY),
        Map.entry("juni", Month.JUNE),
        Map.entry("juli", Month.JULY),
        Map.entry("august", Month.AUGUST),
        Map.entry("september", Month.SEPTEMBER),
        Map.entry("oktober", Month.OCTOBER),
        Map.entry("november", Month.NOVEMBER),
        Map.entry("dezember", Month.DECEMBER));

    private ExamPlanParser()
    {
    }

    public static List<ParsedCalendarDay> parse(String html) throws DigikabuException.ParseFailed
    {
        Document doc = Jsoup.parse(html);
        Elements rows = doc.select("table.saplan tr");
        if (rows.isEmpty())
        {
            throw new DigikabuException.ParseFailed("exam plan table.saplan missing", null, html);
        }

        List<ParsedCalendarDay> days = new ArrayList<>();
        Integer year = null;
        for (Element row : rows)
        {
            Element monthHeader = row.selectFirst("h4");
            if (monthHeader != null)
            {
                year = parseYear(monthHeader.text(), html);
                continue;
            }
            Elements cells = row.select("> td");
            if (cells.size() < 3)
            {
                continue;
            }
            Matcher day = DAY.matcher(cells.get(0).text().trim());
            if (!day.matches() || year == null)
            {
                throw new DigikabuException.ParseFailed("exam plan row not understood: " + row.text(), null, html);
            }
            LocalDate date;
            try
            {
                date = LocalDate.of(year, Integer.parseInt(day.group(2)), Integer.parseInt(day.group(1)));
            }
            catch (DateTimeException e)
            {
                throw new DigikabuException.ParseFailed("invalid exam plan date: " + row.text(), null, html);
            }
            String text = cells.get(2).text().trim();
            if (NO_SCHOOL_PLACEHOLDER.equals(text) || text.isEmpty())
            {
                text = null;
            }
            days.add(new ParsedCalendarDay(date, kindOf(row), text));
        }
        return days;
    }

    private static int parseYear(String header, String html) throws DigikabuException.ParseFailed
    {
        Matcher matcher = MONTH_HEADER.matcher(header.trim());
        if (!matcher.matches() || !isMonthName(matcher.group(1).toLowerCase(Locale.GERMAN)))
        {
            throw new DigikabuException.ParseFailed("exam plan month header not understood: " + header, null, html);
        }
        return Integer.parseInt(matcher.group(2));
    }

    private static boolean isMonthName(String name)
    {
        return MONTHS.keySet().stream().anyMatch(month -> matchesIgnoringReplacementChars(month, name));
    }

    private static boolean matchesIgnoringReplacementChars(String expected, String actual)
    {
        if (expected.length() != actual.length())
        {
            return false;
        }
        for (int i = 0; i < expected.length(); i++)
        {
            if (actual.charAt(i) != REPLACEMENT_CHAR && actual.charAt(i) != expected.charAt(i))
            {
                return false;
            }
        }
        return true;
    }

    private static DayKind kindOf(Element row)
    {
        if (row.hasClass("feiertag"))
        {
            return DayKind.HOLIDAY;
        }
        if (row.hasClass("keinunterr"))
        {
            return DayKind.NO_SCHOOL;
        }
        return DayKind.SCHOOL;
    }
}
