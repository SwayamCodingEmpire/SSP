package com.isekai.ssp.repository;

import com.isekai.ssp.entities.QualityScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QualityScoreRepository extends JpaRepository<QualityScore, Long> {
    Optional<QualityScore> findByChapterId(Long chapterId);
}
