package com.audit.webapp.repository.live;

import com.audit.webapp.entity.live.TspContactList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TspContactRepository extends JpaRepository<TspContactList, Long> {

    /**
     * Active email-enabled contacts for a TSP (canonicalized match).
     * Uses hyphen/space-insensitive comparison to handle Vodafone-Idea vs Vodafone Idea.
     */
    @Query(value = """
        SELECT * FROM dm.t_tsp_contact_list c
        WHERE LOWER(REPLACE(c.tsp_name,'-',' ')) = LOWER(REPLACE(CAST(:tsp AS varchar),'-',' '))
          AND c.deactivated_on IS NULL
          AND c.email_notifications = true
          AND c.email_id IS NOT NULL AND c.email_id <> ''
        ORDER BY c.name
        """, nativeQuery = true)
    List<TspContactList> findActiveEmailContacts(@Param("tsp") String canonicalTsp);

    @Query(value = """
        SELECT * FROM dm.t_tsp_contact_list c
        WHERE c.deactivated_on IS NULL
          AND c.email_notifications = true
          AND c.email_id IS NOT NULL AND c.email_id <> ''
        ORDER BY c.tsp_name, c.name
        """, nativeQuery = true)
    List<TspContactList> findAllActiveEmailContacts();
}
