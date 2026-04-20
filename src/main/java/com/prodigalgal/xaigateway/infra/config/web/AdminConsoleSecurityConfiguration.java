package com.prodigalgal.xaigateway.infra.config.web;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.WebSessionServerSecurityContextRepository;
import org.springframework.web.server.ServerWebExchange;
import tools.jackson.databind.ObjectMapper;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
public class AdminConsoleSecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ObjectMapper objectMapper) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .requestCache(ServerHttpSecurity.RequestCacheSpec::disable)
                .anonymous(ServerHttpSecurity.AnonymousSpec::disable)
                .securityContextRepository(new WebSessionServerSecurityContextRepository())
                .exceptionHandling(spec -> spec
                        .authenticationEntryPoint((exchange, exception) ->
                                writeError(exchange, objectMapper, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录控制台。"))
                        .accessDeniedHandler((exchange, exception) ->
                                writeError(exchange, objectMapper, HttpStatus.FORBIDDEN, "FORBIDDEN", "当前控制台会话无权访问该资源。"))
                )
                .authorizeExchange(exchange -> exchange
                        .pathMatchers(HttpMethod.OPTIONS).permitAll()
                        .pathMatchers(
                                "/admin/auth/session",
                                "/admin/auth/challenge",
                                "/admin/auth/login",
                                "/admin/auth/logout",
                                "/admin/oauth/*/callback"
                        ).permitAll()
                        .pathMatchers("/admin/**").authenticated()
                        .anyExchange().permitAll()
                )
                .build();
    }

    private Mono<Void> writeError(
            ServerWebExchange exchange,
            ObjectMapper objectMapper,
            HttpStatus status,
            String code,
            String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Object traceId = exchange.getAttribute(TraceIdWebFilter.TRACE_ID_ATTRIBUTE);
        ApiErrorResponse payload = new ApiErrorResponse(code, message, traceId == null ? null : traceId.toString());
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception exception) {
            byte[] fallback = ("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}")
                    .getBytes(StandardCharsets.UTF_8);
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(fallback)));
        }
    }
}
