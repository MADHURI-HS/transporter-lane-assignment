package com.transporterassignment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "transporters")
public class TransporterEntity {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    protected TransporterEntity() {
    }

    public TransporterEntity(Long id, String name) {
        this.id = id;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
