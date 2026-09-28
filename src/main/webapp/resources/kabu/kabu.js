(function () {
    'use strict';

    // ---- theme toggle: flips light/dark right away and stores it in the user's settings ----
    var root = document.documentElement;
    var modeField = document.getElementById('settings:mode');
    var toggle = document.getElementById('theme-toggle');
    if (toggle) {
        toggle.addEventListener('click', function () {
            var dark = root.dataset.theme
                ? root.dataset.theme === 'dark'
                : window.matchMedia('(prefers-color-scheme: dark)').matches;
            var mode = dark ? 'light' : 'dark';
            root.dataset.theme = mode;
            if (modeField) {
                selectMode(mode.toUpperCase());
            }
            fetch(toggle.dataset.url, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded', 'X-Kabu-Theme': '1' },
                body: 'mode=' + mode,
                credentials: 'same-origin'
            }).catch(function () { /* offline - only this page keeps the choice */ });
        });
    }

    // ---- settings page: live preview, the hidden fields are what gets saved ----
    var DEFAULT_ACCENT = '#4f46e5';
    function selectMode(mode) {
        modeField.value = mode;
        document.querySelectorAll('.segmented__opt').forEach(function (b) {
            b.setAttribute('aria-checked', String(b.dataset.value === mode));
        });
        root.dataset.theme = mode === 'SYSTEM' ? '' : mode.toLowerCase();
    }
    // same formula as UserSettings.accentText()
    function textOn(hex) {
        var ch = function (i) {
            var c = parseInt(hex.substr(i, 2), 16) / 255;
            return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
        };
        return 0.2126 * ch(1) + 0.7152 * ch(3) + 0.0722 * ch(5) > 0.4 ? '#1d2130' : '#ffffff';
    }
    var accentField = document.getElementById('settings:accent');
    var picker = document.getElementById('accent-picker');
    function selectAccent(color) {
        color = (color || DEFAULT_ACCENT).toLowerCase();
        accentField.value = color === DEFAULT_ACCENT ? '' : color;
        var preset = false;
        document.querySelectorAll('.swatch[data-color]').forEach(function (s) {
            var hit = s.dataset.color === color;
            preset = preset || hit;
            s.setAttribute('aria-checked', String(hit));
        });
        picker.parentNode.setAttribute('aria-checked', String(!preset));
        picker.value = color;
        refreshLessonColors();
        if (color === DEFAULT_ACCENT) {
            root.dataset.accent = 'default';
            root.style.removeProperty('--user-accent');
            root.style.removeProperty('--user-accent-text');
        } else {
            root.dataset.accent = 'custom';
            root.style.setProperty('--user-accent', color);
            root.style.setProperty('--user-accent-text', textOn(color));
        }
    }
    if (modeField && accentField && picker) {
        document.querySelectorAll('.segmented__opt').forEach(function (b) {
            b.addEventListener('click', function () { selectMode(b.dataset.value); });
        });
        document.querySelectorAll('.swatch[data-color]').forEach(function (s) {
            s.addEventListener('click', function () { selectAccent(s.dataset.color); });
        });
        picker.addEventListener('input', function () { selectAccent(picker.value); });
        selectMode(modeField.value || 'SYSTEM');
        selectAccent(accentField.value);
    }

    // theme colours: same --u-<key> properties the server renders into <html style>; built-in values stay unset
    function applyColor(input) {
        var name = '--u-' + input.dataset.var;
        if (input.value.toLowerCase() === input.dataset.default) {
            root.style.removeProperty(name);
        } else {
            root.style.setProperty(name, input.value);
        }
    }
    document.querySelectorAll('.colors input[data-var]').forEach(function (input) {
        input.addEventListener('input', function () { applyColor(input); });
    });
    document.querySelectorAll('.colors .color-reset').forEach(function (b) {
        b.addEventListener('click', function () {
            b.closest('tr').querySelectorAll('input[data-var]').forEach(function (input) {
                input.value = input.dataset.default;
                applyColor(input);
            });
        });
    });

    // lesson colours: the hidden fields are saved (empty = follow); a teacher row falls back to its subject's row, a
    // subject row to the accent. The demos get the same --lesson-color as the timetable.
    var lessonRows = Array.prototype.slice.call(document.querySelectorAll('.lesson-colors tbody tr'));
    function subjectRow(row) {
        return lessonRows.filter(function (r) {
            return !r.dataset.teacher && r.dataset.subject === row.dataset.subject;
        })[0];
    }
    function refreshLessonColors() {
        // also called by selectAccent() during init, before lessonRows is set
        (lessonRows || []).forEach(function (row) {
            var own = row.querySelector('.lesson-color').value;
            var parent = row.dataset.teacher ? subjectRow(row) : null;
            var color = own || (parent && parent.querySelector('.lesson-color').value) || '';
            var demo = row.querySelector('.demo--lesson');
            if (color) {
                demo.style.setProperty('--lesson-color', color);
            } else {
                demo.style.removeProperty('--lesson-color');
            }
            row.querySelector('.lesson-picker').value = color || (picker ? picker.value : DEFAULT_ACCENT);
        });
    }
    lessonRows.forEach(function (row) {
        var lessonPicker = row.querySelector('.lesson-picker');
        var field = row.querySelector('.lesson-color');
        lessonPicker.addEventListener('input', function () {
            field.value = lessonPicker.value.toLowerCase();
            refreshLessonColors();
        });
        row.querySelector('.lesson-reset').addEventListener('click', function () {
            field.value = '';
            refreshLessonColors();
        });
        // an empty teacher name shows the subject's name: keep the teacher rows' placeholders in step
        if (!row.dataset.teacher) {
            var name = row.querySelector('.lesson-name');
            name.addEventListener('input', function () {
                lessonRows.forEach(function (r) {
                    if (r.dataset.teacher && r.dataset.subject === row.dataset.subject) {
                        r.querySelector('.lesson-name').placeholder = name.value.trim() || row.dataset.subject;
                    }
                });
            });
        }
    });

    // ---- mark the lesson running right now ----
    function toMinutes(hhmm) {
        var p = hhmm.split(':');
        return parseInt(p[0], 10) * 60 + parseInt(p[1], 10);
    }
    function markNow() {
        var now = new Date();
        var minutes = now.getHours() * 60 + now.getMinutes();
        document.querySelectorAll('.lesson.is-now').forEach(function (el) { el.classList.remove('is-now'); });
        document.querySelectorAll('.timetable__day.is-today .lesson, .daycard.is-today .lesson').forEach(function (el) {
            var range = (el.dataset.time || '').split('–');
            if (range.length === 2 && minutes >= toMinutes(range[0]) && minutes < toMinutes(range[1])) {
                el.classList.add('is-now');
            }
        });
    }
    markNow();
    setInterval(markNow, 60 * 1000);

    // ---- click a lesson: highlight every lesson of the same group (custom name, else subject + teacher) ----
    var highlighted = null;
    function highlight(group) {
        highlighted = group;
        document.querySelectorAll('.timetable, .daylist').forEach(function (list) {
            list.classList.toggle('has-highlight', group !== null);
        });
        document.querySelectorAll('.lesson[data-group]').forEach(function (el) {
            el.classList.toggle('is-highlighted', group !== null && el.dataset.group === group);
        });
    }
    document.addEventListener('click', function (e) {
        var lesson = e.target.closest ? e.target.closest('.lesson[data-group]') : null;
        if (lesson && lesson.dataset.group && lesson.dataset.group !== highlighted) {
            highlight(lesson.dataset.group);
        } else if (highlighted !== null) {
            highlight(null);
        }
    });
    document.addEventListener('keydown', function (e) {
        if (e.key === 'Escape' && highlighted !== null) {
            highlight(null);
        }
    });

    // ---- countdown to the next (not cancelled) lesson of today, and to the end of the running one ----
    var nextup = document.getElementById('nextup');
    if (nextup) {
        var endRow = document.getElementById('nextup-end');
        var endTimerEl = document.getElementById('nextup-end-timer');
        var nextRow = document.getElementById('nextup-next');
        var timerEl = document.getElementById('nextup-timer');
        var whatEl = document.getElementById('nextup-what');
        // desktop grid only - the mobile list holds the same lessons
        var lessons = [];
        document.querySelectorAll('.timetable__day.is-today .lesson:not(.lesson--cancelled)').forEach(function (el) {
            var range = (el.dataset.time || '').split('–');
            if (range.length !== 2) {
                return;
            }
            var text = function (selector) {
                var found = el.querySelector(selector);
                return found ? found.textContent.trim() : '';
            };
            lessons.push({
                start: toMinutes(range[0]) * 60,
                end: toMinutes(range[1]) * 60,
                subject: text('.lesson__subject'),
                teacher: text('.lesson__meta span:first-child'),
                room: text('.lesson__room')
            });
        });
        var pad = function (n) { return (n < 10 ? '0' : '') + n; };
        var format = function (left) {
            var h = Math.floor(left / 3600);
            var m = Math.floor(left % 3600 / 60);
            return (h > 0 ? h + ':' + pad(m) : m) + ':' + pad(left % 60);
        };
        var tick = function () {
            var now = new Date();
            var seconds = now.getHours() * 3600 + now.getMinutes() * 60 + now.getSeconds();
            var next = null;
            var currentEnd = null;
            lessons.forEach(function (l) {
                if (l.start > seconds && (next === null || l.start < next)) {
                    next = l.start;
                }
                // parallel lessons may end at different times - count until the last one is over
                if (l.start <= seconds && l.end > seconds && (currentEnd === null || l.end > currentEnd)) {
                    currentEnd = l.end;
                }
            });
            // the end only matters when the next lesson doesn't follow right away (break, free period, end of day)
            var showEnd = currentEnd !== null && currentEnd !== next;
            endRow.hidden = !showEnd;
            if (showEnd) {
                endTimerEl.textContent = format(currentEnd - seconds);
            }
            nextRow.hidden = next === null;
            if (next !== null) {
                timerEl.textContent = format(next - seconds);
                // parallel lessons (groups) share a start time - name the teacher to tell them apart
                var parallel = lessons.filter(function (l) { return l.start === next; });
                var labels = parallel.map(function (l) {
                    var details = [l.room, parallel.length > 1 ? l.teacher : ''].filter(Boolean).join(', ');
                    return l.subject + (details ? ' (' + details + ')' : '');
                });
                whatEl.textContent = labels.filter(function (s, i) { return s && labels.indexOf(s) === i; }).join(' / ');
            }
            nextup.hidden = !showEnd && next === null;
        };
        tick();
        setInterval(tick, 1000);
    }

    // ---- mobile: jump to today's card ----
    var todayCard = document.querySelector('.daycard.is-today');
    if (todayCard && window.matchMedia('(max-width: 860px)').matches && !location.hash) {
        todayCard.scrollIntoView({ block: 'start' });
    }

    // ---- keep an open tab fresh: reload (GET, never re-POST) after 15 min when visible ----
    if (!document.querySelector('.ui-datatable, .settings')) {
        var loadedAt = Date.now();
        var FRESH_MS = 15 * 60 * 1000;
        var refreshIfStale = function () {
            if (document.visibilityState === 'visible' && Date.now() - loadedAt > FRESH_MS) {
                location.href = location.pathname + location.search;
            }
        };
        document.addEventListener('visibilitychange', refreshIfStale);
        setInterval(refreshIfStale, 60 * 1000);
    }
})();
