package com.stock.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  // COMMON
  VALID_001(HttpStatus.BAD_REQUEST, "VALID_001", "입력값 오류"),
  VALID_002(HttpStatus.BAD_REQUEST, "VALID_002", "필수값 누락"),
  AUTH_010(HttpStatus.FORBIDDEN, "AUTH_010", "접근 권한 없음"),

  // ACCOUNT
  ACCOUNT_001(HttpStatus.NOT_FOUND, "ACCOUNT_001", "계좌 없음"),
  ACCOUNT_002(HttpStatus.FORBIDDEN, "ACCOUNT_002", "계좌 접근 권한 없음"),
  ACCOUNT_003(HttpStatus.BAD_REQUEST, "ACCOUNT_003", "사용할 수 없는 계좌입니다"),

  // STOCK
  STOCK_001(HttpStatus.NOT_FOUND, "STOCK_001", "종목 없음"),
  STOCK_002(HttpStatus.INTERNAL_SERVER_ERROR, "STOCK_002", "현재가 조회 실패"),
  STOCK_003(HttpStatus.INTERNAL_SERVER_ERROR, "STOCK_003", "차트 데이터 조회 실패"),

  // ORDER
  ORDER_001(HttpStatus.BAD_REQUEST, "ORDER_001", "주문 가능 금액이 부족합니다."),
  ORDER_002(HttpStatus.BAD_REQUEST, "ORDER_002", "보유 수량이 부족합니다."),
  ORDER_003(HttpStatus.NOT_FOUND, "ORDER_003", "주문 정보 없음"),
  ORDER_004(HttpStatus.BAD_REQUEST, "ORDER_004", "주문 상태 오류"),
  ORDER_005(HttpStatus.INTERNAL_SERVER_ERROR, "ORDER_005", "주문 실행 실패"),

  // EXECUTION
  EXECUTION_001(HttpStatus.NOT_FOUND, "EXECUTION_001", "체결 내역 없음"),

  // HOLDING
  HOLDING_001(HttpStatus.NOT_FOUND, "HOLDING_001", "보유 종목 없음"),

  // TRANSFER
  TRANSFER_002(HttpStatus.BAD_REQUEST, "TRANSFER_002", "잔액이 부족합니다");

  private final HttpStatus httpStatus;
  private final String code;
  private final String message;
}
