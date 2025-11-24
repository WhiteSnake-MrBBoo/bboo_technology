package com.example.bboo_technology.Service.Ocrservice;

import com.example.bboo_technology.DTO.OcrGptResultDto;
import com.example.bboo_technology.Entiry.OcrGptResult;
import com.example.bboo_technology.Entiry.OcrResult;
import com.example.bboo_technology.Repository.OcrGptResultRepository;
import com.example.bboo_technology.Repository.OcrResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * OcrGptResultService 구현체.
 *
 * - ModelMapper 를 활용한 Entity <-> DTO 변환
 * - 공통 매핑/정렬 로직은 private 메서드로 분리하여 재사용
 * - 예외 발생 시 로그 + 의미 있는 메시지로 감싸서 던지는 패턴 유지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrGptResultServiceImpl implements OcrGptResultService {

    private final OcrGptResultRepository ocrGptResultRepository;
    private final OcrResultRepository ocrResultRepository;
    private final ModelMapper modelMapper;  // 공용 ModelMapper Bean

    // ====================================================
    // public 메서드
    // ====================================================

    /**
     * GPT 결과 저장
     *
     * - dto.ocrResultId 로 원본 OcrResult 엔티티를 조회하여 FK 설정
     * - DTO -> 엔티티로 매핑 후 save
     * - 저장된 엔티티 -> DTO 로 다시 변환하여 반환
     */
    @Override
    @Transactional
    public OcrGptResultDto saveResult(OcrGptResultDto dto) {

        try {
            validateOcrResultId(dto);

            OcrResult ocrResult = loadOcrResult(dto.getOcrResultId());

            OcrGptResult entity = mapDtoToEntity(dto);
            entity.setOcrResult(ocrResult);

            OcrGptResult saved = ocrGptResultRepository.save(entity);

            log.info("GPT 결과 저장 완료 - id={}, ocrResultId={}, type={}",
                    saved.getId(),
                    saved.getOcrResult() != null ? saved.getOcrResult().getId() : null,
                    saved.getResultType());

            return toDto(saved);

        } catch (IllegalArgumentException e) {
            log.warn("GPT 결과 저장 실패(입력값 오류): {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("GPT 결과 저장 중 예외 발생", e);
            throw new RuntimeException("GPT 결과를 저장하는 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 특정 OCR 결과에 대한 GPT 결과 전체 조회 (최신순)
     */
    @Override
    @Transactional(readOnly = true)
    public List<OcrGptResultDto> findByOcrResultId(Long ocrResultId) {

        try {
            List<OcrGptResult> entities =
                    ocrGptResultRepository.findByOcrResult_IdOrderByCreatedAtDesc(ocrResultId);

            List<OcrGptResultDto> dtos = mapEntitiesToDtos(entities);
            // 리포지토리에서 이미 최신순으로 가져오지만,
            // 일관성을 위해 정렬 메서드를 재사용해도 됨 (원하면 주석 처리 가능)
            sortDtosByCreatedAtDesc(dtos);

            return dtos;

        } catch (Exception e) {
            log.error("GPT 결과 목록 조회 중 예외 발생 - ocrResultId={}", ocrResultId, e);
            throw new RuntimeException("GPT 결과 목록을 조회하는 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * GPT : ocr_gpt_result 테이블에 생성된 결과 전체 조회 (최신순)
     */
    @Override
    @Transactional(readOnly = true)
    public List<OcrGptResultDto> findAllOrderByCreatedAtDesc() {

        try {
            List<OcrGptResult> entities =
                    ocrGptResultRepository.findAllByOrderByCreatedAtDesc();

            List<OcrGptResultDto> dtos = mapEntitiesToDtos(entities);
            sortDtosByCreatedAtDesc(dtos);

            return dtos;

        } catch (Exception e) {
            log.error("GPT 결과 전체 히스토리 조회 중 예외 발생", e);
            throw new RuntimeException("GPT 결과 히스토리를 조회하는 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 체크박스로 선택된 GPT 추론 결과들만 조회 (엑셀 선택 다운로드용)
     */
    @Override
    @Transactional(readOnly = true)
    public List<OcrGptResultDto> findByIds(List<Long> ids) {

        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        try {
            List<OcrGptResult> entities = ocrGptResultRepository.findAllById(ids);

            List<OcrGptResultDto> dtos = mapEntitiesToDtos(entities);
            sortDtosByCreatedAtDesc(dtos);

            return dtos;

        } catch (Exception e) {
            log.error("선택된 GPT 결과 조회 중 예외 발생 - ids={}", ids, e);
            throw new RuntimeException("선택된 GPT 결과를 조회하는 중 오류가 발생했습니다.", e);
        }
    }

    // ====================================================
    // private helper 메서드
    // ====================================================

    /**
     * OCR 결과 ID 필수 값 검증
     */
    private void validateOcrResultId(OcrGptResultDto dto) {
        if (dto == null || dto.getOcrResultId() == null) {
            throw new IllegalArgumentException("OCR 결과 ID(ocrResultId)는 필수입니다.");
        }
    }

    /**
     * OCR 결과 엔티티 조회 (없으면 IllegalArgumentException)
     */
    private OcrResult loadOcrResult(Long ocrResultId) {
        return ocrResultRepository.findById(ocrResultId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "해당 ID의 OCR 결과를 찾을 수 없습니다. id=" + ocrResultId
                ));
    }

    /**
     * DTO -> 엔티티 기본 매핑 (연관관계는 별도 세팅)
     */
    private OcrGptResult mapDtoToEntity(OcrGptResultDto dto) {
        return modelMapper.map(dto, OcrGptResult.class);
    }

    /**
     * 엔티티 리스트 -> DTO 리스트 공통 변환
     */
    private List<OcrGptResultDto> mapEntitiesToDtos(List<OcrGptResult> entities) {
        List<OcrGptResultDto> dtos = new ArrayList<>();

        if (entities == null || entities.isEmpty()) {
            return dtos;
        }

        for (OcrGptResult entity : entities) {
            OcrGptResultDto dto = toDto(entity);
            if (dto != null) {
                dtos.add(dto);
            }
        }

        return dtos;
    }

    /**
     * createdAt 기준 최신순(내림차순) 정렬
     */
    private void sortDtosByCreatedAtDesc(List<OcrGptResultDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return;
        }

        dtos.sort(Comparator.comparing(
                OcrGptResultDto::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
    }

    /**
     * 공통: OcrGptResult 엔티티 → OcrGptResultDto 변환
     * - ModelMapper 기본 매핑 + OcrResult 연관 정보(제목/파일명) 수동 세팅
     */
    private OcrGptResultDto toDto(OcrGptResult entity) {
        if (entity == null) {
            return null;
        }

        OcrGptResultDto dto = modelMapper.map(entity, OcrGptResultDto.class);

        if (entity.getOcrResult() != null) {
            dto.setOcrResultId(entity.getOcrResult().getId());
            dto.setOcrTitle(entity.getOcrResult().getTitle());
            dto.setOcrFileName(entity.getOcrResult().getOriginalFileName());
        }

        return dto;
    }
}
