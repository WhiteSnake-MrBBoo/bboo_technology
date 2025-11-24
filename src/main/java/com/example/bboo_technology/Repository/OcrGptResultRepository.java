package com.example.bboo_technology.Repository;

import com.example.bboo_technology.Entiry.OcrGptResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * OCR GPT 결과용 Repository.
 * - 특정 OCR 결과에 대한 GPT 결과 목록 조회
 * - OCR 결과 + 타입별 최신 결과 조회
 */
public interface OcrGptResultRepository extends JpaRepository<OcrGptResult, Long> {

    /**
     * 특정 OCR 결과에 대한 GPT 결과 전체(최신순)
     */
    List<OcrGptResult> findByOcrResult_IdOrderByCreatedAtDesc(Long ocrResultId);

    /**
     * 특정 OCR 결과 + 결과 타입별 최신 1건 조회
     * - 예: ocrResultId=10, resultType="SUMMARY" 인 가장 최근 결과
     */
    Optional<OcrGptResult> findFirstByOcrResult_IdAndResultTypeOrderByCreatedAtDesc(
            Long ocrResultId,
            String resultType
    );

    /**
     * ✅ 전체 GPT 결과를 생성일 기준 최신순으로 조회
     * - 히스토리 화면 기본 리스트에서 사용
     */
    List<OcrGptResult> findAllByOrderByCreatedAtDesc();

}
