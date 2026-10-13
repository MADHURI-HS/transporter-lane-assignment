package com.transporterassignment.repository;

import com.transporterassignment.entity.LaneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LaneRepository extends JpaRepository<LaneEntity, Long> {

    List<LaneEntity> findAllByOrderByIdAsc();
}
