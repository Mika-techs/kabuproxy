package de.mik.kabuproxy.web.model;

import de.mik.kabuproxy.persistence.entities.ChangeType;
import de.mik.kabuproxy.web.I18n;

import java.util.Locale;

/**
 * One detected lesson change; {@code unseen} = detected after the user last clicked "seen".
 */
public record ChangeView(String dayLabel, String periodLabel, ChangeType type, String before, String after, String detectedLabel, boolean unseen)
{
    public String typeLabel()
    {
        return I18n.text("change." + type.name());
    }

    public String cssClass()
    {
        return "change change--" + type.name().toLowerCase(Locale.ROOT) + (unseen ? " change--unseen" : "");
    }
}
