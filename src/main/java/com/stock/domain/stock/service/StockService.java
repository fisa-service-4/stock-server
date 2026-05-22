package com.stock.domain.stock.service;

import com.stock.domain.stock.dto.response.StockSearchResponse;
import com.stock.domain.stock.entity.StockMaster;
import com.stock.domain.stock.entity.StockPriceHistory;
import com.stock.domain.stock.repository.StockMasterRepository;
import com.stock.domain.stock.repository.StockPriceHistoryRepository;
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
  private final StockPriceHistoryRepository stockPriceHistoryRepository;

  @Transactional(readOnly = true)
  public List<StockSearchResponse> search(String keyword) {
    List<StockMaster> masters =
        stockMasterRepository.findByStockNameContainingIgnoreCaseOrStockCodeContainingIgnoreCase(
            keyword, keyword);

    if (masters.isEmpty()) {
      throw new GlobalException(ErrorCode.STOCK_001);
    }

    return masters.stream()
        .map(
            master -> {
              StockPriceHistory history =
                  stockPriceHistoryRepository
                      .findTopByStockCodeOrderByCollectedAtDesc(master.getStockCode())
                      .orElseThrow(() -> new GlobalException(ErrorCode.STOCK_002));
              return StockSearchResponse.of(master, history);
            })
        .toList();
  }
}
