package com.example.bboo_technology.Service;


import com.example.bboo_technology.DTO.OcrResultDto;

/**
 * OCR 결과 저장/조회와 관련된 비즈니스 로직을 담당하는 서비스.
 * - 현재 단계에서는 "저장" 기능만 필요하지만,
 *   추후 히스토리 목록/검색/상세 조회 기능도 이 인터페이스에 추가할 수 있다.
 */
public interface OcrResultService {

    /**
     * OcrResultDto 를 DB 에 저장하고, 저장된 정보를 다시 DTO 로 반환한다.
     * - 반환 DTO 에는 생성된 id, createdAt, updatedAt 등의 정보가 포함될 수 있다.
     *
     * @param dto View/세션에서 전달된 OCR 결과 DTO
     * @return 실제 DB에 저장된 결과를 반영한 DTO
     */
    OcrResultDto saveOcrResult(OcrResultDto dto);

}
