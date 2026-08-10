package com.payment.payflow.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ErrorResponse {
    private String errorCode;
    private String message;
    private LocalDateTime timestamp;
}
