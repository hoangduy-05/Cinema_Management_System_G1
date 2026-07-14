package com.fpt.cinema.service.impl;

import com.fpt.cinema.entity.Ticket;
import com.fpt.cinema.repository.TicketRepository;
import com.fpt.cinema.service.TicketDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DevelopmentTicketDeliveryService implements TicketDeliveryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevelopmentTicketDeliveryService.class);

    private final TicketRepository ticketRepository;

    public DevelopmentTicketDeliveryService(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public void deliverTickets(Long bookingId) {
        List<Ticket> tickets = ticketRepository.findAllByBookingIdOrderByIdAsc(bookingId);
        List<Long> ticketIds = tickets.stream().map(Ticket::getId).toList();
        List<String> ticketCodes = tickets.stream().map(Ticket::getTicketCode).toList();
        LOGGER.info(
                "Development e-ticket delivery prepared: bookingId={}, ticketIds={}, ticketCodes={}",
                bookingId,
                ticketIds,
                ticketCodes
        );
    }
}
