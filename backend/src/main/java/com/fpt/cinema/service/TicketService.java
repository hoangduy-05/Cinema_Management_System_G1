package com.fpt.cinema.service;

import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.ShowtimeSeat;
import com.fpt.cinema.entity.Ticket;

import java.util.List;

public interface TicketService {

    List<Ticket> issueOrActivateTickets(Booking booking, List<ShowtimeSeat> bookedSeats);

    void expireHeldTickets(Booking booking);

    void cancelHeldTickets(Booking booking);
}
