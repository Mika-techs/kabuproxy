package de.mik.kabuproxy.persistence.entities;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Per-user UI preferences; a missing row means all defaults.
 */
@Getter
@Setter
@Entity
@Table(name = "user_settings")
public class UserSettingsEntity
{
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_mode", nullable = false)
    private ThemeMode themeMode;

    @Column(name = "accent_color")
    private String accentColor;

    /**
     * {@code ThemeColor} key (e.g. {@code dark-bg}) → {@code #rrggbb}; only colours that differ from the built-ins.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_theme_color", joinColumns = @JoinColumn(name = "user_id"))
    @MapKeyColumn(name = "color_key")
    @Column(name = "color", nullable = false)
    private Map<String, String> colors = new HashMap<>();

    /**
     * Subject (+ teacher) → {@code #rrggbb}; lessons without an entry use the subject's colour, then the accent.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_lesson_color", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "color", nullable = false)
    private Map<LessonKey, String> lessonColors = new HashMap<>();

    /**
     * Subject (+ teacher) → name shown instead of the subject; lazy, so loading the settings doesn't join three maps.
     */
    @ElementCollection
    @CollectionTable(name = "user_lesson_name", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "display_name", nullable = false)
    private Map<LessonKey, String> lessonNames = new HashMap<>();

    /**
     * Subjects (+ teachers) left out of the timetable; lazy like the names.
     */
    @ElementCollection
    @CollectionTable(name = "user_lesson_hidden", joinColumns = @JoinColumn(name = "user_id"))
    private Set<LessonKey> hiddenLessons = new HashSet<>();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
