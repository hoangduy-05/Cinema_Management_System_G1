package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.request.HoldSeatsRequest;
import com.fpt.cinema.dto.response.BookingResponse;
import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.BookingStateMachine;
import com.fpt.cinema.entity.CinemaOrder;
import com.fpt.cinema.entity.Customer;
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
import com.fpt.cinema.service.SeatReservationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class SeatReservationServiceImpl implements SeatReservationService {

    private static final String ACTIVE = "ACTIVE";
    private static final String AVAILABLE = "AVAILABLE";
    private static final DateTimeFormatter BOOKING_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final CustomerRepository customerRepository;
    private final ShowtimeRepository showtimeRepository;
    private final ShowtimeSeatRepository showtimeSeatRepository;
    private final CinemaOrderRepository cinemaOrderRepository;
    private final BookingRepository bookingRepository;
    private final TicketRepository ticketRepository;
    private final BookingStateMachine bookingStateMachine;
    private final BookingMapper bookingMapper;
    private final Clock clock;
    private final long holdDurationMinutes;

    public SeatReservationServiceImpl(
            CustomerRepository customerRepository,
            ShowtimeRepository showtimeRepository,
            ShowtimeSeatRepository showtimeSeatRepository,
            CinemaOrderRepository cinemaOrderRepository,
            BookingRepository bookingRepository,
            TicketRepository ticketRepository,
            BookingStateMachine bookingStateMachine,
            BookingMapper bookingMapper,
            Clock clock,
            @Value("${booking.hold-duration-minutes:10}") long holdDurationMinutes
    ) {
        if (holdDurationMinutes <= 0) {
            throw new IllegalArgumentException("booking.hold-duration-minutes must be positive");
        }
        this.customerRepository = customerRepository;
        this.showtimeRepository = showtimeRepository;
        this.showtimeSeatRepository = showtimeSeatRepository;
        this.cinemaOrderRepository = cinemaOrderRepository;
        this.bookingRepository = bookingRepository;
        this.ticketRepository = ticketRepository;
        this.bookingStateMachine = bookingStateMachine;
        this.bookingMapper = bookingMapper;
        this.clock = clock;
        this.holdDurationMinutes = holdDurationMinutes;
    }

    @Override
    @Transactional
    public BookingResponse holdSeats(HoldSeatsRequest request, String username) {
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = requireActiveCustomer(username);
        Showtime showtime = requireReservableShowtime(request.showtimeId(), now);
        List<Long> requestedIds = normalizeRequestedSeatIds(request.showtimeSeatIds());

        List<ShowtimeSeat> seats = showtimeSeatRepository.findAllForUpdate(
                showtime.getShowtimeId(),
                requestedIds
        );
        if (seats.size() != requestedIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "One or more requested seats do not exist for this showtime"
            );
        }

        List<Long> unavailableIds = seats.stream()
                .filter(seat -> !isReservableSeat(seat, showtime))
                .map(ShowtimeSeat::getShowtimeSeatId)
                .toList();
        if (!unavailableIds.isEmpty()) {
            throw new SeatUnavailableException(unavailableIds);
        }

        LocalDateTime holdExpiresAt = now.plusMinutes(holdDurationMinutes);
        CinemaOrder order = createOrder(customer, showtime, now);
        cinemaOrderRepository.save(order);

        Booking booking = createBooking(order, showtime, now, holdExpiresAt);
        List<Ticket> tickets = new ArrayList<>(seats.size());
        BigDecimal seatSubtotal = BigDecimal.ZERO;
        for (ShowtimeSeat showtimeSeat : seats) {
            BigDecimal price = calculateSeatPrice(showtime, showtimeSeat);
            seatSubtotal = seatSubtotal.add(price);

            showtimeSeat.setSeatStatus(ShowtimeSeatStatus.HELD);
            showtimeSeat.setLockedUntil(holdExpiresAt);
            showtimeSeat.setBooking(booking);

            tickets.add(createHeldTicket(booking, showtimeSeat, price));
        }

        seatSubtotal = money(seatSubtotal);
        booking.setSeatSubtotal(seatSubtotal);
        booking.setComboSubtotal(BigDecimal.ZERO.setScale(2));
        booking.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        booking.setTotalAmount(seatSubtotal);
        order.setTotalAmount(seatSubtotal);

        bookingStateMachine.initialize(booking);
        bookingRepository.save(booking);
        bookingStateMachine.transition(booking, BookingStatus.SEAT_HELD);
        booking.setUpdatedAt(now);
        order.setStatus(BookingStatus.SEAT_HELD.name());

        showtimeSeatRepository.saveAll(seats);
        ticketRepository.saveAll(tickets);

        return bookingMapper.toBookingResponse(booking, tickets, List.of(), now);
    }

    @Override
    @Transactional
    public void releaseHeldSeats(Booking booking) {
        Objects.requireNonNull(booking, "booking must not be null");
        if (booking.getId() == null) {
            return;
        }
        List<ShowtimeSeat> ownedSeats = showtimeSeatRepository.findAllByBookingIdForUpdate(booking.getId());
        for (ShowtimeSeat showtimeSeat : ownedSeats) {
            boolean ownedByBooking = showtimeSeat.getBooking() != null
                    && Objects.equals(showtimeSeat.getBooking().getId(), booking.getId());
            if (ownedByBooking && showtimeSeat.getSeatStatus() == ShowtimeSeatStatus.HELD) {
                showtimeSeat.setSeatStatus(ShowtimeSeatStatus.AVAILABLE);
                showtimeSeat.setLockedUntil(null);
                showtimeSeat.setBooking(null);
            }
        }
        showtimeSeatRepository.saveAll(ownedSeats);
    }

    private Customer requireActiveCustomer(String username) {
        if (username == null || username.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication is required");
        }
        Customer customer = customerRepository.findByAccountUsernameIgnoreCase(username.trim())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "The authenticated account is not a customer"
                ));
        if (!ACTIVE.equalsIgnoreCase(customer.getAccount().getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Customer account is inactive");
        }
        return customer;
    }

    private Showtime requireReservableShowtime(Long showtimeId, LocalDateTime now) {
        Showtime showtime = showtimeRepository.findById(showtimeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Showtime not found"));
        boolean activePath = AVAILABLE.equalsIgnoreCase(showtime.getStatus())
                && ACTIVE.equalsIgnoreCase(showtime.getMovie().getStatus())
                && ACTIVE.equalsIgnoreCase(showtime.getRoom().getStatus())
                && ACTIVE.equalsIgnoreCase(showtime.getRoom().getBranch().getStatus());
        if (!activePath || !showtime.getStartTime().isAfter(now)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This showtime is no longer available for booking"
            );
        }
        return showtime;
    }

    private List<Long> normalizeRequestedSeatIds(List<Long> showtimeSeatIds) {
        List<Long> sorted = showtimeSeatIds.stream().sorted().toList();
        if (new HashSet<>(sorted).size() != sorted.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate showtime seat IDs are not allowed");
        }
        return sorted;
    }

    private boolean isReservableSeat(ShowtimeSeat showtimeSeat, Showtime showtime) {
        return showtimeSeat.getSeatStatus() == ShowtimeSeatStatus.AVAILABLE
                && showtimeSeat.getBooking() == null
                && ACTIVE.equalsIgnoreCase(showtimeSeat.getSeat().getStatus())
                && ACTIVE.equalsIgnoreCase(showtimeSeat.getSeat().getSeatType().getStatus())
                && Objects.equals(
                        showtimeSeat.getSeat().getRoom().getRoomId(),
                        showtime.getRoom().getRoomId()
                );
    }

    private CinemaOrder createOrder(Customer customer, Showtime showtime, LocalDateTime now) {
        CinemaOrder order = new CinemaOrder();
        order.setCustomer(customer);
        order.setBranch(showtime.getRoom().getBranch());
        order.setOrderType("ONLINE_TICKET");
        order.setOrderTime(now);
        order.setTotalAmount(BigDecimal.ZERO.setScale(2));
        order.setPaymentStatus("UNPAID");
        order.setPickupStatus("NOT_REQUIRED");
        order.setStatus(BookingStatus.CREATED.name());
        return order;
    }

    private Booking createBooking(
            CinemaOrder order,
            Showtime showtime,
            LocalDateTime now,
            LocalDateTime holdExpiresAt
    ) {
        Booking booking = new Booking();
        booking.setBookingCode(generateBookingCode(now));
        booking.setOrder(order);
        booking.setShowtime(showtime);
        booking.setCreatedAt(now);
        booking.setUpdatedAt(now);
        booking.setHeldAt(now);
        booking.setHoldExpiresAt(holdExpiresAt);
        return booking;
    }

    private Ticket createHeldTicket(Booking booking, ShowtimeSeat showtimeSeat, BigDecimal price) {
        String entropy = compactUuid(8);
        Ticket ticket = new Ticket();
        ticket.setTicketCode("TKT-" + entropy + "-" + showtimeSeat.getShowtimeSeatId());
        ticket.setQrToken(UUID.randomUUID().toString());
        ticket.setBooking(booking);
        ticket.setShowtimeSeat(showtimeSeat);
        ticket.setPrice(price);
        ticket.setTicketStatus(TicketStatus.HELD);
        return ticket;
    }

    private BigDecimal calculateSeatPrice(Showtime showtime, ShowtimeSeat showtimeSeat) {
        BigDecimal basePrice = showtime.getPrice();
        BigDecimal multiplier = showtimeSeat.getSeat().getSeatType().getPriceMultiplier();
        if (basePrice == null || multiplier == null
                || basePrice.signum() < 0 || multiplier.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Seat price is invalid");
        }
        return money(basePrice.multiply(multiplier));
    }

    private String generateBookingCode(LocalDateTime now) {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = "BK-" + BOOKING_DATE.format(now.toLocalDate()) + "-" + compactUuid(6);
            if (bookingRepository.findByBookingCode(candidate).isEmpty()) {
                return candidate;
            }
        }
        return "BK-" + BOOKING_DATE.format(now.toLocalDate()) + "-" + compactUuid(12);
    }

    private String compactUuid(int length) {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, length)
                .toUpperCase(Locale.ROOT);
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
