package de.mik.kabuproxy.web.controller;

import de.mik.kabuproxy.security.UserSession;
import de.mik.kabuproxy.service.TimetableQueryService;
import de.mik.kabuproxy.service.UserService;
import de.mik.kabuproxy.web.model.ChangeDayView;
import lombok.Getter;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.RequestScoped;
import jakarta.faces.context.FacesContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.List;

@Named
@RequestScoped
public class ChangesController
{
    @Inject private UserSession userSession;
    @Inject private StatusController status;
    @Inject private TimetableQueryService queryService;
    @Inject private UserService userService;

    @Getter private List<ChangeDayView> days = List.of();
    @Getter private boolean showPast;
    @Getter private boolean hasUnseen;

    @PostConstruct
    void init()
    {
        showPast = "1".equals(FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap().get("past"));
        if (!status.isHasClass())
        {
            return;
        }
        days = queryService.changeLog(status.getAccount().classId(), showPast, userService.changesSeenAt(userSession.getUserId()));
        hasUnseen = days.stream().flatMap(d -> d.changes().stream()).anyMatch(c -> c.unseen());
    }

    public String markSeen()
    {
        userService.markChangesSeen(userSession.getUserId());
        return "aenderungen?faces-redirect=true" + (showPast ? "&past=1" : "");
    }
}
