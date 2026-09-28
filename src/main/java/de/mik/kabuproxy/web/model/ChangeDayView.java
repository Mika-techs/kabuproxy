package de.mik.kabuproxy.web.model;

import java.util.List;

/**
 * The changes to the lessons of one day.
 */
public record ChangeDayView(String title, boolean past, boolean today, List<ChangeView> changes)
{
    public String cssClass()
    {
        return "changeday" + (past ? " changeday--past" : "") + (today ? " changeday--today" : "");
    }
}
