package com.fpt.cinema.controller;

import com.fpt.cinema.apiresponse.ApiResponse;
import com.fpt.cinema.dto.request.CreatePaymentRequest;
import com.fpt.cinema.dto.request.PaymentCallbackRequest;
import com.fpt.cinema.dto.request.PaymentRetryRequest;
import com.fpt.cinema.dto.response.PaymentCallbackResponse;
import com.fpt.cinema.dto.response.PaymentResponse;
import com.fpt.cinema.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Idempotent simulated payment attempts and callbacks")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create or reuse a payment attempt for a pending booking")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Pending payment attempt created or reused"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking belongs to another customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Booking not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Booking is not PENDING_PAYMENT or deadline passed")
    })
    public ApiResponse<PaymentResponse> createPayment(
            @Valid @RequestBody CreatePaymentRequest request,
            Authentication authentication
    ) {
        return ApiResponse.success(
                "Payment attempt ready",
                paymentService.createPayment(
                        request.bookingId(),
                        authentication.getName(),
                        request.paymentMethod()
                )
        );
    }

    @PostMapping("/{paymentId}/retry")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Retry a failed payment attempt before the booking deadline")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Payment attempt returned; booking remains PENDING_PAYMENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or payment ID"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Booking belongs to another customer"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Retry deadline passed or successful payment already exists")
    })
    public ApiResponse<PaymentResponse> retryPaymentAttempt(
            @PathVariable @Positive Long paymentId,
            @Valid @RequestBody(required = false) PaymentRetryRequest request,
            Authentication authentication
    ) {
        String method = request == null ? null : request.paymentMethod();
        return ApiResponse.success(
                "Payment retry created successfully",
                paymentService.retryPaymentAttempt(paymentId, authentication.getName(), method)
        );
    }

    @PostMapping("/callback")
    @Operation(summary = "Process a verified simulated gateway callback", description = "Public transport endpoint protected by the configured simulation token. Repeated success callbacks are idempotent.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Callback processed idempotently; booking may become CONFIRMED or remain PENDING_PAYMENT"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid token, reference, amount or callback status"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Concurrent or duplicate successful payment conflict")
    })
    public ApiResponse<PaymentCallbackResponse> callback(
            @Valid @RequestBody PaymentCallbackRequest request
    ) {
        return ApiResponse.success(
                paymentService.processCallback(request)
        );
    }
}
