package com.smartcourse.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "course_offering")
public class CourseOffering {

    @Id
    @Column(name = "offering_id", length = 64, nullable = false)
    private String offeringId;

    @Column(nullable = false)
    private int capacity;

    protected CourseOffering() {
    }

    public CourseOffering(String offeringId, int capacity) {
        this.offeringId = offeringId;
        this.capacity = capacity;
    }

    public String getOfferingId() {
        return offeringId;
    }

    public int getCapacity() {
        return capacity;
    }
}
