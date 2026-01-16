package com.haedcom.access.domain.model;

import java.time.ZoneId;
import java.time.zone.ZoneRulesException;

public final class TimeZoneValidator {

    private TimeZoneValidator() {}

    public static String normalizeAndValidateIana(String tz) {
        String v = (tz == null) ? null : tz.trim();
        if (v == null || v.isBlank())
            return null;
        try {
            ZoneId.of(v);
            return v;
        } catch (ZoneRulesException e) {
            throw new IllegalArgumentException("Zona horaria inválida (IANA): " + v);
        }
    }

}
