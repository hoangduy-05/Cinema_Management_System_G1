package com.fpt.cinema.mapper;

import com.fpt.cinema.dto.response.ComboResponse;
import com.fpt.cinema.entity.Combo;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
public class ComboMapper {

    public ComboResponse toResponse(Combo combo) {
        if (combo == null) {
            return null;
        }

        return new ComboResponse(
                combo.getId(),
                combo.getName(),
                combo.getDescription(),
                combo.getImageUrl(),
                combo.getPrice()
        );
    }

    public List<ComboResponse> toResponses(Collection<Combo> combos) {
        if (combos == null || combos.isEmpty()) {
            return List.of();
        }

        return combos.stream()
                .filter(Objects::nonNull)
                .map(this::toResponse)
                .toList();
    }
}
