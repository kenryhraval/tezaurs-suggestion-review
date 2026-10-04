package lv.tezaurs.suggestions.suggestion.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SuggestionStatusConverter implements AttributeConverter<SuggestionStatus, Integer> {

    @Override
    public Integer convertToDatabaseColumn(SuggestionStatus status) {
        return status == null ? null : status.databaseValue();
    }

    @Override
    public SuggestionStatus convertToEntityAttribute(Integer value) {
        return value == null ? null : SuggestionStatus.fromDatabaseValue(value);
    }
}
