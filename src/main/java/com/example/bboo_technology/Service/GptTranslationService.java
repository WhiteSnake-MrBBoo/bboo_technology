package com.example.bboo_technology.Service;

import com.example.bboo_technology.DTO.TranslationDto;
import com.example.bboo_technology.Service.Ocrservice.OcrAiGptServiceImpl;
import com.example.bboo_technology.enums.TranslationLevel;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.mapper.Mapper;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * GPT 기반 번역 서비스.
 * - TranslationDto를 입력받아 번역 결과를 채워서 다시 반환한다.
 * - 실제 OpenAI 호출은 기존 GPT 모듈(OcrAiGptServiceImpl)의 callChatCompletion을 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class GptTranslationService {


    /**
     * 번역 전용 temperature.
     * - application.yml: openai.translation.temperature
     * - 설정이 없을 경우 기본값 0.3 사용.
     */
    @Value("${openai.translation.temperature:0.3}")
    private double translationTemperature;

    // ===============================
    // 번역용 모델명 (application.yml에서 주입)
    // ===============================

    /**
     * BASIC 번역용 모델명.
     * - 예: gpt-4.1-mini
     */
    @Value("${openai.models.translation-basic}")
    private String basicModel;

    /**
     * PREMIUM 번역용 모델명.
     * - 예: gpt-4.1
     */
    @Value("${openai.models.translation-premium}")
    private String premiumModel;

    /**
     * ECONOMY 번역용 모델명.
     * - 예: gpt-4o-mini
     */
    @Value("${openai.models.translation-economy}")
    private String economyModel;

    /**
     * 기존 OCR + GPT 서비스
     * - WebClient 및 OpenAI 호출 로직(callChatCompletion)을 재사용하기 위해 주입.
     */
    private final OcrAiGptServiceImpl ocrAiGptService;

    // ===============================
    // 메인 번역 메서드
    // ===============================

    /**
     * 번역 실행 메서드.
     *
     * - request 내의 level(TranslationLevel)에 따라 사용할 모델을 결정한다.
     * - 번역된 텍스트/엔진 정보/타임스탬프를 채운 새로운 TranslationDto를 반환한다.
     */
    public TranslationDto translate(TranslationDto request) {

        // 1) 레벨 없으면 BASIC 기본값 사용
        TranslationLevel level =
                (request.getLevel() != null) ? request.getLevel() : TranslationLevel.BASIC;

        // 2) 사용할 모델명 결정 (yml 기반)
        String modelName = resolveModelName(level);

        // 3) source/target 언어, 원본 텍스트를 지역 변수로 분리
        String sourceLang = request.getSourceLang();
        String targetLang = request.getTargetLang();

        // OCR로 번역된 원본 데이터 (null 방지: 없을 시 공백 처리)
        String sourceText = (request.getSourceText() != null) ? request.getSourceText() : "";

        // 4) 번역용 System Prompt 구성
        String systemPrompt = buildSystemPrompt(sourceLang, targetLang);

        // 5) GPT 호출 (실제 OpenAI API 호출은 OcrAiGptServiceImpl에 위임)
        String translatedText = ocrAiGptService.callChatCompletion(
                modelName,
                translationTemperature,    // application.yml: openai.translation.temperature
                systemPrompt,
                sourceText
        );

        // 6) 타임스탬프 계산
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt =
                (request.getCreatedAt() != null) ? request.getCreatedAt() : now;
        LocalDateTime updatedAt = now;

        // 7) 결과 DTO 구성 (builder 결과를 지역 변수에 담고 반환)
        TranslationDto result = TranslationDto.builder()
                .id(request.getId())
                .ocrResultId(request.getOcrResultId())

                .sourceLang(sourceLang)
                .targetLang(targetLang)
                .sourceText(sourceText)

                .translatedText(translatedText)
                .engine(modelName)
                .level(level)

                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .saved(false)   // 세션 단계이므로 기본은 false
                .build();

        return result;
    }

    // ===============================
    // 내부 유틸 메서드
    // ===============================

    /**
     * TranslationLevel → yml에 정의된 모델명 매핑.
     */
    private String resolveModelName(TranslationLevel level) {
        return switch (level) {
            case BASIC -> basicModel;
            case PREMIUM -> premiumModel;
            case ECONOMY -> economyModel;
        };
    }

    /**
     * 번역용 System Prompt 템플릿.
     * - sourceLang/targetLang에 따라 번역 방향을 명확히 지정.
     * - 출력은 "번역된 문장만" 달라고 요청해서 후처리 최소화.
     */
    private String buildSystemPrompt(String sourceLang, String targetLang) {
        // 1) 언어 코드 null 방어
        String src = (sourceLang != null) ? sourceLang : "source language";
        String tgt = (targetLang != null) ? targetLang : "target language";

        // 2) 템플릿 문자열 분리
        String template = """
                You are a professional translator.
                - Translate the user's message from %s to %s.
                - Preserve the original meaning and nuance as much as possible.
                - Use natural, fluent expressions appropriate for the target language.
                - Do NOT add explanations or comments.
                - Respond with the translated text ONLY, without quotes.
                """;

        // 3) 템플릿에 값 채워 넣기
        String prompt = String.format(template, src, tgt);

        // 4) 완성된 프롬프트만 반환
        return prompt;
    }

}
