package com.haedcom.access.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditLogTest {

    static class DummyAuditLog extends AuditLog {
        public void triggerOnCreate() {
            onCreate();
        }
    }

    @Test
    void crear_deberiaInicializarCampos_yGenerarEventKey() {
        UUID orgId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(5);

        AuditLog a = AuditLog.crear(orgId, "  ACCESS_ATTEMPTED  ", "  IntentoAcceso  ", "  123  ",
                "  corr-001  ", occurredAt, "  {\"ok\":true}  ");

        assertThat(a.getIdAudit()).isNotNull();
        assertThat(a.getIdOrganizacion()).isEqualTo(orgId);

        assertThat(a.getEventType()).isEqualTo("ACCESS_ATTEMPTED");
        assertThat(a.getAggregateType()).isEqualTo("IntentoAcceso");
        assertThat(a.getAggregateId()).isEqualTo("123");
        assertThat(a.getCorrelationId()).isEqualTo("corr-001");
        assertThat(a.getOccurredAtUtc()).isEqualTo(occurredAt);
        assertThat(a.getPayloadJson()).isEqualTo("{\"ok\":true}");

        String expectedKey = AuditLog.buildEventKey(orgId, "ACCESS_ATTEMPTED", "123", occurredAt);
        assertThat(a.getEventKey()).isEqualTo(expectedKey);
    }

    @Test
    void buildEventKey_deberiaSerDeterministico_yNormalizar() {
        UUID orgId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-01-01T10:20:30Z");

        String k1 = AuditLog.buildEventKey(orgId, "  EVT  ", "  agg-1  ", occurredAt);
        String k2 = AuditLog.buildEventKey(orgId, "EVT", "agg-1", occurredAt);

        assertThat(k1).isEqualTo(k2);
        assertThat(k1).contains("00000000-0000-0000-0000-000000000001|EVT|agg-1|");
        assertThat(k1).endsWith(occurredAt.toInstant().toString());
    }

    @Test
    void buildEventKey_conNulls_deberiaUsarMarcadoresNO() {
        String k = AuditLog.buildEventKey(null, null, "   ", null);
        assertThat(k).isEqualTo("NO_ORG|NO_TYPE|NO_AGG|NO_TS");
    }


    @Test
    void onCreate_deberiaSetearCreatedAtUtc_siEsNull_yNoSobrescribir() throws InterruptedException {
        DummyAuditLog a = new DummyAuditLog();

        assertThat(a.getCreatedAtUtc()).isNull();

        OffsetDateTime antes = OffsetDateTime.now(ZoneOffset.UTC);
        a.triggerOnCreate();
        OffsetDateTime despues = OffsetDateTime.now(ZoneOffset.UTC);

        assertThat(a.getCreatedAtUtc()).isNotNull();
        assertThat(a.getCreatedAtUtc()).isBetween(antes, despues);
        assertThat(a.getCreatedAtUtc().getOffset()).isEqualTo(ZoneOffset.UTC);

        OffsetDateTime original = a.getCreatedAtUtc();
        Thread.sleep(5);
        a.triggerOnCreate(); // no debe cambiar
        assertThat(a.getCreatedAtUtc()).isEqualTo(original);
    }

    @Test
    void setIdAudit_null_deberiaLanzar() {
        AuditLog a = AuditLog.crear(UUID.randomUUID(), "EVT", null, "agg", null,
                OffsetDateTime.now(ZoneOffset.UTC), "{}");

        assertThatThrownBy(() -> a.setIdAudit(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("idAudit es obligatorio");
    }

    @Test
    void setOccurredAtUtc_null_deberiaLanzar() {
        AuditLog a = AuditLog.crear(UUID.randomUUID(), "EVT", null, "agg", null,
                OffsetDateTime.now(ZoneOffset.UTC), "{}");

        assertThatThrownBy(() -> a.setOccurredAtUtc(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("occurredAtUtc es obligatorio");
    }

    @Test
    void settersRequired_nullOBLank_deberianLanzar() {
        AuditLog a = AuditLog.crear(UUID.randomUUID(), "EVT", null, "agg", null,
                OffsetDateTime.now(ZoneOffset.UTC), "{}");

        assertThatThrownBy(() -> a.setEventType(null)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eventType es obligatorio");

        assertThatThrownBy(() -> a.setEventKey("   ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eventKey es obligatorio");

        assertThatThrownBy(() -> a.setPayloadJson(" ")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("payloadJson es obligatorio");
    }

    @Test
    void settersOptional_blank_deberianQuedarNull_yTrim() {
        AuditLog a = AuditLog.crear(UUID.randomUUID(), "EVT", "  Agg  ", "  123  ", "  corr  ",
                OffsetDateTime.now(ZoneOffset.UTC), "{}");

        a.setAggregateType("   ");
        a.setAggregateId(null);
        a.setCorrelationId("   ");

        assertThat(a.getAggregateType()).isNull();
        assertThat(a.getAggregateId()).isNull();
        assertThat(a.getCorrelationId()).isNull();

        a.setAggregateType("  T  ");
        a.setAggregateId("  A1  ");
        a.setCorrelationId("  C1  ");

        assertThat(a.getAggregateType()).isEqualTo("T");
        assertThat(a.getAggregateId()).isEqualTo("A1");
        assertThat(a.getCorrelationId()).isEqualTo("C1");
    }

    @Test
    void limitesDeLongitud_deberianValidarse() {
        AuditLog a = AuditLog.crear(UUID.randomUUID(), "EVT", null, "agg", null,
                OffsetDateTime.now(ZoneOffset.UTC), "{}");

        String s221 = "a".repeat(221);
        assertThatThrownBy(() -> a.setEventKey(s221)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eventKey máximo 220 caracteres");

        String s161 = "a".repeat(161);
        assertThatThrownBy(() -> a.setEventType(s161)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("eventType máximo 160 caracteres");

        String s81 = "a".repeat(81);
        assertThatThrownBy(() -> a.setAggregateType(s81))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("aggregateType máximo 80");

        String s61 = "a".repeat(61);
        assertThatThrownBy(() -> a.setAggregateId(s61)).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("aggregateId máximo 60");

        String s121 = "a".repeat(121);
        assertThatThrownBy(() -> a.setCorrelationId(s121))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("correlationId máximo 120");
    }
}
