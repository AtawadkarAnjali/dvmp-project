package com.devops.dvmp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DatasetRepository extends JpaRepository<Dataset, Long> {

    @Query("select d from Dataset d where lower(d.name) like lower(concat('%', :q, '%')) "
         + "or lower(d.owner) like lower(concat('%', :q, '%')) "
         + "or lower(coalesce(d.tags, '')) like lower(concat('%', :q, '%')) "
         + "or lower(coalesce(d.subtitle, '')) like lower(concat('%', :q, '%')) order by d.id desc")
    List<Dataset> search(@Param("q") String q);

    long countByStatus(Status status);
}
