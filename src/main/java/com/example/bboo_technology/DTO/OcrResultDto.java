package com.example.bboo_technology.DTO;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * OCR 결과를 표현하는 DTO.
 * - View, Session, Service, Repository(엔티티 변환 전 단계) 사이에서 공통으로 사용한다.
 * - "임시 작업 데이터"로서 세션에 통째로 저장되는 객체이기도 하다.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class OcrResultDto {

    /**
     * (선택) DB 저장 후 매핑되는 PK
     * - 아직 저장 전 단계에서는 null 일 수 있다.
     * - 나중에 OCR 히스토리 조회 기능을 만들 때 활용 가능.
     */
    private Long id;

    /**
     * 업로드된 원본 파일명
     * 예) "receipt_2025_11_19.pdf"
     */
    private String originalFileName;

    /**
     * 파일 타입 (IMAGE / PDF 등)
     * - 백엔드에서 분기 및 View 표시 용도로 사용한다.
     */
    private String fileType;

    /**
     * PDF일 경우 페이지 수.
     * - IMAGE 파일일 경우 null 또는 1로 설정할 수 있다.
     */
    private Integer pageCount;

    /**
     * 저장할 제목.
     * - View 오른쪽 상단 "저장할 제목" 입력창과 매핑된다.
     * - DB 저장 시 title 컬럼으로 직결될 값.
     */
    private String title;

    /**
     * OCR로 최초 추출된 원본 텍스트.
     * - 사용자가 수정하기 전 상태.
     * - 디버깅이나 향후 비교를 위해 별도로 유지할 수 있다.
     */
    private String ocrText;

    /**
     * 사용자가 View에서 수정한 텍스트.
     * - "저장하기" 클릭 시 서버로 넘어오는 값.
     * - 실제 DB 저장 시 이 값을 저장하는 것이 일반적이다.
     */
    private String editedText;

    /**
     * (향후) 번역 API를 통해 생성되는 번역된 텍스트.
     * - 현재 단계에서는 아직 사용하지 않지만, 필드만 미리 정의해 둔다.
     * - 나중에 TranslationService 연동 후 채워줄 예정.
     */
    private String translatedText;

    /**
     * 생성 시각 (옵션)
     * - 세션에 저장되는 시점이나 DB 저장 시점에 설정할 수 있다.
     */
    private LocalDateTime createdAt;

    /**
     * 수정 시각 (옵션)
     * - DB 업데이트 시점에 활용 가능.
     */
    private LocalDateTime updatedAt;

    /**
     * (편의용) 이 DTO가 DB에 저장 완료된 상태인지 여부를 나타내는 플래그.
     * - 세션에서 작업 중인지, 이미 저장된 이력인지 구분할 때 사용 가능.
     */
    private boolean saved;


}

