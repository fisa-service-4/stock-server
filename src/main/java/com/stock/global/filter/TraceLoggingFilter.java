package com.stock.global.filter;

import com.stock.global.constants.HeaderConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
public class TraceLoggingFilter extends OncePerRequestFilter {

  private static final String MDC_TRACE_ID = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String traceId = request.getHeader(HeaderConstants.TRACE_ID);
    if (traceId == null || traceId.isBlank()) {
      traceId = UUID.randomUUID().toString();
    }

    MDC.put(MDC_TRACE_ID, traceId);
    log.info("[{}] {} {}", traceId, request.getMethod(), request.getRequestURI());

    try {
      filterChain.doFilter(request, response);
    } finally {
      log.info(
          "[{}] {} {} → {}",
          traceId,
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus());
      MDC.clear();
    }
  }
}
