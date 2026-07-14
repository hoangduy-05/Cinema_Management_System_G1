package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.request.ApplyVoucherRequest;
import com.fpt.cinema.dto.request.BookingComboItemRequest;
import com.fpt.cinema.dto.request.UpdateBookingCombosRequest;
import com.fpt.cinema.entity.Account;
import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.BookingStateMachine;
import com.fpt.cinema.entity.CinemaOrder;
import com.fpt.cinema.entity.Combo;
import com.fpt.cinema.entity.Customer;
import com.fpt.cinema.entity.OrderCombo;
import com.fpt.cinema.entity.Promotion;
import com.fpt.cinema.entity.Seat;
import com.fpt.cinema.entity.SeatType;
import com.fpt.cinema.entity.Showtime;
import com.fpt.cinema.entity.ShowtimeSeat;
import com.fpt.cinema.entity.Ticket;
import com.fpt.cinema.entity.Voucher;
import com.fpt.cinema.enums.BookingStatus;
import com.fpt.cinema.exception.InvalidVoucherException;
import com.fpt.cinema.mapper.BookingMapper;
import com.fpt.cinema.repository.BookingRepository;
import com.fpt.cinema.repository.ComboRepository;
import com.fpt.cinema.repository.CustomerRepository;
import com.fpt.cinema.repository.OrderComboRepository;
import com.fpt.cinema.repository.TicketRepository;
import com.fpt.cinema.repository.VoucherRepository;
import com.fpt.cinema.repository.VoucherUsageRepository;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingPricingServiceImplTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-14T03:00:00Z"),
            ZoneId.of("Asia/Bangkok")
    );
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 14, 10, 0);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private ComboRepository comboRepository;
    @Mock
    private OrderComboRepository orderComboRepository;
    @Mock
    private TicketRepository ticketRepository;
    @Mock
    private VoucherRepository voucherRepository;
    @Mock
    private VoucherUsageRepository voucherUsageRepository;
    @Mock
    private BookingMapper bookingMapper;

    private BookingPricingServiceImpl service;
    private Customer customer;
    private Booking booking;

    @BeforeEach
    void setUp() {
        service = new BookingPricingServiceImpl(
                bookingRepository,
                customerRepository,
                comboRepository,
                orderComboRepository,
                ticketRepository,
                voucherRepository,
                voucherUsageRepository,
                bookingMapper,
                CLOCK
        );
        customer = customer();
        booking = heldBooking(customer);
    }

    @Test
    void appliesValidFixedVoucherWithoutFinalizingUsage() {
        Voucher voucher = voucher("FIXED_AMOUNT", "20000.00");
        stubOwnedBooking();
        when(voucherRepository.findByCodeForUpdate("SUMMER2026")).thenReturn(Optional.of(voucher));

        service.applyVoucher(booking.getId(), new ApplyVoucherRequest(" SUMMER2026 "), "customer");

        assertSame(voucher, booking.getAppliedVoucher());
        assertEquals(new BigDecimal("20000.00"), booking.getDiscountAmount());
        assertEquals(new BigDecimal("70000.00"), booking.getTotalAmount());
        assertEquals(booking.getTotalAmount(), booking.getOrder().getTotalAmount());
        verify(voucherUsageRepository, never()).save(any());
    }

    @Test
    void capsDiscountSoBookingTotalNeverBecomesNegative() {
        Voucher voucher = voucher("FIXED_AMOUNT", "500000.00");
        stubOwnedBooking();
        when(voucherRepository.findByCodeForUpdate("BIG")).thenReturn(Optional.of(voucher));

        service.applyVoucher(booking.getId(), new ApplyVoucherRequest("BIG"), "customer");

        assertEquals(new BigDecimal("90000.00"), booking.getDiscountAmount());
        assertEquals(new BigDecimal("0.00"), booking.getTotalAmount());
    }

    @Test
    void rejectsExpiredVoucher() {
        Voucher voucher = voucher("FIXED_AMOUNT", "20000.00");
        voucher.setValidTo(NOW.minusSeconds(1));
        stubOwnedBooking();
        when(voucherRepository.findByCodeForUpdate("OLD")).thenReturn(Optional.of(voucher));

        assertThrows(
                InvalidVoucherException.class,
                () -> service.applyVoucher(booking.getId(), new ApplyVoucherRequest("OLD"), "customer")
        );
        assertEquals(new BigDecimal("90000.00"), booking.getTotalAmount());
    }

    @Test
    void rejectsVoucherWhenMinimumOrderIsNotMet() {
        Voucher voucher = voucher("FIXED_AMOUNT", "20000.00");
        voucher.setMinimumOrderAmount(new BigDecimal("100000.00"));
        stubOwnedBooking();
        when(voucherRepository.findByCodeForUpdate("MINIMUM")).thenReturn(Optional.of(voucher));

        assertThrows(
                InvalidVoucherException.class,
                () -> service.applyVoucher(booking.getId(), new ApplyVoucherRequest("MINIMUM"), "customer")
        );
    }

    @Test
    void rejectsVoucherWhenAllRemainingUsesAreReservedByLiveBookings() {
        Voucher voucher = voucher("FIXED_AMOUNT", "20000.00");
        voucher.setUsageLimit(1);
        stubOwnedBooking();
        when(voucherRepository.findByCodeForUpdate("RESERVED")).thenReturn(Optional.of(voucher));
        when(bookingRepository.countActiveVoucherReservations(
                voucher.getId(),
                Set.of(BookingStatus.SEAT_HELD, BookingStatus.PENDING_PAYMENT),
                booking.getId()
        )).thenReturn(1L);

        assertThrows(
                InvalidVoucherException.class,
                () -> service.applyVoucher(booking.getId(), new ApplyVoucherRequest("RESERVED"), "customer")
        );
    }

    @Test
    void rejectsSecondLiveVoucherReservationForSameCustomer() {
        Voucher voucher = voucher("FIXED_AMOUNT", "20000.00");
        stubOwnedBooking();
        when(voucherRepository.findByCodeForUpdate("CUSTOMER-RESERVED"))
                .thenReturn(Optional.of(voucher));
        when(bookingRepository.countActiveCustomerVoucherReservations(
                voucher.getId(),
                customer.getCustomerId(),
                Set.of(BookingStatus.SEAT_HELD, BookingStatus.PENDING_PAYMENT),
                booking.getId()
        )).thenReturn(1L);

        assertThrows(
                InvalidVoucherException.class,
                () -> service.applyVoucher(
                        booking.getId(),
                        new ApplyVoucherRequest("CUSTOMER-RESERVED"),
                        "customer"
                )
        );
    }

    @Test
    void updateCombosUsesCurrentDatabasePriceAndRecalculatesTotal() {
        Combo combo = combo(10L, "35000.00");
        stubOwnedBooking();
        when(comboRepository.findAllById(any())).thenReturn(List.of(combo));

        service.updateCombos(
                booking.getId(),
                new UpdateBookingCombosRequest(List.of(new BookingComboItemRequest(10L, 2))),
                "customer"
        );

        assertEquals(new BigDecimal("70000.00"), booking.getComboSubtotal());
        assertEquals(new BigDecimal("160000.00"), booking.getTotalAmount());
        ArgumentCaptor<Iterable<OrderCombo>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(orderComboRepository).saveAll(captor.capture());
        OrderCombo saved = captor.getValue().iterator().next();
        assertEquals(new BigDecimal("35000.00"), saved.getUnitPrice());
        assertEquals(new BigDecimal("70000.00"), saved.getLineTotal());
    }

    @Test
    void checkoutRecalculatesSeatComboVoucherAndTotalFromDatabaseValues() {
        booking.getShowtime().setPrice(new BigDecimal("90000.00"));
        Ticket ticket = ticket(booking.getShowtime(), "1.50", new BigDecimal("1.00"));
        Combo combo = combo(10L, "40000.00");
        OrderCombo orderCombo = orderCombo(booking.getOrder(), combo, 2, new BigDecimal("1.00"));
        Voucher voucher = voucher("PERCENTAGE", "10.00");
        booking.setAppliedVoucher(voucher);
        when(ticketRepository.findAllByBookingIdForUpdate(booking.getId())).thenReturn(List.of(ticket));
        when(orderComboRepository.findAllByOrderIdOrderByComboIdAsc(booking.getOrder().getId()))
                .thenReturn(List.of(orderCombo));
        when(voucherRepository.findByIdForUpdate(voucher.getId())).thenReturn(Optional.of(voucher));

        service.recalculateForCheckout(booking, NOW);

        assertEquals(new BigDecimal("135000.00"), ticket.getPrice());
        assertEquals(new BigDecimal("135000.00"), booking.getSeatSubtotal());
        assertEquals(new BigDecimal("80000.00"), orderCombo.getLineTotal());
        assertEquals(new BigDecimal("80000.00"), booking.getComboSubtotal());
        assertEquals(new BigDecimal("21500.00"), booking.getDiscountAmount());
        assertEquals(new BigDecimal("193500.00"), booking.getTotalAmount());
    }

    @Test
    void checkoutRejectsSeatFromAnotherShowtime() {
        Showtime anotherShowtime = new Showtime();
        anotherShowtime.setShowtimeId(9999L);
        Ticket ticket = ticket(anotherShowtime, "1.00", new BigDecimal("90000.00"));
        when(ticketRepository.findAllByBookingIdForUpdate(booking.getId())).thenReturn(List.of(ticket));

        assertThrows(
                ResponseStatusException.class,
                () -> service.recalculateForCheckout(booking, NOW)
        );
        verify(orderComboRepository, never()).saveAll(any());
    }

    private void stubOwnedBooking() {
        when(customerRepository.findByAccountUsernameIgnoreCase("customer"))
                .thenReturn(Optional.of(customer));
        when(bookingRepository.findByIdForUpdate(booking.getId())).thenReturn(Optional.of(booking));
    }

    private Booking heldBooking(Customer owner) {
        CinemaOrder order = new CinemaOrder();
        order.setId(7001L);
        order.setCustomer(owner);
        order.setTotalAmount(new BigDecimal("90000.00"));
        Showtime showtime = new Showtime();
        showtime.setShowtimeId(3001L);
        showtime.setPrice(new BigDecimal("90000.00"));
        Booking result = new Booking();
        result.setId(8001L);
        result.setOrder(order);
        result.setShowtime(showtime);
        result.setSeatSubtotal(new BigDecimal("90000.00"));
        result.setComboSubtotal(BigDecimal.ZERO.setScale(2));
        result.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        result.setTotalAmount(new BigDecimal("90000.00"));
        result.setHoldExpiresAt(NOW.plusMinutes(10));
        new BookingStateMachine().transition(result, BookingStatus.SEAT_HELD);
        return result;
    }

    private Customer customer() {
        Account account = new Account();
        account.setUsername("customer");
        account.setStatus("ACTIVE");
        Customer result = new Customer();
        result.setCustomerId(1001L);
        result.setAccount(account);
        return result;
    }

    private Voucher voucher(String discountType, String discountValue) {
        Promotion promotion = new Promotion();
        promotion.setStatus("ACTIVE");
        promotion.setDiscountType(discountType);
        promotion.setStartDate(NOW.minusDays(1));
        promotion.setEndDate(NOW.plusDays(1));
        Voucher voucher = new Voucher();
        voucher.setId(1L);
        voucher.setCode("SUMMER2026");
        voucher.setPromotion(promotion);
        voucher.setStatus("ACTIVE");
        voucher.setDiscountValue(new BigDecimal(discountValue));
        voucher.setMinimumOrderAmount(BigDecimal.ZERO);
        voucher.setUsageLimit(100);
        voucher.setUsedCount(0);
        voucher.setValidFrom(NOW.minusDays(1));
        voucher.setValidTo(NOW.plusDays(1));
        return voucher;
    }

    private Combo combo(Long id, String price) {
        Combo combo = new Combo();
        combo.setId(id);
        combo.setName("Combo");
        combo.setPrice(new BigDecimal(price));
        combo.setStatus("ACTIVE");
        return combo;
    }

    private OrderCombo orderCombo(CinemaOrder order, Combo combo, int quantity, BigDecimal stalePrice) {
        OrderCombo result = new OrderCombo();
        result.setOrder(order);
        result.setCombo(combo);
        result.setQuantity(quantity);
        result.setUnitPrice(stalePrice);
        result.setLineTotal(stalePrice.multiply(BigDecimal.valueOf(quantity)));
        return result;
    }

    private Ticket ticket(Showtime showtime, String multiplier, BigDecimal stalePrice) {
        SeatType seatType = new SeatType();
        seatType.setPriceMultiplier(new BigDecimal(multiplier));
        seatType.setStatus("ACTIVE");
        Seat seat = new Seat();
        seat.setSeatType(seatType);
        seat.setStatus("ACTIVE");
        ShowtimeSeat showtimeSeat = new ShowtimeSeat();
        showtimeSeat.setShowtime(showtime);
        showtimeSeat.setSeat(seat);
        Ticket ticket = new Ticket();
        ticket.setShowtimeSeat(showtimeSeat);
        ticket.setPrice(stalePrice);
        return ticket;
    }
}
