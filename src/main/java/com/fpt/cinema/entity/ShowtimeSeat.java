package com.fpt.cinema.entity;

import com.fpt.cinema.enums.ShowtimeSeatStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "showtime_seat",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_showtime_seat",
                columnNames = {"showtime_id", "seat_id"}
        ))
@Getter
@Setter
@NoArgsConstructor
public class ShowtimeSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "showtime_seat_id", nullable = false)
    private Long showtimeSeatId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "showtime_id", nullable = false)
    private Showtime showtime;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seat_id", nullable = false)
    private Seat seat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id")
    private Booking booking;

    @Convert(converter = ShowtimeSeatStatusConverter.class)
    @Column(name = "seat_status", nullable = false, length = 30)
    private ShowtimeSeatStatus seatStatus = ShowtimeSeatStatus.AVAILABLE;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
