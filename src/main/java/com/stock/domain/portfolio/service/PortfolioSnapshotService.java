package com.stock.domain.portfolio.service;

import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.domain.holding.entity.StockHolding;
import com.stock.domain.holding.repository.StockHoldingRepository;
import com.stock.domain.portfolio.entity.StockPortfolioSnapshot;
import com.stock.domain.portfolio.repository.StockPortfolioSnapshotRepository;
import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.repository.StockPriceHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PortfolioSnapshotService {

  private final SecuritiesAccountRepository accountRepository;
  private final StockHoldingRepository holdingRepository;
  private final StockPriceHistoryRepository priceHistoryRepository;
  private final StockPortfolioSnapshotRepository snapshotRepository;

  @Transactional
  public void takeSnapshot(Long userId, Long accountId) {
    LocalDate today = LocalDate.now();

    if (snapshotRepository.existsByUserIdAndSnapshotDate(userId, today)) {
      log.info("스냅샷 이미 존재 — skip userId={} date={}", userId, today);
      return;
    }

    var account =
        accountRepository
            .findById(accountId)
            .orElseThrow(() -> new IllegalArgumentException("계좌 없음 accountId=" + accountId));

    List<StockHolding> holdings = holdingRepository.findBySecuritiesAccountId(accountId);

    BigDecimal stockAsset = BigDecimal.ZERO;
    BigDecimal totalPurchased = BigDecimal.ZERO;

    if (!holdings.isEmpty()) {
      List<String> stockCodes =
          holdings.stream().map(StockHolding::getStockCode).collect(Collectors.toList());

      List<StockPriceHistory> latestPrices =
          priceHistoryRepository.findLatestByStockCodes(stockCodes);
      Map<String, BigDecimal> priceMap =
          latestPrices.stream()
              .collect(
                  Collectors.toMap(
                      StockPriceHistory::getStockCode,
                      StockPriceHistory::getClosePrice,
                      (a, b) -> a));

      stockAsset =
          holdings.stream()
              .map(
                  h ->
                      priceMap
                          .getOrDefault(h.getStockCode(), h.getAveragePurchasePrice())
                          .multiply(BigDecimal.valueOf(h.getHoldingQuantity())))
              .reduce(BigDecimal.ZERO, BigDecimal::add);

      totalPurchased =
          holdings.stream()
              .map(
                  h ->
                      h.getTotalPurchaseAmount() != null
                          ? h.getTotalPurchaseAmount()
                          : h.getAveragePurchasePrice()
                              .multiply(BigDecimal.valueOf(h.getHoldingQuantity())))
              .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    BigDecimal cashAsset = account.getCashBalance();
    BigDecimal totalAsset = stockAsset.add(cashAsset);
    BigDecimal stockProfit = stockAsset.subtract(totalPurchased);
    BigDecimal totalProfitRate =
        totalPurchased.compareTo(BigDecimal.ZERO) > 0
            ? stockProfit
                .divide(totalPurchased, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

    StockPortfolioSnapshot snapshot =
        StockPortfolioSnapshot.builder()
            .userId(userId)
            .totalAsset(totalAsset.setScale(2, RoundingMode.HALF_UP))
            .stockAsset(stockAsset.setScale(2, RoundingMode.HALF_UP))
            .cashAsset(cashAsset.setScale(2, RoundingMode.HALF_UP))
            .totalProfit(stockProfit.setScale(2, RoundingMode.HALF_UP))
            .totalProfitRate(totalProfitRate)
            .snapshotDate(today)
            .createdAt(LocalDateTime.now())
            .build();

    snapshotRepository.save(snapshot);
    log.info(
        "스냅샷 저장 완료 userId={} accountId={} totalAsset={} date={}",
        userId,
        accountId,
        totalAsset,
        today);
  }
}
