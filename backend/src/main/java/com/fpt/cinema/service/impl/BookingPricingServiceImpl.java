package com.fpt.cinema.service.impl;

import com.fpt.cinema.dto.request.ApplyVoucherRequest;
import com.fpt.cinema.dto.request.BookingComboItemRequest;
import com.fpt.cinema.dto.request.UpdateBookingCombosRequest;
import com.fpt.cinema.dto.response.BookingResponse;
import com.fpt.cinema.entity.Booking;
import com.fpt.cinema.entity.Combo;
import com.fpt.cinema.entity.Customer;
import com.fpt.cinema.entity.OrderCombo;
import com.fpt.cinema.entity.OrderComboId;
import com.fpt.cinema.entity.Ticket;
import com.fpt.cinema.entity.Voucher;
import com.fpt.cinema.enums.BookingStatus;
import com.fpt.cinema.exception.BookingNotFoundException;
import com.fpt.cinema.exception.BookingOwnershipException;
import com.fpt.cinema.exception.InvalidVoucherException;
import com.fpt.cinema.exception.SeatHoldExpiredException;
import com.fpt.cinema.mapper.BookingMapper;
import com.fpt.cinema.repository.BookingRepository;
import com.fpt.cinema.repository.ComboRepository;
import com.fpt.cinema.repository.CustomerRepository;
import com.fpt.cinema.repository.OrderComboRepository;
import com.fpt.cinema.repository.TicketRepository;
import com.fpt.cinema.repository.VoucherRepository;
import com.fpt.cinema.repository.VoucherUsageRepository;
import com.fpt.cinema.service.BookingPricingService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookingPricingServiceImpl implements BookingPricingService {

    private static final String ACTIVE = "ACTIVE";
    private static final String FIXED_AMOUNT = "FIXED_AMOUNT";
    private static final String PERCENTAGE = "PERCENTAGE";
    private static final Set<BookingStatus> ACTIVE_VOUCHER_RESERVATION_STATUSES = Set.of(
            BookingStatus.SEAT_HELD,
            BookingStatus.PENDING_PAYMENT
    );

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final ComboRepository comboRepository;
    private final OrderComboRepository orderComboRepository;
    private final TicketRepository ticketRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherUsageRepository voucherUsageRepository;
    private final BookingMapper bookingMapper;
    private final Clock clock;

    public BookingPricingServiceImpl(
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            ComboRepository comboRepository,
            OrderComboRepository orderComboRepository,
            TicketRepository ticketRepository,
            VoucherRepository voucherRepository,
            VoucherUsageRepository voucherUsageRepository,
            BookingMapper bookingMapper,
            Clock clock
    ) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.comboRepository = comboRepository;
        this.orderComboRepository = orderComboRepository;
        this.ticketRepository = ticketRepository;
        this.voucherRepository = voucherRepository;
        this.voucherUsageRepository = voucherUsageRepository;
        this.bookingMapper = bookingMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public BookingResponse updateCombos(
            Long bookingId,
            UpdateBookingCombosRequest request,
            String username
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = requireCustomer(username);
        Booking booking = requireOwnedBookingForUpdate(bookingId, customer);
        requireModifiableHold(booking, now);

        Map<Long, Integer> requestedQuantities = validateAndIndexComboItems(request.items());
        Map<Long, Combo> combosById = loadAndValidateCombos(requestedQuantities.keySet());

        orderComboRepository.deleteAllByOrderId(booking.getOrder().getId());
        orderComboRepository.flush();

        List<OrderCombo> replacementItems = new ArrayList<>(requestedQuantities.size());
        for (Map.Entry<Long, Integer> item : requestedQuantities.entrySet()) {
            Combo combo = combosById.get(item.getKey());
            replacementItems.add(createOrderCombo(booking, combo, item.getValue()));
        }
        orderComboRepository.saveAll(replacementItems);

        BigDecimal comboSubtotal = replacementItems.stream()
                .map(OrderCombo::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        booking.setComboSubtotal(money(comboSubtotal));
        recalculateDiscountAndTotal(booking, now, customer, true);
        touchBookingAndOrder(booking, now);

        List<Ticket> tickets = ticketRepository.findAllByBookingIdOrderByIdAsc(booking.getId());
        return bookingMapper.toBookingResponse(booking, tickets, replacementItems, now);
    }

    @Override
    @Transactional
    public BookingResponse applyVoucher(
            Long bookingId,
            ApplyVoucherRequest request,
            String username
    ) {
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = requireCustomer(username);
        Booking booking = requireOwnedBookingForUpdate(bookingId, customer);
        requireModifiableHold(booking, now);

        Voucher voucher = voucherRepository.findByCodeForUpdate(request.voucherCode().trim())
                .orElseThrow(() -> new InvalidVoucherException("Voucher code does not exist"));
        BigDecimal grossAmount = grossAmount(booking);
        BigDecimal discount = validateAndCalculateVoucher(booking, voucher, customer, grossAmount, now);

        booking.setAppliedVoucher(voucher);
        booking.setDiscountAmount(discount);
        booking.setTotalAmount(money(grossAmount.subtract(discount).max(BigDecimal.ZERO)));
        touchBookingAndOrder(booking, now);

        return currentBookingResponse(booking, now);
    }

    @Override
    @Transactional
    public BookingResponse removeVoucher(Long bookingId, String username) {
        LocalDateTime now = LocalDateTime.now(clock);
        Customer customer = requireCustomer(username);
        Booking booking = requireOwnedBookingForUpdate(bookingId, customer);
        requireModifiableHold(booking, now);

        booking.setAppliedVoucher(null);
        booking.setDiscountAmount(BigDecimal.ZERO.setScale(2));
        booking.setTotalAmount(grossAmount(booking));
        touchBookingAndOrder(booking, now);

        return currentBookingResponse(booking, now);
    }

    @Override
    @Transactional
    public void recalculateForCheckout(Booking booking, LocalDateTime now) {
        Objects.requireNonNull(booking, "booking must not be null");
        Objects.requireNonNull(now, "now must not be null");

        List<Ticket> tickets = ticketRepository.findAllByBookingIdForUpdate(booking.getId());
        if (tickets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking has no reserved seats");
        }

        BigDecimal seatSubtotal = BigDecimal.ZERO;
        for (Ticket ticket : tickets) {
            var showtimeSeat = ticket.getShowtimeSeat();
            if (!ACTIVE.equalsIgnoreCase(showtimeSeat.getSeat().getStatus())
                    || !ACTIVE.equalsIgnoreCase(showtimeSeat.getSeat().getSeatType().getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A selected seat is no longer active");
            }
            BigDecimal price = calculateSeatPrice(booking, ticket);
            ticket.setPrice(price);
            seatSubtotal = seatSubtotal.add(price);
        }
        ticketRepository.saveAll(tickets);
        booking.setSeatSubtotal(money(seatSubtotal));

        List<OrderCombo> orderCombos = orderComboRepository
                .findAllByOrderIdOrderByComboIdAsc(booking.getOrder().getId());
        BigDecimal comboSubtotal = BigDecimal.ZERO;
        for (OrderCombo orderCombo : orderCombos) {
            Combo combo = orderCombo.getCombo();
            validateActiveCombo(combo);
            if (orderCombo.getQuantity() == null || orderCombo.getQuantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Combo quantity is invalid");
            }
            BigDecimal lineTotal = money(combo.getPrice().multiply(BigDecimal.valueOf(orderCombo.getQuantity())));
            orderCombo.setUnitPrice(money(combo.getPrice()));
            orderCombo.setLineTotal(lineTotal);
            comboSubtotal = comboSubtotal.add(lineTotal);
        }
        orderComboRepository.saveAll(orderCombos);
        booking.setComboSubtotal(money(comboSubtotal));

        Customer customer = booking.getOrder().getCustomer();
        if (customer == null) {
            throw new BookingOwnershipException(booking.getId());
        }
        recalculateDiscountAndTotal(booking, now, customer, true);
        touchBookingAndOrder(booking, now);
    }

    private BookingResponse currentBookingResponse(Booking booking, LocalDateTime now) {
        List<Ticket> tickets = ticketRepository.findAllByBookingIdOrderByIdAsc(booking.getId());
        List<OrderCombo> orderCombos = orderComboRepository
                .findAllByOrderIdOrderByComboIdAsc(booking.getOrder().getId());
        return bookingMapper.toBookingResponse(booking, tickets, orderCombos, now);
    }

    private Customer requireCustomer(String username) {
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

    private Booking requireOwnedBookingForUpdate(Long bookingId, Customer customer) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
        Customer owner = booking.getOrder().getCustomer();
        if (owner == null || !Objects.equals(owner.getCustomerId(), customer.getCustomerId())) {
            throw new BookingOwnershipException(bookingId);
        }
        return booking;
    }

    private void requireModifiableHold(Booking booking, LocalDateTime now) {
        if (booking.getStatus() != BookingStatus.SEAT_HELD) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking can only be modified while its seats are held"
            );
        }
        if (booking.getHoldExpiresAt() == null || !now.isBefore(booking.getHoldExpiresAt())) {
            throw new SeatHoldExpiredException(booking.getId());
        }
    }

    private Map<Long, Integer> validateAndIndexComboItems(List<BookingComboItemRequest> items) {
        Map<Long, Integer> quantities = new HashMap<>();
        for (BookingComboItemRequest item : items) {
            if (item == null || item.comboId() == null || item.quantity() == null || item.quantity() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Combo ID and quantity must be positive");
            }
            if (quantities.putIfAbsent(item.comboId(), item.quantity()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate combo IDs are not allowed");
            }
        }
        return quantities;
    }

    private Map<Long, Combo> loadAndValidateCombos(Set<Long> comboIds) {
        if (comboIds.isEmpty()) {
            return Map.of();
        }
        List<Combo> combos = comboRepository.findAllById(new HashSet<>(comboIds));
        if (combos.size() != comboIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more combos do not exist");
        }
        combos.forEach(this::validateActiveCombo);
        return combos.stream().collect(Collectors.toMap(Combo::getId, Function.identity()));
    }

    private void validateActiveCombo(Combo combo) {
        if (!ACTIVE.equalsIgnoreCase(combo.getStatus())
                || combo.getPrice() == null
                || combo.getPrice().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Combo is inactive or invalid");
        }
    }

    private OrderCombo createOrderCombo(Booking booking, Combo combo, int quantity) {
        BigDecimal unitPrice = money(combo.getPrice());
        OrderComboId id = new OrderComboId();
        id.setOrderId(booking.getOrder().getId());
        id.setComboId(combo.getId());

        OrderCombo orderCombo = new OrderCombo();
        orderCombo.setId(id);
        orderCombo.setOrder(booking.getOrder());
        orderCombo.setCombo(combo);
        orderCombo.setQuantity(quantity);
        orderCombo.setUnitPrice(unitPrice);
        orderCombo.setLineTotal(money(unitPrice.multiply(BigDecimal.valueOf(quantity))));
        return orderCombo;
    }

    private void recalculateDiscountAndTotal(
            Booking booking,
            LocalDateTime now,
            Customer customer,
            boolean lockVoucher
    ) {
        BigDecimal gross = grossAmount(booking);
        Voucher appliedVoucher = booking.getAppliedVoucher();
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        if (appliedVoucher != null) {
            Voucher currentVoucher = lockVoucher
                    ? voucherRepository.findByIdForUpdate(appliedVoucher.getId())
                    .orElseThrow(InvalidVoucherException::new)
                    : appliedVoucher;
            discount = validateAndCalculateVoucher(booking, currentVoucher, customer, gross, now);
            booking.setAppliedVoucher(currentVoucher);
        }
        booking.setDiscountAmount(discount);
        booking.setTotalAmount(money(gross.subtract(discount).max(BigDecimal.ZERO)));
    }

    private BigDecimal validateAndCalculateVoucher(
            Booking booking,
            Voucher voucher,
            Customer customer,
            BigDecimal gross,
            LocalDateTime now
    ) {
        var promotion = voucher.getPromotion();
        boolean active = ACTIVE.equalsIgnoreCase(voucher.getStatus())
                && ACTIVE.equalsIgnoreCase(promotion.getStatus());
        boolean inVoucherPeriod = !now.isBefore(voucher.getValidFrom()) && !now.isAfter(voucher.getValidTo());
        boolean inPromotionPeriod = !now.isBefore(promotion.getStartDate()) && !now.isAfter(promotion.getEndDate());
        if (!active || !inVoucherPeriod || !inPromotionPeriod) {
            throw new InvalidVoucherException("Voucher is inactive or outside its valid period");
        }

        BigDecimal minimum = voucher.getMinimumOrderAmount() == null
                ? BigDecimal.ZERO
                : voucher.getMinimumOrderAmount();
        if (gross.compareTo(minimum) < 0) {
            throw new InvalidVoucherException("Booking amount does not meet the voucher minimum");
        }
        if (voucher.getUsageLimit() == null || voucher.getUsedCount() == null
                || voucher.getUsageLimit() <= 0
                || voucher.getUsedCount() >= voucher.getUsageLimit()) {
            throw new InvalidVoucherException("Voucher usage limit has been reached");
        }
        long activeReservations = bookingRepository.countActiveVoucherReservations(
                voucher.getId(),
                ACTIVE_VOUCHER_RESERVATION_STATUSES,
                booking.getId()
        );
        if ((long) voucher.getUsedCount() + activeReservations >= voucher.getUsageLimit()) {
            throw new InvalidVoucherException("All remaining voucher uses are currently reserved");
        }
        if (voucherUsageRepository.countByVoucherAndCustomer(
                voucher.getId(),
                customer.getCustomerId()
        ) > 0) {
            throw new InvalidVoucherException("This voucher has already been used by the customer");
        }
        if (bookingRepository.countActiveCustomerVoucherReservations(
                voucher.getId(),
                customer.getCustomerId(),
                ACTIVE_VOUCHER_RESERVATION_STATUSES,
                booking.getId()
        ) > 0) {
            throw new InvalidVoucherException("This voucher is already reserved by the customer");
        }

        BigDecimal configuredValue = voucher.getDiscountValue();
        if (configuredValue == null || configuredValue.signum() < 0) {
            throw new InvalidVoucherException("Voucher discount value is invalid");
        }

        BigDecimal discount;
        if (FIXED_AMOUNT.equalsIgnoreCase(promotion.getDiscountType())) {
            discount = configuredValue;
        } else if (PERCENTAGE.equalsIgnoreCase(promotion.getDiscountType())) {
            if (configuredValue.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new InvalidVoucherException("Voucher percentage is invalid");
            }
            discount = gross.multiply(configuredValue)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        } else {
            throw new InvalidVoucherException("Voucher discount type is not supported");
        }
        return money(discount.min(gross).max(BigDecimal.ZERO));
    }

    private BigDecimal calculateSeatPrice(Booking booking, Ticket ticket) {
        if (!Objects.equals(
                ticket.getShowtimeSeat().getShowtime().getShowtimeId(),
                booking.getShowtime().getShowtimeId()
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking contains a seat from another showtime");
        }
        BigDecimal basePrice = booking.getShowtime().getPrice();
        BigDecimal multiplier = ticket.getShowtimeSeat().getSeat().getSeatType().getPriceMultiplier();
        if (basePrice == null || multiplier == null || basePrice.signum() < 0 || multiplier.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Seat price is invalid");
        }
        return money(basePrice.multiply(multiplier));
    }

    private BigDecimal grossAmount(Booking booking) {
        BigDecimal seatSubtotal = booking.getSeatSubtotal() == null ? BigDecimal.ZERO : booking.getSeatSubtotal();
        BigDecimal comboSubtotal = booking.getComboSubtotal() == null ? BigDecimal.ZERO : booking.getComboSubtotal();
        return money(seatSubtotal.add(comboSubtotal));
    }

    private void touchBookingAndOrder(Booking booking, LocalDateTime now) {
        booking.setUpdatedAt(now);
        booking.getOrder().setTotalAmount(booking.getTotalAmount());
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
