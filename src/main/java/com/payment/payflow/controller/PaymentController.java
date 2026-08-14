package com.payment.payflow.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.payment.payflow.dto.PaymentRequest;
import com.payment.payflow.entity.Payment;
import com.payment.payflow.service.PaymentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<Payment> createPayment(@Valid @RequestBody PaymentRequest request) {
        
        Payment processedPayment = paymentService.processPayment(
                request.getIdempotencyKey(),
                request.getAmount(),
                request.getCurrency()
        );
        
        return new ResponseEntity<>(processedPayment, HttpStatus.CREATED);
    }
}
