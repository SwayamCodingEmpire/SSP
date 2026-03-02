package com.isekai.ssp.repository;

import com.isekai.ssp.entities.Segment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SegmentRepository extends JpaRepository<Segment, Long> {
    List<Segment> findByChapterIdOrderBySequenceNumber(Long chapterId);
}
