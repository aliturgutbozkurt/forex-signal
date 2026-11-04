package com.fsp.identity.domain;

import java.time.Instant;
import java.util.Objects;
import org.jmolecules.ddd.annotation.AggregateRoot;

@AggregateRoot
public final class PasskeyAccount {

    private final String userId;
    private final String displayName;
    private final String credentialId;
    private final String publicKeyAlgorithm;
    private final Instant registeredAt;

    public PasskeyAccount(
            String userId,
            String displayName,
            String credentialId,
            String publicKeyAlgorithm,
            Instant registeredAt
    ) {
        this.userId = Objects.requireNonNull(userId);
        this.displayName = Objects.requireNonNull(displayName);
        this.credentialId = Objects.requireNonNull(credentialId);
        this.publicKeyAlgorithm = Objects.requireNonNull(publicKeyAlgorithm);
        this.registeredAt = Objects.requireNonNull(registeredAt);
    }

    public String userId() {
        return userId;
    }

    public String displayName() {
        return displayName;
    }

    public String credentialId() {
        return credentialId;
    }

    public String publicKeyAlgorithm() {
        return publicKeyAlgorithm;
    }

    public Instant registeredAt() {
        return registeredAt;
    }
}

