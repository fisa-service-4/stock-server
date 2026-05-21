package com.stock.domain.order.entity;

import com.stock.domain.order.entity.enums.ModificationType;
import com.stock.domain.order.entity.enums.ModifiedBy;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_modification_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderModificationHistory {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "modification_history_id")
  private Long modificationHistoryId;

  @Column(name = "stock_order_id", nullable = false)
  private Long stockOrderId;

  @Enumerated(EnumType.STRING)
  @Column(name = "modification_type", length = 10, nullable = false)
  private ModificationType modificationType;

  @Lob
  @Column(name = "before_payload")
  private String beforePayload;

  @Lob
  @Column(name = "after_payload")
  private String afterPayload;

  @Enumerated(EnumType.STRING)
  @Column(name = "modified_by", length = 10, nullable = false)
  private ModifiedBy modifiedBy;

  @Column(name = "modified_at", nullable = false)
  private LocalDateTime modifiedAt;

  @Builder
  public OrderModificationHistory(
      Long stockOrderId,
      ModificationType modificationType,
      String beforePayload,
      String afterPayload,
      ModifiedBy modifiedBy,
      LocalDateTime modifiedAt) {
    this.stockOrderId = stockOrderId;
    this.modificationType = modificationType;
    this.beforePayload = beforePayload;
    this.afterPayload = afterPayload;
    this.modifiedBy = modifiedBy;
    this.modifiedAt = modifiedAt;
  }
}
