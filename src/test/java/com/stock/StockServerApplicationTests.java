package com.stock;

import com.stock.global.config.DataInitializer;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@TestPropertySource(
    properties = {
      "spring.jpa.hibernate.ddl-auto=none",
      "spring.jpa.database-platform=org.hibernate.dialect.OracleDialect",
      "spring.sql.init.mode=never",
      "spring.jpa.defer-datasource-initialization=false"
    })
class StockServerApplicationTests {

  @MockitoBean DataSource dataSource;
  @MockitoBean DataInitializer dataInitializer;

  @Test
  void contextLoads() {}
}
