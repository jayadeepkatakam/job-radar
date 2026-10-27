package com.jayadeep.jobradar.domain;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.List;

/** Stores a list of short strings in one TEXT column, one entry per line. */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    @Override
    public String convertToDatabaseColumn(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return String.join("\n", values);
    }

    @Override
    public List<String> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return List.of();
        }
        return Arrays.stream(column.split("\n"))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
