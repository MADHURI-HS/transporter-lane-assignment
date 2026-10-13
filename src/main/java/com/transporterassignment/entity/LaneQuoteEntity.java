package com.transporterassignment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "lane_quotes")
public class LaneQuoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transporter_id", nullable = false)
    private Long transporterId;

    @Column(name = "lane_id", nullable = false)
    private Long laneId;

    @Column(nullable = false)
    private Long quote;

    protected LaneQuoteEntity() {
    }

    public LaneQuoteEntity(Long transporterId, Long laneId, Long quote) {
        this.transporterId = transporterId;
        this.laneId = laneId;
        this.quote = quote;
    }

    public Long getTransporterId() {
        return transporterId;
    }

    public Long getLaneId() {
        return laneId;
    }

    public Long getQuote() {
        return quote;
    }
}
