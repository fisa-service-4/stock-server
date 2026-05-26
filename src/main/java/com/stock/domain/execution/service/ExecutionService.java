package com.stock.domain.execution.service;

import com.stock.domain.account.validator.AccountValidator;
import com.stock.domain.execution.dto.response.ExecutionResponse;
import com.stock.domain.execution.repository.StockExecutionRepository;
import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.repository.StockMasterRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

  private final StockExecutionRepository stockExecutionRepository;
  private final StockMasterRepository stockMasterRepository;
  private final AccountValidator accountValidator;

  @Transactional(readOnly = true)
  public Page<ExecutionResponse> getExecutions(
      Long userId,
      Long accountId,
      String stockCode,
      LocalDate fromDate,
      LocalDate toDate,
      int page,
      int size) {

    accountValidator.validateOwner(userId, accountId);

    LocalDateTime from = fromDate != null ? fromDate.atStartOfDay() : null;
    LocalDateTime to = toDate != null ? toDate.atTime(23, 59, 59) : null;

    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "executedAt"));

    Map<String, String> nameCache = new HashMap<>();
    Page<ExecutionResponse> result =
        stockExecutionRepository
            .findByAccountIdWithFilters(accountId, stockCode, from, to, pageable)
            .map(
                execution -> {
                  String name =
                      nameCache.computeIfAbsent(
                          execution.getStockCode(),
                          code ->
                              stockMasterRepository
                                  .findById(code)
                                  .map(StockMaster::getStockName)
                                  .orElse(code));
                  return ExecutionResponse.from(execution, name);
                });

    log.info(
        "[{}] 체결 내역 조회 accountId={} total={}",
        MDC.get("traceId"),
        accountId,
        result.getTotalElements());
    return result;
  }
}
