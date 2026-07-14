package com.fpt.cinema.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShowtimeResponse(
        Long showtimeId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BigDecimal price,
        String status,
        Long movieId,
        String movieTitle,
        String moviePosterUrl,
        Integer movieDuration,
        Long branchId,
        String branchName,
        String branchAddress,
        Long roomId,
        String roomName,
        String roomType
) {
}
