package com.example.bboo_technology.Entiry;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * OCR 결과를 DB에 저장하기 위한 엔티티.
 * - 한 번 저장된 결과는 히스토리/검색/재사용 등에 활용할 수 있다.
 */
@Entity
@Table(name = "ocr_result")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrResult {

    /**
     * 기본 키 (auto increment)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 사용자가 저장 시 입력한 제목.
     * 예) "2025-11-19 호텔 영수증", "여권 OCR 결과"
     */
    @Column(nullable = false, length = 200)
    private String title;

    /**
     * 업로드된 원본 파일명.
     */
    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    /**
     * 파일 타입 (IMAGE / PDF 등)
     */
    @Column(name = "file_type", length = 50)
    private String fileType;

    /**
     * PDF 페이지 수 (이미지는 1 페이지로 간주).
     */
    @Column(name = "page_count")
    private Integer pageCount;

    /**
     * OCR로 최초 추출된 텍스트.
     * - MariaDB 기준으로 긴 텍스트를 수용하기 위해 LONGTEXT 사용.
     */
    @Lob
    @Column(name = "ocr_text", columnDefinition = "LONGTEXT")
    private String ocrText;

    /**
     * 사용자가 수정한 텍스트 (최종본).
     * - 실제로 조회/검색 등에 사용할 값.
     */
    @Lob
    @Column(name = "edited_text", columnDefinition = "LONGTEXT")
    private String editedText;

    /**
     * (옵션) 번역된 텍스트.
     * - 번역 기능 연동 이후 사용 예정.
     */
    @Lob
    @Column(name = "translated_text", columnDefinition = "LONGTEXT")
    private String translatedText;

    /**
     * 생성 시각.
     */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /**
     * 마지막 수정 시각.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 엔티티가 처음 저장되기 전 자동 호출.
     * - createdAt 기본값을 현재 시간으로 설정.
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 엔티티가 업데이트되기 전 자동 호출.
     * - updatedAt 을 현재 시간으로 갱신.
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }


}
