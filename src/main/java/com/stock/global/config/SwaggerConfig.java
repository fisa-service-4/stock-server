package com.stock.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
    info =
        @Info(
            title = "stock-server API",
            version = "v1",
            description = "증권 도메인 내부 API — 계좌 / 주문 / 체결 / 보유종목 / 포트폴리오"),
    servers = @Server(url = "/", description = "stock-server (port 8082)"))
@Configuration
public class SwaggerConfig {}
