package com.stock.domain.holding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.stock.domain.account.validator.AccountValidator;
import com.stock.domain.holding.entity.StockHolding;
import com.stock.domain.holding.repository.StockHoldingRepository;
import com.stock.domain.portfolio.entity.StockPortfolioSnapshot;
import com.stock.domain.portfolio.repository.StockPortfolioSnapshotRepository;
import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.repository.StockPriceHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HoldingServiceTest {

  @Mock private StockHoldingRepository holdingRepository;
  @Mock private StockPriceHistoryRepository priceHistoryRepository;
  @Mock private StockMasterRepository stockMasterRepository;
  @Mock private StockPortfolioSnapshotRepository snapshotRepository;
  @Mock private AccountValidator accountValidator;

  @InjectMocks private HoldingService holdingService;

  private static final Long USER_ID = 1L;
  private static final Long ACCOUNT_ID = 2001L;
  private static final String STOCK_CODE = "005930";

  private StockHolding buildHolding(
      String stockCode, int quantity, BigDecimal avgPrice, BigDecimal totalPurchaseAmount) {
    return StockHolding.builder()
        .securitiesAccountId(ACCOUNT_ID)
        .stockCode(stockCode)
        .holdingQuantity(quantity)
        .averagePurchasePrice(avgPrice)
        .totalPurchaseAmount(totalPurchaseAmount)
        .build();
  }

  private StockPriceHistory buildPriceHistory(String stockCode, BigDecimal closePrice) {
    return StockPriceHistory.builder()
        .stockCode(stockCode)
        .tradedDate(LocalDate.now())
        .openPrice(closePrice)
        .highPrice(closePrice)
        .lowPrice(closePrice)
        .closePrice(closePrice)
        .volume(100L)
        .collectedAt(LocalDateTime.now())
        .build();
  }

  private StockPortfolioSnapshot buildSnapshot(BigDecimal stockAsset) {
    return StockPortfolioSnapshot.builder()
        .userId(USER_ID)
        .stockAsset(stockAsset)
        .cashAsset(new BigDecimal("1000000"))
        .totalAsset(stockAsset.add(new BigDecimal("1000000")))
        .snapshotDate(LocalDate.now().minusDays(1))
        .createdAt(LocalDateTime.now().minusDays(1))
        .build();
  }

  @Nested
  @DisplayName("getReturns — 수익률 조회")
  class GetReturns {

    @Test
    @DisplayName("보유 종목이 없으면 totalReturnRate=0, dailyReturnRate=null 반환")
    void emptyHoldings_returnsZeroRates() {
      when(holdingRepository.findBySecuritiesAccountId(ACCOUNT_ID))
          .thenReturn(Collections.emptyList());

      var response = holdingService.getReturns(USER_ID, ACCOUNT_ID);

      assertThat(response.getAccountId()).isEqualTo(ACCOUNT_ID);
      assertThat(response.getTotalReturnRate()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(response.getDailyReturnRate()).isNull();
    }

    @Test
    @DisplayName("현재가가 평균단가와 같으면 totalReturnRate=0")
    void currentPriceEqualToAvg_zeroReturnRate() {
      BigDecimal price = new BigDecimal("70000");
      when(holdingRepository.findBySecuritiesAccountId(ACCOUNT_ID))
          .thenReturn(List.of(buildHolding(STOCK_CODE, 10, price, new BigDecimal("700000"))));
      when(priceHistoryRepository.findLatestByStockCodes(anyList()))
          .thenReturn(List.of(buildPriceHistory(STOCK_CODE, price)));
      when(snapshotRepository.findByUserIdAndSnapshotDate(eq(USER_ID), any(LocalDate.class)))
          .thenReturn(Optional.empty());

      var response = holdingService.getReturns(USER_ID, ACCOUNT_ID);

      assertThat(response.getTotalReturnRate()).isEqualByComparingTo(BigDecimal.ZERO);
      assertThat(response.getDailyReturnRate()).isNull();
    }

    @Test
    @DisplayName("현재가 10% 상승 시 totalReturnRate=10.00")
    void priceUp10Percent_totalReturnRateIs10() {
      // 매입가 70000, 현재가 77000, 10주 → 총매입=700000, 평가=770000
      when(holdingRepository.findBySecuritiesAccountId(ACCOUNT_ID))
          .thenReturn(
              List.of(
                  buildHolding(STOCK_CODE, 10, new BigDecimal("70000"), new BigDecimal("700000"))));
      when(priceHistoryRepository.findLatestByStockCodes(anyList()))
          .thenReturn(List.of(buildPriceHistory(STOCK_CODE, new BigDecimal("77000"))));
      when(snapshotRepository.findByUserIdAndSnapshotDate(eq(USER_ID), any(LocalDate.class)))
          .thenReturn(Optional.empty());

      var response = holdingService.getReturns(USER_ID, ACCOUNT_ID);

      // (770000 - 700000) / 700000 * 100 = 10.00
      assertThat(response.getTotalReturnRate()).isEqualByComparingTo(new BigDecimal("10.00"));
      assertThat(response.getDailyReturnRate()).isNull();
    }

    @Test
    @DisplayName("현재가 10% 하락 시 totalReturnRate=-10.00")
    void priceDown10Percent_totalReturnRateIsNegative() {
      // 매입가 70000, 현재가 63000, 10주 → 총매입=700000, 평가=630000
      when(holdingRepository.findBySecuritiesAccountId(ACCOUNT_ID))
          .thenReturn(
              List.of(
                  buildHolding(STOCK_CODE, 10, new BigDecimal("70000"), new BigDecimal("700000"))));
      when(priceHistoryRepository.findLatestByStockCodes(anyList()))
          .thenReturn(List.of(buildPriceHistory(STOCK_CODE, new BigDecimal("63000"))));
      when(snapshotRepository.findByUserIdAndSnapshotDate(eq(USER_ID), any(LocalDate.class)))
          .thenReturn(Optional.empty());

      var response = holdingService.getReturns(USER_ID, ACCOUNT_ID);

      // (630000 - 700000) / 700000 * 100 = -10.00
      assertThat(response.getTotalReturnRate()).isEqualByComparingTo(new BigDecimal("-10.00"));
    }

    @Test
    @DisplayName("totalPurchaseAmount가 null이면 avgPrice×quantity로 대체 계산한다")
    void nullTotalPurchaseAmount_fallbackToAvgTimesQuantity() {
      // totalPurchaseAmount=null → 70000 * 10 = 700000 으로 계산
      when(holdingRepository.findBySecuritiesAccountId(ACCOUNT_ID))
          .thenReturn(List.of(buildHolding(STOCK_CODE, 10, new BigDecimal("70000"), null)));
      when(priceHistoryRepository.findLatestByStockCodes(anyList()))
          .thenReturn(List.of(buildPriceHistory(STOCK_CODE, new BigDecimal("77000"))));
      when(snapshotRepository.findByUserIdAndSnapshotDate(eq(USER_ID), any(LocalDate.class)))
          .thenReturn(Optional.empty());

      var response = holdingService.getReturns(USER_ID, ACCOUNT_ID);

      assertThat(response.getTotalReturnRate()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("시세 데이터 없으면 평균단가를 현재가로 간주해 totalReturnRate=0")
    void noPriceHistory_fallbackToAvgPrice_zeroReturn() {
      when(holdingRepository.findBySecuritiesAccountId(ACCOUNT_ID))
          .thenReturn(
              List.of(
                  buildHolding(STOCK_CODE, 10, new BigDecimal("70000"), new BigDecimal("700000"))));
      when(priceHistoryRepository.findLatestByStockCodes(anyList()))
          .thenReturn(Collections.emptyList()); // 시세 없음
      when(snapshotRepository.findByUserIdAndSnapshotDate(eq(USER_ID), any(LocalDate.class)))
          .thenReturn(Optional.empty());

      var response = holdingService.getReturns(USER_ID, ACCOUNT_ID);

      // priceMap에 없으므로 averagePurchasePrice(70000) 사용 → 손익 0
      assertThat(response.getTotalReturnRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("어제 스냅샷이 있으면 dailyReturnRate를 계산한다")
    void yesterdaySnapshot_dailyReturnRateCalculated() {
      // 어제 stock_asset=700000, 오늘 평가=770000 → 일간 +10%
      when(holdingRepository.findBySecuritiesAccountId(ACCOUNT_ID))
          .thenReturn(
              List.of(
                  buildHolding(STOCK_CODE, 10, new BigDecimal("70000"), new BigDecimal("700000"))));
      when(priceHistoryRepository.findLatestByStockCodes(anyList()))
          .thenReturn(List.of(buildPriceHistory(STOCK_CODE, new BigDecimal("77000"))));
      when(snapshotRepository.findByUserIdAndSnapshotDate(eq(USER_ID), any(LocalDate.class)))
          .thenReturn(Optional.of(buildSnapshot(new BigDecimal("700000"))));

      var response = holdingService.getReturns(USER_ID, ACCOUNT_ID);

      // (770000 - 700000) / 700000 * 100 = 10.00
      assertThat(response.getDailyReturnRate()).isEqualByComparingTo(new BigDecimal("10.00"));
    }

    @Test
    @DisplayName("어제 스냅샷의 stockAsset이 0이면 dailyReturnRate는 null이다")
    void yesterdaySnapshotStockAssetZero_dailyReturnRateNull() {
      when(holdingRepository.findBySecuritiesAccountId(ACCOUNT_ID))
          .thenReturn(
              List.of(
                  buildHolding(STOCK_CODE, 10, new BigDecimal("70000"), new BigDecimal("700000"))));
      when(priceHistoryRepository.findLatestByStockCodes(anyList()))
          .thenReturn(List.of(buildPriceHistory(STOCK_CODE, new BigDecimal("77000"))));
      when(snapshotRepository.findByUserIdAndSnapshotDate(eq(USER_ID), any(LocalDate.class)))
          .thenReturn(Optional.of(buildSnapshot(BigDecimal.ZERO)));

      var response = holdingService.getReturns(USER_ID, ACCOUNT_ID);

      assertThat(response.getDailyReturnRate()).isNull();
    }

    @Test
    @DisplayName("어제 스냅샷이 없으면 dailyReturnRate는 null이다")
    void noYesterdaySnapshot_dailyReturnRateNull() {
      when(holdingRepository.findBySecuritiesAccountId(ACCOUNT_ID))
          .thenReturn(
              List.of(
                  buildHolding(STOCK_CODE, 10, new BigDecimal("70000"), new BigDecimal("700000"))));
      when(priceHistoryRepository.findLatestByStockCodes(anyList()))
          .thenReturn(List.of(buildPriceHistory(STOCK_CODE, new BigDecimal("77000"))));
      when(snapshotRepository.findByUserIdAndSnapshotDate(eq(USER_ID), any(LocalDate.class)))
          .thenReturn(Optional.empty());

      var response = holdingService.getReturns(USER_ID, ACCOUNT_ID);

      assertThat(response.getDailyReturnRate()).isNull();
    }
  }
}
