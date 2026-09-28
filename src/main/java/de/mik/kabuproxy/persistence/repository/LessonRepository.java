package de.mik.kabuproxy.persistence.repository;

import de.mik.kabuproxy.persistence.entities.LessonChangeEntity;
import de.mik.kabuproxy.persistence.entities.LessonEntity;
import de.mik.kabuproxy.persistence.entities.LessonStatus;
import de.mik.kabuproxy.persistence.entities.SchoolClassEntity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class LessonRepository
{
    private static final int MAX_CHANGES = 100;
    private static final int MAX_CHANGE_LOG = 500;

    @PersistenceContext(unitName = "kabu")
    private EntityManager em;

    public List<LessonEntity> findBetween(long classId, LocalDate from, LocalDate to)
    {
        return em.createQuery("select l from LessonEntity l where l.schoolClass.id = :classId and l.date between :from and :to"
                    + " order by l.date, l.periodFrom, l.lane",
                LessonEntity.class)
            .setParameter("classId", classId)
            .setParameter("from", from)
            .setParameter("to", to)
            .getResultList();
    }

    /**
     * Every subject the class has had so far (for the lesson colours in the settings).
     */
    public List<String> findSubjects(long classId)
    {
        return em.createQuery("select distinct l.subject from LessonEntity l where l.schoolClass.id = :classId and l.subject is not null", String.class)
            .setParameter("classId", classId)
            .getResultList();
    }

    /**
     * Subject/teacher pairs of the class's regular lessons (substitutes don't count as the subject's teachers).
     */
    public List<Object[]> findRegularTeachers(long classId)
    {
        return em.createQuery("select distinct l.subject, l.teacher from LessonEntity l where l.schoolClass.id = :classId"
                    + " and l.subject is not null and l.teacher is not null and l.status = :regular", Object[].class)
            .setParameter("classId", classId)
            .setParameter("regular", LessonStatus.REGULAR)
            .getResultList();
    }

    public void deleteDay(SchoolClassEntity schoolClass, LocalDate date)
    {
        em.createQuery("delete from LessonEntity l where l.schoolClass = :schoolClass and l.date = :date")
            .setParameter("schoolClass", schoolClass)
            .setParameter("date", date)
            .executeUpdate();
    }

    public void persist(LessonEntity lesson)
    {
        em.persist(lesson);
    }

    public void persist(LessonChangeEntity change)
    {
        em.persist(change);
    }

    /**
     * Changes detected after {@code since} that concern lessons from {@code fromDate} on (past lessons are irrelevant).
     */
    public List<LessonChangeEntity> findChanges(long classId, Instant since, LocalDate fromDate)
    {
        return em.createQuery("select c from LessonChangeEntity c where c.schoolClass.id = :classId and c.detectedAt > :since"
                    + " and c.date >= :fromDate order by c.date, c.periodFrom, c.detectedAt",
                LessonChangeEntity.class)
            .setParameter("classId", classId)
            .setParameter("since", since)
            .setParameter("fromDate", fromDate)
            .setMaxResults(MAX_CHANGES)
            .getResultList();
    }

    /**
     * The class's change log: from {@code fromDate} on in lesson order, or (null) everything, latest lessons first.
     */
    public List<LessonChangeEntity> findChangeLog(long classId, LocalDate fromDate)
    {
        String order = fromDate == null ? " order by c.date desc, c.periodFrom, c.detectedAt" : " order by c.date, c.periodFrom, c.detectedAt";
        TypedQuery<LessonChangeEntity> query = em.createQuery("select c from LessonChangeEntity c where c.schoolClass.id = :classId"
                    + (fromDate == null ? "" : " and c.date >= :fromDate") + order,
                LessonChangeEntity.class)
            .setParameter("classId", classId)
            .setMaxResults(MAX_CHANGE_LOG);
        if (fromDate != null)
        {
            query.setParameter("fromDate", fromDate);
        }
        return query.getResultList();
    }
}
