package com.isekai.ssp.repository;

import com.isekai.ssp.entities.ModelAdapter;
import com.isekai.ssp.helpers.ContentFamily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelAdapterRepository extends JpaRepository<ModelAdapter, Long> {
    List<ModelAdapter> findByActiveTrue();
    List<ModelAdapter> findByTargetFamilyAndActiveTrue(ContentFamily family);
    List<ModelAdapter> findByLanguagePairAndActiveTrue(String languagePair);
}
