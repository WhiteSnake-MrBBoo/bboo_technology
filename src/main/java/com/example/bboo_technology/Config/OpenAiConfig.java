package com.example.bboo_technology.Config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * OpenAI API 설정 및 WebClient Bean 정의.
 *
 * - application.yml 의 openai.* 설정을 읽어온다.
 * - openAiWebClient Bean 하나만 만들어 두고,
 *   실제 서비스(OcrAiGptService 등)에서는 이 Bean을 주입받아서 사용한다.
 *
 *   openai:
 *     api:
 *       key: ${OPENAI_API_KEY}
 *       base-url: https://api.openai.com/v1
 *       default-model: gpt-3.5-turbo
 *       temperature: 0.4
 *     models:
 *       summary: gpt-3.5-turbo
 *       host-script: gpt-3.5-turbo
 *       marketing-points: gpt-3.5-turbo
 */
@Slf4j
@Getter
@Configuration
public class OpenAiConfig {

    // =========================
    // openai.api.* 공통 설정
    // =========================

    /**
     * OpenAI API 키
     * - OS 환경변수 OPENAI_API_KEY 에서 주입됨.
     */
    @Value("${openai.api.key}")
    private String apiKey;

    /**
     * OpenAI REST 기본 URL
     * - 예: https://api.openai.com/v1
     * - WebClient 의 baseUrl 로 사용된다.
     */
    @Value("${openai.api.base-url}")
    private String baseUrl;

    /**
     * 기본 모델 (fallback 용)
     * - summary/host/marketing 에서 별도 모델 지정이 없다면 이 값 사용.
     */
    @Value("${openai.api.default-model}")
    private String defaultModel;

    /**
     * 기본 temperature 값.
     * - 요약/정보 위주라면 0.2~0.4 정도가 무난.
     * - application.yml 에 없으면 0.4 로 기본값 사용.
     */
    @Value("${openai.api.temperature:0.4}")
    private double defaultTemperature;

    // =========================
    // openai.models.* 용도별 모델
    // =========================

    /**
     * 3-1. 상품 정보 요약용 모델
     */
    @Value("${openai.models.summary}")
    private String summaryModel;

    /**
     * 3-2. 쇼호스트 멘트 스크립트용 모델
     */
    @Value("${openai.models.host-script}")
    private String hostScriptModel;

    /**
     * 3-3. 마케팅 포인트 & 자막 문구용 모델
     */
    @Value("${openai.models.marketing-points}")
    private String marketingPointsModel;

    // =========================
    // OpenAI 전용 WebClient Bean
    // =========================

    /**
     * OpenAI API 호출 전용 WebClient.
     *
     * - baseUrl      : openai.api.base-url
     * - Authorization: Bearer {API_KEY}
     * - Content-Type : application/json
     *
     * 이후 Service 코드에서는:
     *   webClient.post()
     *     .uri("/chat/completions")   // base-url 이 /v1 까지 잡혀 있으면 이렇게
     *     .bodyValue(requestBody)
     *     ...
     * 이런 식으로 사용하면 된다.
     */
    @Bean
    public WebClient openAiWebClient(WebClient.Builder builder) {

        if (apiKey == null || apiKey.isBlank()) {
            log.warn("⚠ OpenAI API 키가 설정되어 있지 않습니다. " +
                    "환경변수 OPENAI_API_KEY 및 application.yml 설정을 확인해 주세요.");
        }

        return builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }


    /*Open Api Key : 테스트용 함수
    * 시스템 환경 변수에 제대로 설정 됐는지 확인 하기
    * */
    @PostConstruct
    public void logConfigOnStartup() {
        String masked = null;
        if (apiKey != null && !apiKey.isBlank()) {
            int len = apiKey.length();
            // 앞 4자리만 살짝 보여주고 나머지는 별로 마스킹
            String prefix = apiKey.substring(0, Math.min(4, len));
            masked = prefix + "**** (length=" + len + ")";
        }

        log.info("=== OpenAI 설정 확인 ===");
        log.info("API Key 존재 여부: {}", (apiKey != null && !apiKey.isBlank()));
        log.info("API Key (masked): {}", masked);
        log.info("Base URL: {}", baseUrl);
        log.info("Default Model: {}", defaultModel);
        log.info("Summary Model: {}", summaryModel);
        log.info("Host Script Model: {}", hostScriptModel);
        log.info("Marketing Points Model: {}", marketingPointsModel);
        log.info("=======================");
    }
}
