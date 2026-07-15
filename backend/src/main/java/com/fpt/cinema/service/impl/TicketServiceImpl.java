package com.fpt.cinema.service.impl;

import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.ShowtimeSeat;
import com.fpt.cinema.entity.Ticket;
import com.fpt.cinema.enums.ShowtimeSeatStatus;
import com.fpt.cinema.enums.TicketStatus;
import com.fpt.cinema.repository.TicketRepository;
import com.fpt.cinema.service.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class TicketServiceImpl implements TicketService {

    private static final String TICKET_CODE_PREFIX = "TKT-";

    private final TicketRepository ticketRepository;

    public TicketServiceImpl(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Override
    @Transactional
    public List<Ticket> issueOrActivateTickets(Booking booking, List<ShowtimeSeat> bookedSeats) {
        Objects.requireNonNull(booking, "booking must not be null");
        Objects.requireNonNull(bookedSeats, "bookedSeats must not be null");
        if (booking.getId() == null) {
            throw new IllegalArgumentException("Booking must be persisted before tickets are issued");
        }

        List<Ticket> existingTickets = ticketRepository.findAllByBookingIdForUpdate(booking.getId());
        Map<Long, Ticket> ticketBySeatId = new HashMap<>();
        for (Ticket ticket : existingTickets) {
            ticketBySeatId.put(ticket.getShowtimeSeat().getShowtimeSeatId(), ticket);
        }

        List<Ticket> issuedTickets = new ArrayList<>(bookedSeats.size());
        for (ShowtimeSeat bookedSeat : bookedSeats) {
            validateBookedSeatOwnership(booking, bookedSeat);
            Ticket ticket = ticketBySeatId.get(bookedSeat.getShowtimeSeatId());
            if (ticket == null) {
                ticket = new Ticket();
                ticket.setBooking(booking);
                ticket.setShowtimeSeat(bookedSeat);
                ticket.setPrice(bookingSeatPrice(bookedSeat));
                ticket.setTicketCode(newTicketCode());
                ticket.setQrToken(UUID.randomUUID().toString());
            } else if (ticket.getTicketStatus() != TicketStatus.HELD
                    && ticket.getTicketStatus() != TicketStatus.VALID) {
                throw new IllegalStateException("Only a held ticket can be issued");
            }
            ticket.setTicketStatus(TicketStatus.VALID);
            issuedTickets.add(ticket);
        }

        List<Ticket> savedTickets = ticketRepository.saveAll(issuedTickets);
        savedTickets.sort(Comparator.comparing(Ticket::getId));
        return List.copyOf(savedTickets);
    }

    @Override
    @Transactional
    public void expireHeldTickets(Booking booking) {
        updateHeldTicketStatus(booking, TicketStatus.EXPIRED);
    }

    @Override
    @Transactional
    public void cancelHeldTickets(Booking booking) {
        updateHeldTicketStatus(booking, TicketStatus.CANCELLED);
    }

    private void updateHeldTicketStatus(Booking booking, TicketStatus targetStatus) {
        Objects.requireNonNull(booking, "booking must not be null");
        if (booking.getId() == null) {
            return;
        }
        List<Ticket> tickets = ticketRepository.findAllByBookingIdForUpdate(booking.getId());
        for (Ticket ticket : tickets) {
            if (ticket.getTicketStatus() == TicketStatus.HELD) {
                ticket.setTicketStatus(targetStatus);
            }
        }
    }

    private void validateBookedSeatOwnership(Booking booking, ShowtimeSeat seat) {
        if (seat == null
                || seat.getBooking() == null
                || !Objects.equals(seat.getBooking().getId(), booking.getId())
                || seat.getSeatStatus() != ShowtimeSeatStatus.BOOKED) {
            throw new IllegalStateException("A ticket can only be issued for a booked seat owned by the booking");
        }
    }

    private BigDecimal bookingSeatPrice(ShowtimeSeat seat) {
        BigDecimal basePrice = seat.getShowtime().getPrice();
        BigDecimal multiplier = seat.getSeat().getSeatType().getPriceMultiplier();
        if (basePrice == null || multiplier == null
                || basePrice.signum() < 0 || multiplier.signum() < 0) {
            throw new IllegalStateException("Cannot issue a ticket with an invalid seat price");
        }
        return basePrice.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    private String newTicketCode() {
        return TICKET_CODE_PREFIX + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }
}
