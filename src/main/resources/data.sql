MERGE INTO stock_master t
USING (SELECT '005930' AS stock_code, '삼성전자' AS stock_name, 'KOSPI' AS market_type FROM DUAL) s
ON (t.stock_code = s.stock_code)
WHEN NOT MATCHED THEN
  INSERT (stock_code, stock_name, market_type) VALUES (s.stock_code, s.stock_name, s.market_type);

MERGE INTO stock_master t
USING (SELECT '000660' AS stock_code, 'SK하이닉스' AS stock_name, 'KOSPI' AS market_type FROM DUAL) s
ON (t.stock_code = s.stock_code)
WHEN NOT MATCHED THEN
  INSERT (stock_code, stock_name, market_type) VALUES (s.stock_code, s.stock_name, s.market_type);

MERGE INTO stock_master t
USING (SELECT '035420' AS stock_code, 'NAVER' AS stock_name, 'KOSPI' AS market_type FROM DUAL) s
ON (t.stock_code = s.stock_code)
WHEN NOT MATCHED THEN
  INSERT (stock_code, stock_name, market_type) VALUES (s.stock_code, s.stock_name, s.market_type);

MERGE INTO stock_master t
USING (SELECT '035720' AS stock_code, '카카오' AS stock_name, 'KOSDAQ' AS market_type FROM DUAL) s
ON (t.stock_code = s.stock_code)
WHEN NOT MATCHED THEN
  INSERT (stock_code, stock_name, market_type) VALUES (s.stock_code, s.stock_name, s.market_type);
