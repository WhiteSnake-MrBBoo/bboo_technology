package com.example.bboo_technology.Service.Ocrservice;

import com.example.bboo_technology.Config.OpenAiConfig;
import com.example.bboo_technology.DTO.OcrResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OcrAiGptService 구현체.
 *
 * - OpenAiConfig 로부터 WebClient + 모델 이름들을 주입받는다.
 * - 공통 메서드 callChatCompletion(...) 에서 실제 OpenAI 호출을 처리하고,
 *   각 기능(요약/멘트/마케팅)은 프롬프트만 다르게 구성한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrAiGptServiceImpl implements OcrAiGptService {

    private final WebClient openAiWebClient;   // OpenAiConfig에서 생성한 WebClient Bean
    private final OpenAiConfig openAiConfig;   // 모델 이름, 기본 temperature 등 설정

    // 공통 타임아웃(필요하면 yml로 빼도 됨)
    private static final Duration API_TIMEOUT = Duration.ofSeconds(60);

    // =========================
    // 3-1. 상품 정보 요약
    // =========================
    @Override
    public String generateSummary(OcrResultDto ocr) {

        String model = openAiConfig.getSummaryModel();          // yml: openai.models.summary
        double temperature = openAiConfig.getDefaultTemperature();

        String systemPrompt =
                "너는 상품 상세 설명서를 요약해 주는 전문가야. " +
                        "입력으로 주어지는 텍스트는 홈쇼핑/온라인몰용 상품 기술서 OCR 결과다. " +
                        "불필요한 잡음을 제거하고, 핵심 정보만 정리된 한국어 요약을 만들어 줘.";

        String userPrompt =
                "다음 상품 기술서를 바탕으로, 홈쇼핑에서 사용할 수 있는 '상품 정보 요약'을 작성해 줘.\n" +
                        "- 항목별 Bullet 형태로 정리\n" +
                        "- 주요 효능/특징, 사용 대상, 사용 방법, 주의사항 등을 포함\n" +
                        "- 너무 과장되지 않게, 객관적인 설명 위주\n\n" +
                        "=== 상품 기술서 OCR 텍스트 시작 ===\n" +
                        safeText(ocr.getEditedText()) +
                        "\n=== 끝 ===";

        return callChatCompletion(model, temperature, systemPrompt, userPrompt);
    }

    // =========================
    // 3-2. 쇼호스트 멘트
    // =========================
    @Override
    public String generateHostScript(OcrResultDto ocr) {

        String model = openAiConfig.getHostScriptModel();       // yml: openai.models.host-script
        double temperature = openAiConfig.getDefaultTemperature();

        String title = ocr.getTitle() != null ? ocr.getTitle() : "(상품명 미지정)";

        String systemPrompt =
                "너는 TV 홈쇼핑 쇼호스트 멘트를 작성하는 카피라이터야. " +
                        "시청자가 이해하기 쉽고, 자연스럽게 구매를 유도하는 멘트를 만들어야 한다.";

        String userPrompt =
                "다음 상품 기술서를 바탕으로 TV 홈쇼핑 쇼호스트용 멘트를 작성해 줘.\n" +
                        "- 상품명: " + title + "\n" +
                        "- 오프닝 인사, 문제 제기, 상품 소개, 혜택 강조, 마무리 멘트 순서\n" +
                        "- 시간 기준 1~2분 길이 분량\n" +
                        "- 과도한 의학적 효능 주장이나 허위/과장은 피하고, '도와줄 수 있습니다' 수준의 표현 사용\n\n" +
                        "=== 상품 기술서 OCR 텍스트 시작 ===\n" +
                        safeText(ocr.getEditedText()) +
                        "\n=== 끝 ===";

        return callChatCompletion(model, temperature, systemPrompt, userPrompt);
    }

    // =========================
    // 3-3. 마케팅 포인트 & 자막 문구
    // =========================
    @Override
    public String generateMarketingPoints(OcrResultDto ocr) {

        String model = openAiConfig.getMarketingPointsModel();  // yml: openai.models.marketing-points
        double temperature = openAiConfig.getDefaultTemperature();

        String title = ocr.getTitle() != null ? ocr.getTitle() : "(상품명 미지정)";

        String systemPrompt =
                "너는 TV 홈쇼핑/온라인몰용 마케팅 카피를 작성하는 전문가야. " +
                        "짧고 임팩트 있는 문구를 만들어야 한다.";

        String userPrompt =
                "다음 상품 기술서를 바탕으로 TV 홈쇼핑 방송에서 사용할 수 있는 '마케팅 포인트 & 자막 문구'를 작성해 줘.\n" +
                        "- 1) 메인 카피 3개 (15자 내외)\n" +
                        "- 2) 서브 카피 3개 (20~25자 내외)\n" +
                        "- 3) 화면 하단 자막용 문구 5개 (15자 내외)\n" +
                        "- 한국어로 작성\n" +
                        "- 과장된 표현은 피하고, 사실 기반의 장점을 강조\n\n" +
                        "상품명: " + title + "\n\n" +
                        "=== 상품 기술서 OCR 텍스트 시작 ===\n" +
                        safeText(ocr.getEditedText()) +
                        "\n=== 끝 ===";

        return callChatCompletion(model, temperature, systemPrompt, userPrompt);
    }

    // =========================
    // 공통: OpenAI Chat Completion 호출
    // =========================

    /**
     * OpenAI /v1/chat/completions 엔드포인트를 호출하는 공통 메서드.
     *
     * @param model         사용할 모델 이름 (예: gpt-3.5-turbo)
     * @param temperature   생성 temperature
     * @param systemPrompt  system 역할 프롬프트
     * @param userPrompt    user 역할 프롬프트
     * @return choices[0].message.content (실패 시 예외 대신 안내 텍스트 반환)
     */
    public String callChatCompletion(String model,
                                        double temperature,
                                        String systemPrompt,
                                        String userPrompt) {

        try {
            // OpenAI Chat Completion 요청 바디 (간단하게 Map으로 구성)
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "temperature", temperature,
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", systemPrompt
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", userPrompt
                            )
                    )
            );

            // WebClient 동기(block) 호출
            OpenAiChatResponse response = openAiWebClient.post()
                    .uri("/chat/completions")  // baseUrl 에 /v1 까지 포함되어 있다고 가정
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(OpenAiChatResponse.class)
                    .timeout(API_TIMEOUT)
                    .block();

            if (response == null || response.getChoices() == null || response.getChoices().isEmpty()) {
                log.warn("OpenAI 응답이 비어 있습니다.");
                return "[오류] OpenAI 응답이 비어 있습니다. 잠시 후 다시 시도해 주세요.";
            }

            String content = response.getChoices().get(0).getMessage().getContent();
            if (content == null || content.isBlank()) {
                return "[오류] OpenAI 응답에서 내용을 찾을 수 없습니다.";
            }

            return content.trim();

        } catch (Exception e) {
            log.error("OpenAI Chat Completion 호출 중 예외 발생", e);
            return "[오류] AI 생성 중 문제가 발생했습니다. 잠시 후 다시 시도해 주세요.";
        }
    }

    /**
     * OCR 텍스트가 null인 경우를 대비한 안전 처리용 헬퍼.
     */
    private String safeText(String text) {
        return text != null ? text : "";
    }

    // =========================
    // OpenAI 응답 매핑용 내부 클래스
    //  - 필요한 필드만 최소한으로 정의
    // =========================

    @lombok.Data
    public static class OpenAiChatResponse {
        private List<Choice> choices;

        @lombok.Data
        public static class Choice {
            private Message message;
        }

        @lombok.Data
        public static class Message {
            private String role;
            private String content;
        }
    }
}
