package com.example.bboo_technology.Entiry;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * GPT가 생성한 결과 텍스트를 저장하는 엔티티.
 *
 * - 어떤 OCR 결과(OcrResult)에서 나온 것인지 FK로 연결
 * - 결과 타입: SUMMARY / HOST_SCRIPT / MARKETING_POINTS
 * - GPT 응답 텍스트, 사용된 모델, temperature, 토큰 사용량 등을 기록
 */
@Entity
@Table(name = "ocr_gpt_result")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OcrGptResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 어떤 OCR 결과에서 나온 GPT 결과인지 연결하는 FK.
     * - 하나의 OcrResult에 여러 GPT 결과가 연결될 수 있다.
     *   (예: SUMMARY 1개, HOST_SCRIPT 여러 버전 등)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ocr_result_id", nullable = false)
    private OcrResult ocrResult;

    /**
     * GPT 결과 종류
     * - SUMMARY           : 상품 정보 요약
     * - HOST_SCRIPT       : 쇼호스트 멘트
     * - MARKETING_POINTS  : 마케팅 포인트 & 자막 문구
     */
    @Column(name = "result_type", length = 30, nullable = false)
    private String resultType;

    /**
     * GPT가 생성한 결과 텍스트 전체
     */
    @Lob
    @Column(name = "content", columnDefinition = "LONGTEXT", nullable = false)
    private String content;

    /**
     * 사용한 모델명 (예: gpt-3.5-turbo, gpt-4.1-mini 등)
     */
    @Column(name = "model", length = 50, nullable = false)
    private String model;

    /**
     * 요청 시 사용한 temperature (옵션)
     */
    @Column(name = "temperature")
    private Double temperature;

    /**
     * 토큰 사용량 정보 (선택)
     * - 추후 OpenAI 응답에서 usage 필드를 파싱하여 저장할 때 사용
     */
    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "total_tokens")
    private Integer totalTokens;

    /**
     * 생성/수정 시간
     * - 프로젝트에 BaseEntity가 있다면 BaseEntity를 상속받는 형태로 리팩토링 가능.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
