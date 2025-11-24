package com.example.bboo_technology.Service.Ocrservice;

import com.example.bboo_technology.DTO.OcrResultDto;

/**
 * OCR 결과를 바탕으로 OpenAI(GPT)를 호출하는 전담 서비스.
 *
 * 3가지 기능:
 *  - 3-1. 상품 정보 요약 생성
 *  - 3-2. 쇼호스트 멘트 생성
 *  - 3-3. 마케팅 포인트 & 자막 문구 생성
 */
public interface OcrAiGptService {

    /**
     * 3-1. 상품 정보 요약 생성
     */
    String generateSummary(OcrResultDto ocr);

    /**
     * 3-2. 쇼호스트 멘트 스크립트 생성
     */
    String generateHostScript(OcrResultDto ocr);

    /**
     * 3-3. 마케팅 포인트 & 자막 문구 생성
     */
    String generateMarketingPoints(OcrResultDto ocr);
}
