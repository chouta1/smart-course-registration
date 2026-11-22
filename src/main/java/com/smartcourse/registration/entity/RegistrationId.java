package com.smartcourse.registration.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class RegistrationId implements Serializable {

    @Column(name = "offering_id", length = 64, nullable = false)
    private String offeringId;

    @Column(name = "learner_id", length = 64, nullable = false)
    private String learnerId;

    protected RegistrationId() {
    }

    public RegistrationId(String offeringId, String learnerId) {
        this.offeringId = offeringId;
        this.learnerId = learnerId;
    }

    public String getOfferingId() {
        return offeringId;
    }

    public String getLearnerId() {
        return learnerId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RegistrationId that)) {
            return false;
        }
        return Objects.equals(offeringId, that.offeringId)
                && Objects.equals(learnerId, that.learnerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offeringId, learnerId);
    }
}
