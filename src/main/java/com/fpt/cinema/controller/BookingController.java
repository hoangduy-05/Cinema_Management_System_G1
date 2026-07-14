package com.fpt.cinema.controller;

import com.fpt.cinema.apiresponse.ApiResponse;
import com.fpt.cinema.dto.request.ApplyVoucherRequest;
import com.fpt.cinema.dto.request.CheckoutRequest;
import com.fpt.cinema.dto.request.HoldSeatsRequest;
import com.fpt.cinema.dto.request.PaymentRetryRequest;
import com.fpt.cinema.dto.request.UpdateBookingCombosRequest;
import com.fpt.cinema.dto.response.BookingResponse;
import com.fpt.cinema.dto.response.BookingSummaryResponse;
import com.fpt.cinema.dto.response.PaymentResponse;
import com.fpt.cinema.service.BookingPricingService;
import com.fpt.cinema.service.BookingService;
import com.fpt.cinema.service.PaymentService;
import com.fpt.cinema.service.SeatReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/bookings")
@Tag(name = "Customer bookings", description = "Authenticated seat hold, pricing, checkout and cancellation lifecycle")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final SeatReservationService seatReservationService;
    private final BookingPricingService bookingPricingService;
    private final BookingService bookingService;
    private final PaymentService paymentService;

    public BookingController(
            SeatReservationService seatReservationService,
            BookingPricingService bookingPricingService,
            BookingService bookingService,
            PaymentService paymentService
    ) {
        this.seatReservationService = seatReservationService;
        this.bookingPricingService = bookingPricingService;
        this.bookingService = bookingService;
        this.paymentService = paymentService;
    }

    @PostMapping("/holds")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a booking and atomically hold available seats", description = "Creates CREATED, then transitions it to SEAT_HELD. Returns 409 if any seat is unavailable.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Booking created in SEAT_HELD"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid seat selection"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Authenticated account is not an active customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Showtime or seat not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Showtime unavailable or at least one seat is not AVAILABLE")
    })
    public ApiResponse<BookingResponse> holdSeats(
            @Valid @RequestBody HoldSeatsRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "Seats held successfully",
                seatReservationService.holdSeats(request, authentication.getName())
        );
    }

    @PutMapping("/{bookingId}/combos")
    @Operation(summary = "Replace selected combos", description = "Allowed only while the booking is SEAT_HELD.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Combos updated; booking remains SEAT_HELD"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid combo request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking belongs to another customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking or combo not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking is not a live SEAT_HELD booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Combo is inactive or violates a business rule")
    })
    public ApiResponse<BookingResponse> updateCombos(
            @PathVariable @Positive Long bookingId,
            @Valid @RequestBody UpdateBookingCombosRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "Booking combos updated successfully",
                bookingPricingService.updateCombos(bookingId, request, authentication.getName())
        );
    }

    @PostMapping("/{bookingId}/voucher")
    @Operation(summary = "Apply a voucher", description = "Validates but does not permanently consume the voucher while SEAT_HELD.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Voucher reserved; booking remains SEAT_HELD"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking belongs to another customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking is not a live SEAT_HELD booking"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Voucher is invalid, unavailable, expired or already used/reserved")
    })
    public ApiResponse<BookingResponse> applyVoucher(
            @PathVariable @Positive Long bookingId,
            @Valid @RequestBody ApplyVoucherRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "Voucher applied successfully",
                bookingPricingService.applyVoucher(bookingId, request, authentication.getName())
        );
    }

    @DeleteMapping("/{bookingId}/voucher")
    @Operation(summary = "Remove an applied voucher", description = "Allowed only while the booking is SEAT_HELD.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Voucher removed; booking remains SEAT_HELD"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking belongs to another customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking is not a live SEAT_HELD booking")
    })
    public ApiResponse<BookingResponse> removeVoucher(
            @PathVariable @Positive Long bookingId,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "Voucher removed successfully",
                bookingPricingService.removeVoucher(bookingId, authentication.getName())
        );
    }

    @GetMapping("/{bookingId}/summary")
    @Operation(summary = "Get the authenticated customer's booking summary")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Owner-safe booking summary"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking belongs to another customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found")
    })
    public ApiResponse<BookingSummaryResponse> getSummary(
            @PathVariable @Positive Long bookingId,
            Authentication authentication
    ) {
        return ApiResponse.success(
                bookingService.getSummary(bookingId, authentication.getName())
        );
    }

    @PostMapping("/{bookingId}/checkout")
    @Operation(summary = "Confirm checkout and create/reuse a payment attempt", description = "Transitions SEAT_HELD to PENDING_PAYMENT after recalculating all totals from the database.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment attempt returned; booking is PENDING_PAYMENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking belongs to another customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Expired hold, unavailable seat or invalid booking transition"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Combo or voucher is no longer valid")
    })
    public ApiResponse<PaymentResponse> checkout(
            @PathVariable @Positive Long bookingId,
            @Valid @RequestBody(required = false) CheckoutRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "Checkout created successfully",
                bookingService.checkout(bookingId, request, authentication.getName())
        );
    }

    @PostMapping("/{bookingId}/payments/retry")
    @Operation(summary = "Retry payment before the payment deadline", description = "Uses the allowed PENDING_PAYMENT self-transition and keeps seats held.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment attempt returned; booking remains PENDING_PAYMENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking belongs to another customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Payment deadline passed, successful payment exists or transition is invalid")
    })
    public ApiResponse<PaymentResponse> retryPayment(
            @PathVariable @Positive Long bookingId,
            @Valid @RequestBody(required = false) PaymentRetryRequest request,
            Authentication authentication
    ) {
        String method = request == null ? null : request.paymentMethod();
        return ApiResponse.success(
                "Payment retry created successfully",
                paymentService.retryPayment(bookingId, authentication.getName(), method)
        );
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel an unconfirmed booking", description = "Transitions SEAT_HELD or PENDING_PAYMENT to CANCELLED and releases only HELD seats.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Booking is CANCELLED; repeated cancellation is idempotent"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking belongs to another customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking cannot transition to CANCELLED")
    })
    public ApiResponse<BookingResponse> cancel(
            @PathVariable @Positive Long bookingId,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "Booking cancelled successfully",
                bookingService.cancel(bookingId, authentication.getName())
        );
    }
}
