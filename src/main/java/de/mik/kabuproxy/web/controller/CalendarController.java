package de.mik.kabuproxy.web.controller;

import de.mik.kabuproxy.config.KabuConfig;
import de.mik.kabuproxy.service.TimetableQueryService;
import de.mik.kabuproxy.web.model.CalendarEntryView;
import de.mik.kabuproxy.web.model.MonthView;
import lombok.Getter;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.time.LocalDate;
import java.util.List;

@Named
@RequestScoped
public class CalendarController
{
    private static final int PAST_DAYS_DEFAULT = 7;
    private static final int PAST_DAYS_ALL = 366;
    private static final int FUTURE_DAYS = 400;

    @Inject private StatusController status;
    @Inject private TimetableQueryService queryService;

    @Getter private List<MonthView> months = List.of();
    @Getter private List<CalendarEntryView> upcomingExams = List.of();
    @Getter private boolean showPast;

    @PostConstruct
    void init()
    {
        showPast = "1".equals(FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("past"));
        if (!status.isHasClass())
        {
            return;
        }
        LocalDate today = LocalDate.now(KabuConfig.ZONE);
        LocalDate from = today.minusDays(showPast ? PAST_DAYS_ALL : PAST_DAYS_DEFAULT);
        months = queryService.loadCalendar(status.getAccount().classId(), from, today.plusDays(FUTURE_DAYS));
        upcomingExams = months.stream()
            .flatMap(m -> m.entries().stream())
            .filter(e -> e.exam() && !e.past())
            .toList();
    }
}
