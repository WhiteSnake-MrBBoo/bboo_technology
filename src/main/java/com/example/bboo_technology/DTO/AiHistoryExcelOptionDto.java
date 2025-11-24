package com.example.bboo_technology.DTO;


import lombok.Data;

import java.util.List;

/**
 * AI 히스토리 엑셀 내보내기 옵션 DTO.
 * - 모달에서 넘어온 값들을 한 번에 받기 위한 용도
 */
@Data


public class AiHistoryExcelOptionDto {

    // 내보낼 범위: ALL(현재 목록 전체) / SELECTED(체크 항목만)
    private String scope; // "ALL" or "SELECTED"

    // 선택된 히스토리 ID 리스트 (scope == SELECTED 일 때만 의미 있음)
    private List<Long> selectedIds;

    // 컬럼 포함 여부
    private boolean includeId;
    private boolean includeResultType;
    private boolean includeOcrTitle;
    private boolean includeOcrFileName;
    private boolean includeCreatedAt;
    private boolean includeModel;
    private boolean includeContent;
    private boolean includeTokens; // prompt/completion/total 같이 쓸지 여부

    // 저장할 파일명 (확장자 제외, 비어 있으면 기본값 사용)
    private String fileName;
}
