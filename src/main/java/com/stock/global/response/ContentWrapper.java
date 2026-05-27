package com.stock.global.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ContentWrapper<T> {

  private List<T> content;

  public static <T> ContentWrapper<T> of(List<T> list) {
    return ContentWrapper.<T>builder().content(list).build();
  }
}
