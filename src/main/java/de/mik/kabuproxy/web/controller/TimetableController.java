package de.mik.kabuproxy.web.controller;

import de.mik.kabuproxy.config.KabuConfig;
import de.mik.kabuproxy.security.UserSession;
import de.mik.kabuproxy.service.TimetableQueryService;
import de.mik.kabuproxy.service.UserService;
import de.mik.kabuproxy.web.model.ChangeView;
import de.mik.kabuproxy.web.model.WeekView;
import lombok.Getter;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Named
@RequestScoped
public class TimetableController
{
    @Inject private UserSession userSession;
    @Inject private StatusController status;
    @Inject private TimetableQueryService queryService;
    @Inject private UserService userService;
    @Inject private ThemeController theme;

    @Getter private WeekView week;
    @Getter private List<ChangeView> changes = List.of();
    @Getter private LocalDate thisWeek;

    @PostConstruct
    void init()
    {
        thisWeek = TimetableQueryService.defaultMonday(LocalDate.now(KabuConfig.ZONE));
        if (!status.isHasClass())
        {
            return;
        }
        long classId = status.getAccount().classId();
        week = queryService.loadWeek(classId, requestedMonday(), theme.getSettings()::lessonHidden);
        changes = queryService.changesSince(classId, userService.changesSeenAt(userSession.getUserId()));
    }

    public boolean isCurrentWeek()
    {
        return week != null && week.monday().equals(thisWeek);
    }

    public String markSeen()
    {
        userService.markChangesSeen(userSession.getUserId());
        return "stundenplan?faces-redirect=true&week=" + (week == null ? thisWeek : week.monday());
    }

    private LocalDate requestedMonday()
    {
        String param = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("week");
        if (param == null || param.isBlank())
        {
            return thisWeek;
        }
        try
        {
            return LocalDate.parse(param).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        catch (DateTimeParseException e)
        {
            return thisWeek;
        }
    }
}
