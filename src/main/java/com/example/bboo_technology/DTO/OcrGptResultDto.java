package com.example.bboo_technology.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * GPT 결과를 표현하는 DTO.
 * - Controller, Service, View 사이에서 사용.
 * - ocrResultId 로 원본 OCR 결과와 연결된다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrGptResultDto {

    private Long id;

    /**
     * 어떤 OCR 결과(OcrResult)의 결과인지 구분하기 위한 FK ID
     */
    private Long ocrResultId;

    /**
     * 결과 타입: SUMMARY / HOST_SCRIPT / MARKETING_POINTS
     */
    private String resultType;

    /**
     * GPT가 생성한 결과 텍스트
     */
    private String content;

    /**
     * 사용한 모델명 (예: gpt-3.5-turbo)
     */
    private String model;

    /**
     * temperature
     */
    private Double temperature;

    /**
     * 토큰 사용량 정보
     */
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    /**
     * 생성 시각
     */
    private LocalDateTime createdAt;
}
