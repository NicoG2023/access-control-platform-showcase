package com.haedcom.access.domain.events;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Invalida toda la política cacheada del tenant (uso excepcional). */
public record ReglaAccesoPolicyInvalidateAllRequested(UUID eventId, UUID orgId, String reason,
        OffsetDateTime occurredAtUtc) {
    public ReglaAccesoPolicyInvalidateAllRequested {
        if (eventId == null)
            throw new IllegalArgumentException("eventId es obligatorio");
        if (orgId == null)
            throw new IllegalArgumentException("orgId es obligatorio");
        if (occurredAtUtc == null)
            throw new IllegalArgumentException("occurredAtUtc es obligatorio");
    }

    public static ReglaAccesoPolicyInvalidateAllRequested of(UUID orgId, String reason,
            OffsetDateTime nowUtc) {
        return new ReglaAccesoPolicyInvalidateAllRequested(UUID.randomUUID(), orgId, reason,
                nowUtc);
    }
}
