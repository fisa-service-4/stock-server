package com.stock.domain.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stock.domain.account.entity.SecuritiesAccount;
import com.stock.domain.account.entity.enums.AccountStatus;
import com.stock.domain.account.repository.SecuritiesAccountRepository;
import com.stock.domain.account.validator.AccountValidator;
import com.stock.domain.execution.entity.StockExecution;
import com.stock.domain.execution.repository.StockExecutionRepository;
import com.stock.domain.holding.entity.StockHolding;
import com.stock.domain.holding.repository.StockHoldingRepository;
import com.stock.domain.order.dto.request.OrderCreateRequest;
import com.stock.domain.order.dto.response.OrderCancelResponse;
import com.stock.domain.order.dto.response.OrderCreateResponse;
import com.stock.domain.order.entity.StockOrder;
import com.stock.domain.order.entity.enums.OrderMethod;
import com.stock.domain.order.entity.enums.OrderStatus;
import com.stock.domain.order.entity.enums.OrderType;
import com.stock.domain.order.entity.enums.OrderedBy;
import com.stock.domain.order.repository.OrderModificationHistoryRepository;
import com.stock.domain.order.repository.StockOrderRepository;
import com.stock.domain.stock.dto.response.StockPriceResponse;
import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.entity.enums.MarketType;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.service.StockPriceHistoryService;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderServiceTest {

  @Mock private StockOrderRepository stockOrderRepository;
  @Mock private OrderModificationHistoryRepository orderModificationHistoryRepository;
  @Mock private StockExecutionRepository stockExecutionRepository;
  @Mock private StockHoldingRepository stockHoldingRepository;
  @Mock private SecuritiesAccountRepository securitiesAccountRepository;
  @Mock private StockMasterRepository stockMasterRepository;
  @Mock private AccountValidator accountValidator;
  @Mock private StockPriceHistoryService stockPriceHistoryService;

  @InjectMocks private OrderService orderService;

  private static final Long USER_ID = 1L;
  private static final Long ACCOUNT_ID = 1L;
  private static final String STOCK_CODE = "005930";
  private static final BigDecimal CURRENT_PRICE = new BigDecimal("70000");

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(orderService, "objectMapper", new ObjectMapper());
  }

  /** securitiesAccountId는 JPA auto-increment 필드라 Reflection으로 주입 */
  private SecuritiesAccount account(BigDecimal cashBalance) {
    SecuritiesAccount acc =
        SecuritiesAccount.builder()
            .userId(USER_ID)
            .brokerCode("243")
            .accountNumber("300-123-456789")
            .accountName("테스트 계좌")
            .cashBalance(cashBalance)
            .withdrawableBalance(cashBalance)
            .accountStatus(AccountStatus.ACTIVE)
            .openedAt(LocalDateTime.now())
            .build();
    ReflectionTestUtils.setField(acc, "securitiesAccountId", ACCOUNT_ID);
    return acc;
  }

  private StockMaster stockMaster() {
    return StockMaster.builder()
        .stockCode(STOCK_CODE)
        .stockName("삼성전자")
        .marketType(MarketType.KOSPI)
        .build();
  }

  private StockPriceResponse priceResponse(BigDecimal price) {
    return StockPriceResponse.builder()
        .stockCode(STOCK_CODE)
        .stockName("삼성전자")
        .currentPrice(price)
        .build();
  }

  private OrderCreateRequest buyRequest(OrderMethod method, int quantity, BigDecimal limitPrice) {
    OrderCreateRequest req = new OrderCreateRequest();
    ReflectionTestUtils.setField(req, "stockCode", STOCK_CODE);
    ReflectionTestUtils.setField(req, "orderType", OrderType.BUY);
    ReflectionTestUtils.setField(req, "orderMethod", method);
    ReflectionTestUtils.setField(req, "quantity", quantity);
    ReflectionTestUtils.setField(req, "price", limitPrice);
    return req;
  }

  private OrderCreateRequest sellRequest(OrderMethod method, int quantity, BigDecimal limitPrice) {
    OrderCreateRequest req = new OrderCreateRequest();
    ReflectionTestUtils.setField(req, "stockCode", STOCK_CODE);
    ReflectionTestUtils.setField(req, "orderType", OrderType.SELL);
    ReflectionTestUtils.setField(req, "orderMethod", method);
    ReflectionTestUtils.setField(req, "quantity", quantity);
    ReflectionTestUtils.setField(req, "price", limitPrice);
    return req;
  }

  private StockHolding holding(int quantity, BigDecimal avgPrice) {
    return StockHolding.builder()
        .securitiesAccountId(ACCOUNT_ID)
        .stockCode(STOCK_CODE)
        .holdingQuantity(quantity)
        .averagePurchasePrice(avgPrice)
        .totalPurchaseAmount(avgPrice.multiply(BigDecimal.valueOf(quantity)))
        .build();
  }

  private void mockBaseSetup(SecuritiesAccount acc, BigDecimal currentPrice) {
    when(accountValidator.validateOwner(USER_ID, ACCOUNT_ID)).thenReturn(acc);
    when(stockMasterRepository.findById(STOCK_CODE)).thenReturn(Optional.of(stockMaster()));
    when(stockPriceHistoryService.getCurrentPrice(STOCK_CODE))
        .thenReturn(priceResponse(currentPrice));
    when(stockOrderRepository.save(any(StockOrder.class))).thenAnswer(inv -> inv.getArgument(0));
    when(stockExecutionRepository.save(any(StockExecution.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(stockHoldingRepository.save(any(StockHolding.class)))
        .thenAnswer(inv -> inv.getArgument(0));
    when(securitiesAccountRepository.save(any(SecuritiesAccount.class)))
        .thenAnswer(inv -> inv.getArgument(0));
  }

  // ──────────────────────────────────────────────
  // isExecutable
  // ──────────────────────────────────────────────

  @Nested
  @DisplayName("isExecutable — 체결 조건 판단")
  class IsExecutable {

    @Test
    @DisplayName("MARKET 주문은 항상 즉시 체결된다")
    void market_alwaysExecutes() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));
      mockBaseSetup(acc, CURRENT_PRICE);
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.empty());

      OrderCreateResponse response =
          orderService.createOrder(USER_ID, ACCOUNT_ID, buyRequest(OrderMethod.MARKET, 10, null));

      assertThat(response.getStatus()).isEqualTo(OrderStatus.FILLED);
      verify(stockExecutionRepository).save(any(StockExecution.class));
    }

    @Test
    @DisplayName("LIMIT BUY — 현재가(63,000) ≤ 지정가(65,000)이면 즉시 체결된다")
    void limitBuy_executes_whenCurrentPriceLe() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));
      mockBaseSetup(acc, new BigDecimal("63000"));
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.empty());

      OrderCreateResponse response =
          orderService.createOrder(
              USER_ID, ACCOUNT_ID, buyRequest(OrderMethod.LIMIT, 10, new BigDecimal("65000")));

      assertThat(response.getStatus()).isEqualTo(OrderStatus.FILLED);
      verify(stockExecutionRepository).save(any(StockExecution.class));
    }

    @Test
    @DisplayName("LIMIT BUY — 현재가(70,000) > 지정가(65,000)이면 REQUESTED 유지")
    void limitBuy_staysRequested_whenCurrentPriceExceeds() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));
      mockBaseSetup(acc, new BigDecimal("70000"));

      OrderCreateResponse response =
          orderService.createOrder(
              USER_ID, ACCOUNT_ID, buyRequest(OrderMethod.LIMIT, 10, new BigDecimal("65000")));

      assertThat(response.getStatus()).isEqualTo(OrderStatus.REQUESTED);
      assertThat(response.getFilledQuantity()).isEqualTo(0);
      verify(stockExecutionRepository, never()).save(any());
    }

    @Test
    @DisplayName("LIMIT SELL — 현재가(72,000) ≥ 지정가(70,000)이면 즉시 체결된다")
    void limitSell_executes_whenCurrentPriceGe() {
      SecuritiesAccount acc = account(new BigDecimal("0"));
      mockBaseSetup(acc, new BigDecimal("72000"));
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.of(holding(20, new BigDecimal("60000"))));

      OrderCreateResponse response =
          orderService.createOrder(
              USER_ID, ACCOUNT_ID, sellRequest(OrderMethod.LIMIT, 10, new BigDecimal("70000")));

      assertThat(response.getStatus()).isEqualTo(OrderStatus.FILLED);
      verify(stockExecutionRepository).save(any(StockExecution.class));
    }

    @Test
    @DisplayName("LIMIT SELL — 현재가(68,000) < 지정가(70,000)이면 REQUESTED 유지")
    void limitSell_staysRequested_whenCurrentPriceBelow() {
      SecuritiesAccount acc = account(new BigDecimal("0"));
      mockBaseSetup(acc, new BigDecimal("68000"));
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.of(holding(20, new BigDecimal("60000"))));

      OrderCreateResponse response =
          orderService.createOrder(
              USER_ID, ACCOUNT_ID, sellRequest(OrderMethod.LIMIT, 10, new BigDecimal("70000")));

      assertThat(response.getStatus()).isEqualTo(OrderStatus.REQUESTED);
      verify(stockExecutionRepository, never()).save(any());
    }
  }

  // ──────────────────────────────────────────────
  // validateOrderCondition
  // ──────────────────────────────────────────────

  @Nested
  @DisplayName("validateOrderCondition — 주문 조건 검증")
  class ValidateOrderCondition {

    @Test
    @DisplayName("BUY — 잔액(1,000,000) > 주문금액(700,000)이면 통과한다")
    void buy_passes_whenSufficientBalance() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));
      mockBaseSetup(acc, CURRENT_PRICE);
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.empty());

      assertThat(
              orderService
                  .createOrder(USER_ID, ACCOUNT_ID, buyRequest(OrderMethod.MARKET, 10, null))
                  .getStatus())
          .isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @DisplayName("BUY — 잔액(700,000) == 주문금액(700,000)이면 통과한다 (경계값)")
    void buy_passes_atExactBalance() {
      SecuritiesAccount acc = account(new BigDecimal("700000")); // 70,000 * 10
      mockBaseSetup(acc, CURRENT_PRICE);
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.empty());

      assertThat(
              orderService
                  .createOrder(USER_ID, ACCOUNT_ID, buyRequest(OrderMethod.MARKET, 10, null))
                  .getStatus())
          .isEqualTo(OrderStatus.FILLED);
    }

    @Test
    @DisplayName("BUY — 잔액(100,000) < 주문금액(700,000)이면 ORDER_001 발생")
    void buy_throws_ORDER_001() {
      SecuritiesAccount acc = account(new BigDecimal("100000"));
      when(accountValidator.validateOwner(USER_ID, ACCOUNT_ID)).thenReturn(acc);
      when(stockMasterRepository.findById(STOCK_CODE)).thenReturn(Optional.of(stockMaster()));
      when(stockPriceHistoryService.getCurrentPrice(STOCK_CODE))
          .thenReturn(priceResponse(CURRENT_PRICE));

      assertThatThrownBy(
              () ->
                  orderService.createOrder(
                      USER_ID, ACCOUNT_ID, buyRequest(OrderMethod.MARKET, 10, null)))
          .isInstanceOf(GlobalException.class)
          .satisfies(
              e -> assertThat(((GlobalException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_001));
    }

    @Test
    @DisplayName("SELL — 보유종목이 없으면 ORDER_002 발생")
    void sell_throws_ORDER_002_whenNoHolding() {
      SecuritiesAccount acc = account(new BigDecimal("0"));
      when(accountValidator.validateOwner(USER_ID, ACCOUNT_ID)).thenReturn(acc);
      when(stockMasterRepository.findById(STOCK_CODE)).thenReturn(Optional.of(stockMaster()));
      when(stockPriceHistoryService.getCurrentPrice(STOCK_CODE))
          .thenReturn(priceResponse(CURRENT_PRICE));
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.empty());

      assertThatThrownBy(
              () ->
                  orderService.createOrder(
                      USER_ID, ACCOUNT_ID, sellRequest(OrderMethod.MARKET, 10, null)))
          .isInstanceOf(GlobalException.class)
          .satisfies(
              e -> assertThat(((GlobalException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_002));
    }

    @Test
    @DisplayName("SELL — 보유(5주) < 주문(10주)이면 ORDER_002 발생")
    void sell_throws_ORDER_002_whenInsufficientHolding() {
      SecuritiesAccount acc = account(new BigDecimal("0"));
      when(accountValidator.validateOwner(USER_ID, ACCOUNT_ID)).thenReturn(acc);
      when(stockMasterRepository.findById(STOCK_CODE)).thenReturn(Optional.of(stockMaster()));
      when(stockPriceHistoryService.getCurrentPrice(STOCK_CODE))
          .thenReturn(priceResponse(CURRENT_PRICE));
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.of(holding(5, new BigDecimal("60000"))));

      assertThatThrownBy(
              () ->
                  orderService.createOrder(
                      USER_ID, ACCOUNT_ID, sellRequest(OrderMethod.MARKET, 10, null)))
          .isInstanceOf(GlobalException.class)
          .satisfies(
              e -> assertThat(((GlobalException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_002));
    }
  }

  // ──────────────────────────────────────────────
  // createOrder — 체결 후 상태 검증
  // ──────────────────────────────────────────────

  @Nested
  @DisplayName("createOrder — 체결 후 상태 검증")
  class CreateOrderExecution {

    @Test
    @DisplayName("MARKET BUY 즉시 체결: FILLED, filledQty=10, remainingQty=0, cashBalance 차감")
    void marketBuy_immediateExecution() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));
      mockBaseSetup(acc, CURRENT_PRICE);
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.empty());

      OrderCreateResponse response =
          orderService.createOrder(USER_ID, ACCOUNT_ID, buyRequest(OrderMethod.MARKET, 10, null));

      assertThat(response.getStatus()).isEqualTo(OrderStatus.FILLED);
      assertThat(response.getFilledQuantity()).isEqualTo(10);
      assertThat(response.getRemainingQuantity()).isEqualTo(0);
      assertThat(acc.getCashBalance())
          .isEqualByComparingTo(new BigDecimal("300000")); // 1,000,000 - 700,000
      verify(stockExecutionRepository).save(any(StockExecution.class));
      verify(stockHoldingRepository).save(any(StockHolding.class));
    }

    @Test
    @DisplayName("MARKET BUY 기존 10주@10,000 + 신규 10주@20,000 → 20주, 평단가 15,000")
    void marketBuy_recalculatesAvgPrice() {
      SecuritiesAccount acc = account(new BigDecimal("2000000"));
      StockHolding existingHolding = holding(10, new BigDecimal("10000"));
      mockBaseSetup(acc, new BigDecimal("20000"));
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.of(existingHolding));

      orderService.createOrder(USER_ID, ACCOUNT_ID, buyRequest(OrderMethod.MARKET, 10, null));

      assertThat(existingHolding.getHoldingQuantity()).isEqualTo(20);
      assertThat(existingHolding.getAveragePurchasePrice())
          .isEqualByComparingTo(new BigDecimal("15000"));
    }

    @Test
    @DisplayName("MARKET SELL 즉시 체결: holding 20→10주, cashBalance 0→700,000")
    void marketSell_immediateExecution() {
      SecuritiesAccount acc = account(new BigDecimal("0"));
      StockHolding existingHolding = holding(20, new BigDecimal("60000"));
      mockBaseSetup(acc, CURRENT_PRICE);
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.of(existingHolding));

      OrderCreateResponse response =
          orderService.createOrder(USER_ID, ACCOUNT_ID, sellRequest(OrderMethod.MARKET, 10, null));

      assertThat(response.getStatus()).isEqualTo(OrderStatus.FILLED);
      assertThat(existingHolding.getHoldingQuantity()).isEqualTo(10);
      assertThat(acc.getCashBalance()).isEqualByComparingTo(new BigDecimal("700000"));
      verify(stockExecutionRepository).save(any(StockExecution.class));
    }

    @Test
    @DisplayName("MARKET SELL 전량(10주) 체결: holding이 delete된다")
    void marketSell_fullQuantity_deletesHolding() {
      SecuritiesAccount acc = account(new BigDecimal("0"));
      StockHolding existingHolding = holding(10, new BigDecimal("60000"));
      mockBaseSetup(acc, CURRENT_PRICE);
      when(stockHoldingRepository.findBySecuritiesAccountIdAndStockCode(
              eq(ACCOUNT_ID), eq(STOCK_CODE)))
          .thenReturn(Optional.of(existingHolding));

      orderService.createOrder(USER_ID, ACCOUNT_ID, sellRequest(OrderMethod.MARKET, 10, null));

      verify(stockHoldingRepository).delete(existingHolding);
      verify(stockHoldingRepository, never()).save(existingHolding);
    }

    @Test
    @DisplayName("LIMIT BUY 조건 미충족: execution·holding·cashBalance 변동 없음")
    void limitBuy_notExecutable_noSideEffects() {
      SecuritiesAccount acc = account(new BigDecimal("1000000"));
      mockBaseSetup(acc, new BigDecimal("70000"));

      orderService.createOrder(
          USER_ID, ACCOUNT_ID, buyRequest(OrderMethod.LIMIT, 10, new BigDecimal("65000")));

      verify(stockExecutionRepository, never()).save(any());
      verify(stockHoldingRepository, never()).save(any());
      assertThat(acc.getCashBalance()).isEqualByComparingTo(new BigDecimal("1000000"));
    }
  }

  // ──────────────────────────────────────────────
  // cancelOrder
  // ──────────────────────────────────────────────

  @Nested
  @DisplayName("cancelOrder")
  class CancelOrder {

    private StockOrder buildRequestedOrder() {
      StockOrder order =
          StockOrder.builder()
              .securitiesAccountId(ACCOUNT_ID)
              .stockCode(STOCK_CODE)
              .orderType(OrderType.BUY)
              .orderMethod(OrderMethod.LIMIT)
              .orderPrice(new BigDecimal("65000"))
              .orderQuantity(10)
              .orderedBy(OrderedBy.USER)
              .orderedAt(LocalDateTime.now())
              .build();
      ReflectionTestUtils.setField(order, "stockOrderId", 1L);
      return order;
    }

    @Test
    @DisplayName("REQUESTED 주문 취소: CANCELLED 전환, ModificationHistory 저장")
    void cancel_requested_succeeds() {
      StockOrder order = buildRequestedOrder();
      when(stockOrderRepository.findById(1L)).thenReturn(Optional.of(order));
      when(accountValidator.validateOwner(USER_ID, ACCOUNT_ID))
          .thenReturn(account(new BigDecimal("1000000")));

      OrderCancelResponse response = orderService.cancelOrder(USER_ID, 1L);

      assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
      assertThat(response.getRemainingQuantity()).isEqualTo(0);
      assertThat(response.getCancelledQuantity()).isEqualTo(10);
      verify(orderModificationHistoryRepository).save(any());
    }

    @Test
    @DisplayName("FILLED 주문 취소 시도: ORDER_004 발생")
    void cancel_filled_throws_ORDER_004() {
      StockOrder order = buildRequestedOrder();
      order.fill(10, new BigDecimal("65000"));
      when(stockOrderRepository.findById(1L)).thenReturn(Optional.of(order));

      assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, 1L))
          .isInstanceOf(GlobalException.class)
          .satisfies(
              e -> assertThat(((GlobalException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_004));
    }

    @Test
    @DisplayName("없는 주문 취소 시도: ORDER_003 발생")
    void cancel_notFound_throws_ORDER_003() {
      when(stockOrderRepository.findById(999L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> orderService.cancelOrder(USER_ID, 999L))
          .isInstanceOf(GlobalException.class)
          .satisfies(
              e -> assertThat(((GlobalException) e).getErrorCode()).isEqualTo(ErrorCode.ORDER_003));
    }
  }
}
