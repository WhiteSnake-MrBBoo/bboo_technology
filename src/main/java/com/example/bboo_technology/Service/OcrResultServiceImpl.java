package com.example.bboo_technology.Service;

import com.example.bboo_technology.DTO.OcrResultDto;
import com.example.bboo_technology.Entiry.OcrResult;
import com.example.bboo_technology.Repository.OcrResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * OcrResultService 의 기본 구현체.
 * - OcrResultDto 를 OcrResult 엔티티로 변환하여 DB 에 저장한다.
 * - 저장 후 생성된 PK(id) 및 타임스탬프를 다시 DTO 에 반영하여 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrResultServiceImpl implements OcrResultService {

    private final OcrResultRepository ocrResultRepository;
    // private final ModelMapper modelMapper;  // 프로젝트에서 ModelMapper 사용 중이면 주입받아 사용 가능

    /**
     * OCR 결과를 DB 에 저장하는 메서드.
     * - @Transactional 로 트랜잭션 경계를 명확히 한다.
     */
    @Override
    @Transactional
    public OcrResultDto saveOcrResult(OcrResultDto dto) {

        try {
            // 1) DTO → 엔티티 수동 매핑
            //    (ModelMapper를 써도 되지만, 여기선 필드가 명확하므로 직접 매핑)
            OcrResult entity = OcrResult.builder()
                    .id(dto.getId())  // 보통 null 이어야 함 (신규 저장)
                    .title(dto.getTitle())
                    .originalFileName(dto.getOriginalFileName())
                    .fileType(dto.getFileType())
                    .pageCount(dto.getPageCount())
                    .ocrText(dto.getOcrText())
                    .editedText(dto.getEditedText())
                    .translatedText(dto.getTranslatedText())
                    // createdAt / updatedAt 은 @PrePersist / @PreUpdate 에서 자동 처리
                    .build();

            // 2) JPA 를 통해 DB 저장 (insert or update)
            OcrResult saved = ocrResultRepository.save(entity);

            // 3) 저장된 엔티티 정보를 DTO 에 다시 반영
            dto.setId(saved.getId());
            dto.setCreatedAt(saved.getCreatedAt());
            dto.setUpdatedAt(saved.getUpdatedAt());
            dto.setSaved(true);   // 저장 완료 표시

            log.info("OCR 결과 저장 완료 - id={}, title={}", saved.getId(), saved.getTitle());

            return dto;

        } catch (Exception e) {
            // (중요) 저장 중 예외 발생 시 로그 남기고 상위 레이어에 의미 있는 메시지 전달
            log.error("OCR 결과 저장 중 예외 발생", e);
            // 여기서는 OcrProcessingException 재사용
            throw new OcrProcessingException("OCR 결과를 저장하는 중 오류가 발생했습니다.", e);
        }
    }
}
