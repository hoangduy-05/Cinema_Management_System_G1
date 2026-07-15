package com.fpt.cinema.entity;

import com.fpt.cinema.enums.ShowtimeSeatStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ShowtimeSeatStatusConverter implements AttributeConverter<ShowtimeSeatStatus, String> {

    @Override
    public String convertToDatabaseColumn(ShowtimeSeatStatus status) {
        return status == null ? null : status.getDatabaseValue();
    }

    @Override
    public ShowtimeSeatStatus convertToEntityAttribute(String value) {
        return ShowtimeSeatStatus.fromDatabaseValue(value);
    }
}
