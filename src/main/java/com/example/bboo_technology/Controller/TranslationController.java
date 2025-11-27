package com.example.bboo_technology.Controller;

import com.example.bboo_technology.DTO.OcrResultDto;
import com.example.bboo_technology.DTO.TranslationDto;
import com.example.bboo_technology.Service.GptTranslationService;
import com.example.bboo_technology.enums.TranslationLevel;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 번역 전용 컨트롤러.
 *
 * - OCR, 채팅, 리뷰 등 "텍스트 번역" 관련 API를 이 컨트롤러에 모아둔다.
 * - 현재 단계에서는 OCR 결과를 번역하는 엔드포인트만 구현한다.
 *
 * URL 구조 예:
 *   POST /api/translation/ocr   → OCR 결과 텍스트 번역
 *   (향후 확장)
 *   POST /api/translation/chat  → 채팅 메시지 번역
 *   POST /api/translation/review → 리뷰/설명 번역
 */
@Slf4j
@RestController
@RequestMapping("/api/translation")
@RequiredArgsConstructor
public class TranslationController {

    /**
     * 세션에서 OCR 결과를 가져올 때 사용할 키.
     * - OCR 화면에서 OcrResultDto를 세션에 저장할 때 동일한 키를 사용해야 한다.
     * - 예: OCRController에서 세션에 저장할 때:
     *       session.setAttribute("OCR_RESULT", ocrResultDto);
     */
    private static final String OCR_RESULT_SESSION_KEY = "OCR_RESULT";

    /**
     * 세션에 번역 결과를 저장할 때 사용할 키.
     * - 번역 실행 후 TranslationDto를 이 키로 세션에 보관한다.
     * - "저장하기" 버튼 클릭 시 이 값을 읽어서 DB에 저장할 수 있다.
     */
    private static final String OCR_TRANSLATION_SESSION_KEY = "OCR_TRANSLATION";

    /**
     * GPT 기반 번역 서비스.
     * - GptTranslationService는 번역 전용 Service (이전에 구현한 클래스).
     */
    private final GptTranslationService gptTranslationService;

    // =======================================================
    // 3. OCR 결과 텍스트 번역 엔드포인트
    //    - POST /api/translation/ocr
    //    - 프론트에서 fetch/Ajax로 호출 → JSON 응답으로 번역 결과 반환
    // =======================================================

    /**
     * OCR 결과 텍스트를 GPT로 번역하는 엔드포인트.
     *
     * 요청 파라미터:
     *  - sourceText (optional)
     *      : 명시적으로 번역할 텍스트를 보내고 싶을 때 사용.
     *        null 또는 빈 문자열이면, 세션의 OcrResultDto에서 editedText → ocrText 순으로 사용.
     *
     *  - sourceLang (default: "ko")
     *      : 원본 언어 코드 (예: "ko", "en", "ja"...).
     *
     *  - targetLang (default: "en")
     *      : 타겟 언어 코드.
     *
     *  - level (default: "BASIC")
     *      : 번역 레벨 (BASIC / PREMIUM / ECONOMY).
     *
     * 동작 흐름:
     *  1) 세션에서 OcrResultDto(OCR_RESULT)를 가져온다 (없어도 동작 가능).
     *  2) 최종 번역 원본 텍스트(finalSourceText)를 결정한다.
     *  3) TranslationDto를 구성하여 GptTranslationService.translate() 호출.
     *  4) 결과 TranslationDto를 세션(OCR_TRANSLATION)에 저장.
     *  5) 번역된 텍스트와 메타 정보를 JSON(TranslationResponse)으로 응답한다.
     */
    @PostMapping("/ocr")
    public ResponseEntity<TranslationResponse> translateOcrText(
            @RequestParam(required = false) String sourceText,
            @RequestParam(defaultValue = "ko") String sourceLang,
            @RequestParam(defaultValue = "en") String targetLang,
            @RequestParam(defaultValue = "BASIC") String level,
            HttpSession session
    ) {

        // 1) 세션에서 현재 OCR 작업 정보를 가져온다.
        //    - OCR 화면에서 이미 OcrResultDto를 세션에 저장해 두었다고 가정.
        OcrResultDto ocrResult = (OcrResultDto) session.getAttribute(OCR_RESULT_SESSION_KEY);

        // 2) 최종 번역 원본 텍스트(finalSourceText) 결정
        //    - 2-1. 요청 파라미터 sourceText가 있으면 그 값을 우선 사용
        //    - 2-2. 없으면 세션의 editedText → ocrText 순으로 fallback
        String finalSourceText = sourceText;

        if (finalSourceText == null || finalSourceText.isBlank()) {
            if (ocrResult != null) {
                String edited = ocrResult.getEditedText();
                String ocrText = ocrResult.getOcrText();

                if (edited != null && !edited.isBlank()) {
                    finalSourceText = edited;
                } else if (ocrText != null && !ocrText.isBlank()) {
                    finalSourceText = ocrText;
                }
            }
        }

        // null 방지: 그래도 없으면 빈 문자열로 처리
        if (finalSourceText == null) {
            finalSourceText = "";
        }

        // 3) 번역 레벨 파라미터를 Enum으로 변환
        //    - 잘못된 값이 들어오면 BASIC으로 fallback
        TranslationLevel translationLevel;
        try {
            translationLevel = TranslationLevel.valueOf(level.toUpperCase());
        } catch (IllegalArgumentException ex) {
            log.warn("지원하지 않는 번역 레벨 파라미터: {} → BASIC으로 대체", level);
            translationLevel = TranslationLevel.BASIC;
        }

        // 4) TranslationDto 요청 객체 구성
        Long ocrResultId = (ocrResult != null) ? ocrResult.getId() : null;

        TranslationDto requestDto = TranslationDto.builder()
                .id(null)                 // 아직 DB 저장 전이므로 null
                .ocrResultId(ocrResultId) // OCR와 연동하고 싶을 때 사용 (없으면 null)
                .sourceLang(sourceLang)
                .targetLang(targetLang)
                .sourceText(finalSourceText)
                .level(translationLevel)
                .build();

        // 5) GPT 기반 번역 실행 (Service 로직 호출)
        TranslationDto translatedDto = gptTranslationService.translate(requestDto);

        // 6) 번역 결과를 세션에 저장
        //    - "저장하기" 버튼 클릭 시, 이 값을 꺼내서 DB에 저장할 수 있다.
        session.setAttribute(OCR_TRANSLATION_SESSION_KEY, translatedDto);

        // 7) 프론트로 돌려줄 응답 DTO 구성
        TranslationResponse response = new TranslationResponse(
                translatedDto.getTranslatedText(),
                translatedDto.getSourceLang(),
                translatedDto.getTargetLang(),
                translatedDto.getEngine(),
                translatedDto.getLevel().name()
        );

        // 8) JSON 응답 반환
        return ResponseEntity.ok(response);
    }

    // ===========================
    // 번역 응답용 내부 DTO (JSON 응답)
    // ===========================

    /**
     * 프론트로 반환할 번역 응답 DTO.
     * - 필요한 최소 정보만 담아서 보낸다.
     * - (원한다면 향후 토큰 사용량, 처리 시간 등의 필드를 추가할 수 있다.)
     */
    public record TranslationResponse(
            String translatedText,   // 번역된 문장
            String sourceLang,       // 원본 언어 코드
            String targetLang,       // 타겟 언어 코드
            String engine,           // 사용된 모델명 (예: gpt-4.1-mini)
            String level             // 번역 레벨 (BASIC / PREMIUM / ECONOMY)
    ) {}
}
