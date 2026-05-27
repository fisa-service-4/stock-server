package com.stock.domain.holding.service;

import com.stock.domain.account.validator.AccountValidator;
import com.stock.domain.holding.dto.response.HoldingResponse;
import com.stock.domain.holding.dto.response.HoldingReturnResponse;
import com.stock.domain.holding.entity.StockHolding;
import com.stock.domain.holding.repository.StockHoldingRepository;
import com.stock.domain.portfolio.repository.StockPortfolioSnapshotRepository;
import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.repository.StockPriceHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class HoldingService {

  private final StockHoldingRepository holdingRepository;
  private final StockPriceHistoryRepository priceHistoryRepository;
  private final StockMasterRepository stockMasterRepository;
  private final StockPortfolioSnapshotRepository snapshotRepository;
  private final AccountValidator accountValidator;

  @Transactional(readOnly = true)
  public List<HoldingResponse> getHoldings(Long userId, Long accountId) {
    accountValidator.validateOwner(userId, accountId);

    List<StockHolding> holdings = holdingRepository.findBySecuritiesAccountId(accountId);
    if (holdings.isEmpty()) {
      log.info("[{}] 보유종목 없음 accountId={}", MDC.get("traceId"), accountId);
      return Collections.emptyList();
    }

    List<String> stockCodes =
        holdings.stream().map(StockHolding::getStockCode).collect(Collectors.toList());
    Map<String, BigDecimal> priceMap = buildPriceMap(stockCodes);
    Map<String, String> nameCache = buildNameCache(stockCodes);

    List<HoldingResponse> result =
        holdings.stream()
            .map(
                holding -> {
                  BigDecimal currentPrice =
                      priceMap.getOrDefault(
                          holding.getStockCode(), holding.getAveragePurchasePrice());
                  String name =
                      nameCache.getOrDefault(holding.getStockCode(), holding.getStockCode());
                  return HoldingResponse.of(holding, name, currentPrice);
                })
            .collect(Collectors.toList());

    log.info("[{}] 보유종목 조회 accountId={} count={}", MDC.get("traceId"), accountId, result.size());
    return result;
  }

  @Transactional(readOnly = true)
  public HoldingReturnResponse getReturns(Long userId, Long accountId) {
    accountValidator.validateOwner(userId, accountId);

    List<StockHolding> holdings = holdingRepository.findBySecuritiesAccountId(accountId);

    BigDecimal totalReturnRate = BigDecimal.ZERO;
    BigDecimal dailyReturnRate = null;

    if (!holdings.isEmpty()) {
      List<String> stockCodes =
          holdings.stream().map(StockHolding::getStockCode).collect(Collectors.toList());
      Map<String, BigDecimal> priceMap = buildPriceMap(stockCodes);

      BigDecimal todayStockAsset =
          holdings.stream()
              .map(
                  h ->
                      priceMap
                          .getOrDefault(h.getStockCode(), h.getAveragePurchasePrice())
                          .multiply(BigDecimal.valueOf(h.getHoldingQuantity())))
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      BigDecimal totalPurchased =
          holdings.stream()
              .map(
                  h ->
                      h.getTotalPurchaseAmount() != null
                          ? h.getTotalPurchaseAmount()
                          : h.getAveragePurchasePrice()
                              .multiply(BigDecimal.valueOf(h.getHoldingQuantity())))
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      if (totalPurchased.compareTo(BigDecimal.ZERO) > 0) {
        totalReturnRate =
            todayStockAsset
                .subtract(totalPurchased)
                .divide(totalPurchased, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
      }

      LocalDate yesterday = LocalDate.now().minusDays(1);
      var yesterdaySnapshot = snapshotRepository.findByUserIdAndSnapshotDate(userId, yesterday);
      if (yesterdaySnapshot.isPresent()) {
        BigDecimal prevStockAsset = yesterdaySnapshot.get().getStockAsset();
        if (prevStockAsset != null && prevStockAsset.compareTo(BigDecimal.ZERO) > 0) {
          dailyReturnRate =
              todayStockAsset
                  .subtract(prevStockAsset)
                  .divide(prevStockAsset, 4, RoundingMode.HALF_UP)
                  .multiply(BigDecimal.valueOf(100))
                  .setScale(2, RoundingMode.HALF_UP);
        }
      }
    }

    log.info(
        "[{}] 수익률 조회 accountId={} totalReturnRate={} dailyReturnRate={}",
        MDC.get("traceId"),
        accountId,
        totalReturnRate,
        dailyReturnRate);
    return HoldingReturnResponse.builder()
        .accountId(accountId)
        .totalReturnRate(totalReturnRate)
        .dailyReturnRate(dailyReturnRate)
        .build();
  }

  private Map<String, BigDecimal> buildPriceMap(List<String> stockCodes) {
    List<StockPriceHistory> latestPrices =
        priceHistoryRepository.findLatestByStockCodes(stockCodes);
    return latestPrices.stream()
        .collect(
            Collectors.toMap(
                StockPriceHistory::getStockCode, StockPriceHistory::getClosePrice, (a, b) -> a));
  }

  private Map<String, String> buildNameCache(List<String> stockCodes) {
    return stockCodes.stream()
        .collect(
            Collectors.toMap(
                code -> code,
                code ->
                    stockMasterRepository.findById(code).map(m -> m.getStockName()).orElse(code)));
  }
}
