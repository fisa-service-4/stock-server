package com.stock.domain.account.entity;

import com.stock.domain.account.entity.enums.AccountStatus;
import com.stock.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "securities_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecuritiesAccount extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "securities_account_id")
  private Long securitiesAccountId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "broker_code", length = 20, nullable = false)
  private String brokerCode;

  @Column(name = "account_number", length = 50, nullable = false, unique = true)
  private String accountNumber;

  @Column(name = "account_name", length = 100, nullable = false)
  private String accountName;

  @Column(name = "cash_balance", precision = 18, scale = 2, nullable = false)
  private BigDecimal cashBalance;

  @Column(name = "withdrawable_balance", precision = 18, scale = 2, nullable = false)
  private BigDecimal withdrawableBalance;

  @Column(name = "total_evaluated_asset", precision = 18, scale = 2)
  private BigDecimal totalEvaluatedAsset;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_status", length = 20, nullable = false)
  private AccountStatus accountStatus;

  @Column(name = "opened_at", nullable = false)
  private LocalDateTime openedAt;

  @Column(name = "closed_at")
  private LocalDateTime closedAt;

  @Builder
  public SecuritiesAccount(
      Long userId,
      String brokerCode,
      String accountNumber,
      String accountName,
      BigDecimal cashBalance,
      BigDecimal withdrawableBalance,
      BigDecimal totalEvaluatedAsset,
      AccountStatus accountStatus,
      LocalDateTime openedAt) {
    this.userId = userId;
    this.brokerCode = brokerCode;
    this.accountNumber = accountNumber;
    this.accountName = accountName;
    this.cashBalance = cashBalance;
    this.withdrawableBalance = withdrawableBalance;
    this.totalEvaluatedAsset = totalEvaluatedAsset;
    this.accountStatus = accountStatus;
    this.openedAt = openedAt;
  }

  public void deposit(BigDecimal amount) {
    this.cashBalance = this.cashBalance.add(amount);
    this.withdrawableBalance = this.withdrawableBalance.add(amount);
  }

  public void withdraw(BigDecimal amount) {
    this.cashBalance = this.cashBalance.subtract(amount);
    this.withdrawableBalance = this.withdrawableBalance.subtract(amount);
  }
}
