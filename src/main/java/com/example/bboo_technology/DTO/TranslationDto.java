package com.example.bboo_technology.DTO;

import com.example.bboo_technology.enums.TranslationLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 번역 결과를 표현하는 DTO.
 * - OCR와 독립적인 "번역 도메인"으로 설계.
 * - 향후 채팅/공지/리뷰 등 다른 곳에서도 재사용 가능.
 * 채팅형 페이지 / 관리자 페이지의 설명 번역 / 공지/리뷰 자동 번역

 * 외부 텍스트 입력 후 바로 번역
 * - Session, Service, Repository(엔티티 변환 전 단계) 사이 공통 사용.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranslationDto {

    /**
     * (선택) DB 저장 후 매핑되는 PK
     * - 아직 저장 전 단계에서는 null일 수 있다.
     */
    private Long id;

    /**
     * (선택) OCR 결과와 연동할 경우 참조하는 FK.
     * - OCR 없이 단독 번역일 수도 있기 때문에 null 허용.
     */
    private Long ocrResultId;

    /**
     * 번역에 사용된 원본 언어 코드 (예: "ko", "en", "ja")
     */
    private String sourceLang;

    /**
     * 번역의 타겟 언어 코드 (예: "en", "ko", "ja")
     */
    private String targetLang;

    /**
     * 실제 번역의 원본 텍스트.
     * - OCR 결과에서 가져오거나, 사용자가 직접 입력한 텍스트.
     */
    private String sourceText;

    /**
     * 번역된 텍스트 결과.
     * - View의 "번역 결과" textarea와 매핑될 값.
     */
    private String translatedText;

    /**
     * 번역에 사용된 엔진/모델 이름.
     * - 예: "gpt-4.1-mini", "gpt-4.1", "gpt-4o-mini" 등
     */
    private String engine;

    /**
     * 번역 품질/비용 레벨.
     * - BASIC / PREMIUM / ECONOMY
     */
    private TranslationLevel level;

    /**
     * 번역 작업 생성 시각.
     */
    private LocalDateTime createdAt;

    /**
     * 번역 결과가 마지막으로 갱신된 시각.
     */
    private LocalDateTime updatedAt;

    /**
     * (옵션) DB에 저장 완료된 상태인지 여부를 나타내는 플래그.
     * - 세션에서 작업 중인지, 히스토리로 저장된 상태인지 구분할 때 사용.
     */
    private boolean saved;
}
