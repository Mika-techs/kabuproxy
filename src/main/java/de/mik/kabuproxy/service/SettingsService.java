package de.mik.kabuproxy.service;

import de.mik.kabuproxy.persistence.entities.ThemeMode;
import de.mik.kabuproxy.persistence.entities.UserSettingsEntity;
import de.mik.kabuproxy.persistence.repository.SettingsRepository;
import de.mik.kabuproxy.web.model.UserSettings;
import org.apache.logging.log4j.Logger;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;

@ApplicationScoped
public class SettingsService
{
    @Inject private Logger logger;
    @Inject private SettingsRepository settingsRepository;

    @Transactional
    public UserSettings load(long userId)
    {
        return settingsRepository.findByUserId(userId)
            .map(s -> new UserSettings(s.getThemeMode(), s.getAccentColor(), s.getColors(), s.getLessonColors(), s.getLessonNames(),
                s.getHiddenLessons()))
            .orElse(UserSettings.DEFAULT);
    }

    @Transactional
    public void save(long userId, UserSettings settings)
    {
        UserSettingsEntity entity = settingsRepository.findByUserId(userId).orElse(null);
        boolean created = entity == null;
        if (created)
        {
            entity = new UserSettingsEntity();
            entity.setUserId(userId);
        }
        entity.setThemeMode(settings.themeMode());
        entity.setAccentColor(settings.accentColor());
        entity.getColors().clear();
        entity.getColors().putAll(settings.colors());
        entity.getLessonColors().clear();
        entity.getLessonColors().putAll(settings.lessonColors());
        entity.getLessonNames().clear();
        entity.getLessonNames().putAll(settings.lessonNames());
        entity.getHiddenLessons().clear();
        entity.getHiddenLessons().addAll(settings.hiddenLessons());
        entity.setUpdatedAt(Instant.now());
        if (created)
        {
            settingsRepository.persist(entity);
        }
        logger.debug("settings of user {} saved: {}", userId, settings);
    }

    @Transactional
    public void saveThemeMode(long userId, ThemeMode themeMode)
    {
        save(userId, load(userId).withThemeMode(themeMode));
    }
}
