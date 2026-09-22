package com.px.base.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.px.base.entity.DutyQualificationSnapshot;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DutyQualificationSnapshotConverter implements AttributeConverter<DutyQualificationSnapshot, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Override
    public String convertToDatabaseColumn(DutyQualificationSnapshot snapshot) {
        if (snapshot == null) return null;
        try {
            return MAPPER.writeValueAsString(snapshot);
        } catch (Exception e) {
            throw new IllegalArgumentException("值守资格快照无法序列化", e);
        }
    }

    @Override
    public DutyQualificationSnapshot convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, DutyQualificationSnapshot.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("值守资格快照无法反序列化", e);
        }
    }
}
