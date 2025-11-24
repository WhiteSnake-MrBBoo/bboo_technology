package com.example.bboo_technology.Service.Ocrservice;

import com.example.bboo_technology.DTO.OcrGptResultDto;

import java.util.List;

/**
 * GPT 결과 저장/조회 전담 서비스.
 *
 * - "이 결과 저장" 버튼을 통해 들어온 데이터를 DB에 저장
 * - OCR 결과 기준으로 GPT 히스토리 조회
 * - (추후) 전체 히스토리/엑셀 내보내기에도 활용 가능
 */
public interface OcrGptResultService {

    /**
     * GPT 결과 저장
     * - ocrResultId + resultType + content + model + temperature 등
     * - 저장된 PK/createdAt 이 반영된 DTO 반환
     */
    OcrGptResultDto saveResult(OcrGptResultDto dto);

    /**
     * 특정 OCR 결과에 대한 GPT 결과 전체 조회 (최신순)
     */
    List<OcrGptResultDto> findByOcrResultId(Long ocrResultId);



}
