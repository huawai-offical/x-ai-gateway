package com.prodigalgal.xaigateway.infra.persistence.converter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Collections;
import java.util.List;

@Converter
public class StringListJsonConverter implements AttributeConverter<List<String>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<String> attribute) {
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute == null ? List.of() : attribute);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法将字符串列表序列化为 JSON。", exception);
        }
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return List.of();
        }

        try {
            return Collections.unmodifiableList(OBJECT_MAPPER.readValue(dbData, LIST_TYPE));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("无法将 JSON 反序列化为字符串列表。", exception);
        }
    }
}
