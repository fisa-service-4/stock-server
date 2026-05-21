package com.stock.domain.stock.service;

import com.stock.domain.stock.dto.response.StockSearchResponse;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.global.exception.ErrorCode;
import com.stock.global.exception.GlobalException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockService {

  private final StockMasterRepository stockMasterRepository;

  @Transactional(readOnly = true)
  public List<StockSearchResponse> search(String keyword) {
    List<StockSearchResponse> result =
        stockMasterRepository
            .findByStockNameContainingIgnoreCaseOrStockCodeContainingIgnoreCase(keyword, keyword)
            .stream()
            .map(StockSearchResponse::from)
            .toList();

    if (result.isEmpty()) {
      throw new GlobalException(ErrorCode.STOCK_001);
    }

    return result;
  }
}
