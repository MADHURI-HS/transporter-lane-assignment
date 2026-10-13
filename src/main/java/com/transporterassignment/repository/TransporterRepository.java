package com.transporterassignment.repository;

import com.transporterassignment.entity.TransporterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransporterRepository extends JpaRepository<TransporterEntity, Long> {

    List<TransporterEntity> findAllByOrderByIdAsc();
}
