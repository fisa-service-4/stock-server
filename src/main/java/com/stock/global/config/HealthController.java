package com.stock.global.config;

import com.stock.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Health", description = "서버 및 DB 연결 상태 확인")
@RestController
@RequestMapping("/internal/v1/health")
@RequiredArgsConstructor
public class HealthController {

  private final JdbcTemplate jdbcTemplate;

  @Operation(summary = "헬스체크", description = "서버 상태 및 Oracle DB 연결 확인")
  @GetMapping
  public ResponseEntity<ApiResponse<Map<String, String>>> health(HttpServletRequest request) {
    String traceId = request.getHeader("X-Trace-Id");
    String dbStatus;
    try {
      jdbcTemplate.queryForObject("SELECT 1 FROM DUAL", Integer.class);
      dbStatus = "UP";
    } catch (Exception e) {
      dbStatus = "DOWN";
    }
    return ResponseEntity.ok(
        ApiResponse.success(Map.of("server", "UP", "database", dbStatus), traceId));
  }
}
