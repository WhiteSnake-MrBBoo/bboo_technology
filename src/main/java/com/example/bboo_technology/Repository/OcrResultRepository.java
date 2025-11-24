package com.example.bboo_technology.Repository;

import com.example.bboo_technology.Entiry.OcrGptResult;
import com.example.bboo_technology.Entiry.OcrResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * OCR 결과 엔티티에 대한 기본 CRUD 를 제공하는 Repository.
 * - 기본적인 save, findById, findAll 등의 메서드는 JpaRepository 에서 제공한다.
 */
@Repository
public interface OcrResultRepository extends JpaRepository<OcrResult, Long> {

    // 필요하면 여기서 검색 조건 메서드 추가 가능 (예: findByTitleContaining 등)

    /**
     * 생성일 기준 내림차순(최신순)으로 전체 조회
     */
    List<OcrResult> findAllByOrderByCreatedAtDesc();

}
