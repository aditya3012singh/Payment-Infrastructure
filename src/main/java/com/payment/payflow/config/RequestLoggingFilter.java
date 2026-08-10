package com.payment.payflow.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String TRACE_ID_KEY = "trace_id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        try {
            // 1. Generate a unique trace ID for this incoming request
            String traceId = UUID.randomUUID().toString();
            
            // 2. Put it in the MDC (Mapped Diagnostic Context)
            MDC.put(TRACE_ID_KEY, traceId);

            HttpServletRequest httpRequest = (HttpServletRequest) request;
            log.info("Incoming Request: {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());

            // 3. Continue the filter chain (pass to the controller)
            chain.doFilter(request, response);
            
            log.info("Completed Request: {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());

        } finally {
            // 4. ALWAYS clear the MDC to prevent memory leaks in the thread pool
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
