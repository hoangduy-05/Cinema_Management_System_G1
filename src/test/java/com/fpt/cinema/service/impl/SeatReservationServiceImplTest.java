package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.request.HoldSeatsRequest;
import com.fpt.cinema.entity.Account;
import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.BookingStateMachine;
import com.fpt.cinema.entity.Branch;
import com.fpt.cinema.entity.Customer;
import com.fpt.cinema.entity.Movie;
import com.fpt.cinema.entity.Room;
import com.fpt.cinema.entity.Seat;
import com.fpt.cinema.entity.SeatType;
import com.fpt.cinema.entity.Showtime;
import com.fpt.cinema.entity.ShowtimeSeat;
import com.fpt.cinema.entity.Ticket;
import com.fpt.cinema.enums.BookingStatus;
import com.fpt.cinema.enums.ShowtimeSeatStatus;
import com.fpt.cinema.enums.TicketStatus;
import com.fpt.cinema.exception.SeatUnavailableException;
import com.fpt.cinema.mapper.BookingMapper;
import com.fpt.cinema.repository.BookingRepository;
import com.fpt.cinema.repository.CinemaOrderRepository;
import com.fpt.cinema.repository.CustomerRepository;
import com.fpt.cinema.repository.ShowtimeRepository;
import com.fpt.cinema.repository.ShowtimeSeatRepository;
import com.fpt.cinema.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeatReservationServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-14T03:00:00Z"),
            ZoneId.of("Asia/Bangkok")
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 14, 10, 0);

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ShowtimeRepository showtimeRepository;
    @Mock
    private ShowtimeSeatRepository showtimeSeatRepository;
    @Mock
    private CinemaOrderRepository cinemaOrderRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private BookingMapper bookingMapper;

    private SeatReservationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SeatReservationServiceImpl(
                customerRepository,
                showtimeRepository,
                showtimeSeatRepository,
                cinemaOrderRepository,
                bookingRepository,
                ticketRepository,
                new BookingStateMachine(),
                bookingMapper,
                CLOCK,
                10
        );
    }

    @Test
    void holdsEveryRequestedSeatAtomicallyAndCalculatesPricesOnServer() {
        Showtime showtime = activeShowtime(NOW.plusHours(2));
        ShowtimeSeat seat701 = seat(701L, showtime, ShowtimeSeatStatus.AVAILABLE, "1.00");
        ShowtimeSeat seat702 = seat(702L, showtime, ShowtimeSeatStatus.AVAILABLE, "1.50");
        stubReservable(showtime, List.of(seat701, seat702));
        when(bookingRepository.findByBookingCode(any())).thenReturn(Optional.empty());

        service.holdSeats(new HoldSeatsRequest(3001L, List.of(702L, 701L)), "customer");

        verify(showtimeSeatRepository).findAllForUpdate(3001L, List.of(701L, 702L));
        assertEquals(ShowtimeSeatStatus.HELD, seat701.getSeatStatus());
        assertEquals(ShowtimeSeatStatus.HELD, seat702.getSeatStatus());
        assertEquals(NOW.plusMinutes(10), seat701.getLockedUntil());
        assertEquals(NOW.plusMinutes(10), seat702.getLockedUntil());
        assertSame(seat701.getBooking(), seat702.getBooking());

        ArgumentCaptor<Booking> bookingCaptor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(bookingCaptor.capture());
        Booking booking = bookingCaptor.getValue();
        assertEquals(BookingStatus.SEAT_HELD, booking.getStatus());
        assertEquals(new BigDecimal("225000.00"), booking.getSeatSubtotal());
        assertEquals(new BigDecimal("225000.00"), booking.getTotalAmount());
        assertEquals(NOW, booking.getCreatedAt());
        assertEquals(NOW.plusMinutes(10), booking.getHoldExpiresAt());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Ticket>> ticketCaptor = ArgumentCaptor.forClass(List.class);
        verify(ticketRepository).saveAll(ticketCaptor.capture());
        assertEquals(2, ticketCaptor.getValue().size());
        ticketCaptor.getValue().forEach(ticket -> assertEquals(TicketStatus.HELD, ticket.getTicketStatus()));
    }

    @Test
    void rejectsWholeRequestWhenOneSeatIsAlreadyHeld() {
        Showtime showtime = activeShowtime(NOW.plusHours(2));
        ShowtimeSeat available = seat(701L, showtime, ShowtimeSeatStatus.AVAILABLE, "1.00");
        ShowtimeSeat held = seat(702L, showtime, ShowtimeSeatStatus.HELD, "1.00");
        stubReservable(showtime, List.of(available, held));

        assertThrows(
                SeatUnavailableException.class,
                () -> service.holdSeats(new HoldSeatsRequest(3001L, List.of(701L, 702L)), "customer")
        );

        assertEquals(ShowtimeSeatStatus.AVAILABLE, available.getSeatStatus());
        verify(cinemaOrderRepository, never()).save(any());
        verify(bookingRepository, never()).save(any());
        verify(ticketRepository, never()).saveAll(any());
    }

    @Test
    void rejectsBookedSeat() {
        Showtime showtime = activeShowtime(NOW.plusHours(2));
        ShowtimeSeat booked = seat(701L, showtime, ShowtimeSeatStatus.BOOKED, "1.00");
        stubReservable(showtime, List.of(booked));

        assertThrows(
                SeatUnavailableException.class,
                () -> service.holdSeats(new HoldSeatsRequest(3001L, List.of(701L)), "customer")
        );
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsSeatIdsThatDoNotAllBelongToRequestedShowtime() {
        Showtime showtime = activeShowtime(NOW.plusHours(2));
        ShowtimeSeat onlyMatchingSeat = seat(701L, showtime, ShowtimeSeatStatus.AVAILABLE, "1.00");
        stubReservable(showtime, List.of(onlyMatchingSeat));

        assertThrows(
                ResponseStatusException.class,
                () -> service.holdSeats(new HoldSeatsRequest(3001L, List.of(701L, 999L)), "customer")
        );
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void rejectsShowtimeThatAlreadyStartedBeforeLockingSeats() {
        Showtime showtime = activeShowtime(NOW);
        when(customerRepository.findByAccountUsernameIgnoreCase("customer"))
                .thenReturn(Optional.of(activeCustomer()));
        when(showtimeRepository.findById(3001L)).thenReturn(Optional.of(showtime));

        assertThrows(
                ResponseStatusException.class,
                () -> service.holdSeats(new HoldSeatsRequest(3001L, List.of(701L)), "customer")
        );
        verify(showtimeSeatRepository, never()).findAllForUpdate(any(), any());
    }

    @Test
    void releasesOnlyHeldSeatsStillOwnedByBooking() {
        Booking booking = new Booking();
        booking.setId(8001L);
        Booking anotherBooking = new Booking();
        anotherBooking.setId(8002L);
        Showtime showtime = activeShowtime(NOW.plusHours(2));

        ShowtimeSeat heldOwned = seat(701L, showtime, ShowtimeSeatStatus.HELD, "1.00");
        heldOwned.setBooking(booking);
        heldOwned.setLockedUntil(NOW.plusMinutes(10));
        ShowtimeSeat bookedOwned = seat(702L, showtime, ShowtimeSeatStatus.BOOKED, "1.00");
        bookedOwned.setBooking(booking);
        ShowtimeSeat heldByOther = seat(703L, showtime, ShowtimeSeatStatus.HELD, "1.00");
        heldByOther.setBooking(anotherBooking);

        when(showtimeSeatRepository.findAllByBookingIdForUpdate(8001L))
                .thenReturn(List.of(heldOwned, bookedOwned, heldByOther));

        service.releaseHeldSeats(booking);

        assertEquals(ShowtimeSeatStatus.AVAILABLE, heldOwned.getSeatStatus());
        assertNull(heldOwned.getBooking());
        assertNull(heldOwned.getLockedUntil());
        assertEquals(ShowtimeSeatStatus.BOOKED, bookedOwned.getSeatStatus());
        assertSame(booking, bookedOwned.getBooking());
        assertEquals(ShowtimeSeatStatus.HELD, heldByOther.getSeatStatus());
        assertSame(anotherBooking, heldByOther.getBooking());
    }

    private void stubReservable(Showtime showtime, List<ShowtimeSeat> seats) {
        when(customerRepository.findByAccountUsernameIgnoreCase("customer"))
                .thenReturn(Optional.of(activeCustomer()));
        when(showtimeRepository.findById(3001L)).thenReturn(Optional.of(showtime));
        when(showtimeSeatRepository.findAllForUpdate(eq(3001L), any())).thenReturn(seats);
    }

    private Customer activeCustomer() {
        Account account = new Account();
        account.setStatus("ACTIVE");
        account.setUsername("customer");
        Customer customer = new Customer();
        customer.setCustomerId(1001L);
        customer.setAccount(account);
        return customer;
    }

    private Showtime activeShowtime(LocalDateTime startTime) {
        Branch branch = new Branch();
        branch.setBranchId(1L);
        branch.setStatus("ACTIVE");
        Room room = new Room();
        room.setRoomId(101L);
        room.setBranch(branch);
        room.setStatus("ACTIVE");
        Movie movie = new Movie();
        movie.setMovieId(1001L);
        movie.setStatus("ACTIVE");

        Showtime showtime = new Showtime();
        showtime.setShowtimeId(3001L);
        showtime.setRoom(room);
        showtime.setMovie(movie);
        showtime.setStartTime(startTime);
        showtime.setEndTime(startTime.plusHours(2));
        showtime.setPrice(new BigDecimal("90000.00"));
        showtime.setStatus("AVAILABLE");
        return showtime;
    }

    private ShowtimeSeat seat(
            Long id,
            Showtime showtime,
            ShowtimeSeatStatus status,
            String multiplier
    ) {
        SeatType seatType = new SeatType();
        seatType.setSeatTypeId(1L);
        seatType.setTypeName("STANDARD");
        seatType.setPriceMultiplier(new BigDecimal(multiplier));
        seatType.setStatus("ACTIVE");

        Seat seat = new Seat();
        seat.setSeatId(id + 1000);
        seat.setRoom(showtime.getRoom());
        seat.setSeatType(seatType);
        seat.setStatus("ACTIVE");

        ShowtimeSeat showtimeSeat = new ShowtimeSeat();
        showtimeSeat.setShowtimeSeatId(id);
        showtimeSeat.setShowtime(showtime);
        showtimeSeat.setSeat(seat);
        showtimeSeat.setSeatStatus(status);
        return showtimeSeat;
    }
}
