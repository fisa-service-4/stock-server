package com.stock.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

  private final boolean success;
  private final T data;
  private final ErrorBody error;
  private final Meta meta;

  @Getter
  @Builder
  public static class ErrorBody {
    private final String code;
    private final String message;
  }

  @Getter
  @Builder
  public static class Meta {
    private final String traceId;
  }

  public static <T> ApiResponse<T> success(T data, String traceId) {
    return ApiResponse.<T>builder()
        .success(true)
        .data(data)
        .meta(Meta.builder().traceId(traceId).build())
        .build();
  }

  public static <T> ApiResponse<T> error(String code, String message, String traceId) {
    return ApiResponse.<T>builder()
        .success(false)
        .error(ErrorBody.builder().code(code).message(message).build())
        .meta(Meta.builder().traceId(traceId).build())
        .build();
  }
}
