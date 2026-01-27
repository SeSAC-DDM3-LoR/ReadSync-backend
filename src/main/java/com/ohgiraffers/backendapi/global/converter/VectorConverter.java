package com.ohgiraffers.backendapi.global.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Converter
public class VectorConverter implements AttributeConverter<List<Float>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<Float> attribute) {
        if (attribute == null) {
            return null;
        }
        // pgvector format: [1.1,2.2,3.3]
        return attribute.toString();
    }

    @Override
    public List<Float> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        // pgvector returns string like "[1.1,2.2,3.3]"
        // We can parse it manually or use Jackson if it's strictly JSON compatible
        // pgvector output is usually JSON compatible for arrays
        try {
            String cleanData = dbData.trim();
            if (cleanData.startsWith("[") && cleanData.endsWith("]")) {
                String[] parts = cleanData.substring(1, cleanData.length() - 1).split(",");
                return Arrays.stream(parts)
                        .map(String::trim)
                        .map(Float::parseFloat)
                        .collect(Collectors.toList());
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert vector string to list: " + dbData, e);
        }
    }
}
