package com.stock.global.config;

import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.entity.enums.AccountStatus;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.service.StockPriceHistoryService;
import com.stock.external.kis.dummy.provider.MockStockPriceProvider;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer {

  private final SecuritiesAccountRepository securitiesAccountRepository;
  private final StockMasterRepository stockMasterRepository;
  private final StockPriceHistoryService stockPriceHistoryService;

  @Autowired(required = false)
  private MockStockPriceProvider mockStockPriceProvider;

  @PostConstruct
  public void init() {
    initAccount();
    initStockPrices();
    initDailyCandles();
  }

  private void initAccount() {
    if (securitiesAccountRepository.findByUserId(1L).isEmpty()) {
      securitiesAccountRepository.save(
          SecuritiesAccount.builder()
              .userId(1L)
              .brokerCode("KIS")
              .accountNumber("1234567890")
              .accountName("테스트 계좌")
              .cashBalance(new BigDecimal("10000000"))
              .withdrawableBalance(new BigDecimal("10000000"))
              .accountStatus(AccountStatus.ACTIVE)
              .openedAt(LocalDateTime.now())
              .build());
      log.info("[DataInitializer] 테스트 계좌 생성 완료 userId=1");
    }
  }

  private void initStockPrices() {
    if (mockStockPriceProvider == null) {
      log.info("[DataInitializer] mock 비활성화 — 초기 시세 삽입 skip");
      return;
    }
    stockMasterRepository
        .findAll()
        .forEach(
            stock ->
                stockPriceHistoryService.initializeIfAbsent(
                    stock.getStockCode(),
                    mockStockPriceProvider.getFallbackPrice(stock.getStockCode())));
    log.info("[DataInitializer] 초기 시세 삽입 완료");
  }

  private void initDailyCandles() {
    if (mockStockPriceProvider == null) {
      log.info("[DataInitializer] mock 비활성화 — daily candle 삽입 skip");
      return;
    }
    stockMasterRepository
        .findAll()
        .forEach(
            stock ->
                stockPriceHistoryService.initDailyCandles(
                    stock.getStockCode(),
                    mockStockPriceProvider.getFallbackPrice(stock.getStockCode())));
    log.info("[DataInitializer] daily candle 삽입 완료");
  }
}
