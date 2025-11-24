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

import java.util.List;

/**
 * OcrGptResultService 구현체.
 *
 * - ModelMapper 를 최대한 활용하여 Entity <-> DTO 변환
 * - 예외 발생 시 로그 + 의미 있는 메시지로 감싸서 던지는 패턴 유지
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrGptResultServiceImpl implements OcrGptResultService {

    private final OcrGptResultRepository ocrGptResultRepository;
    private final OcrResultRepository ocrResultRepository;
    private final ModelMapper modelMapper;  // 프로젝트 전체에서 사용 중인 ModelMapper Bean 주입

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
            if (dto.getOcrResultId() == null) {
                throw new IllegalArgumentException("OCR 결과 ID(ocrResultId)는 필수입니다.");
            }

            // 1) ocrResultId 기준으로 OcrResult 엔티티 조회
            OcrResult ocrResult = ocrResultRepository.findById(dto.getOcrResultId())
                    .orElseThrow(() -> new IllegalArgumentException("해당 ID의 OCR 결과를 찾을 수 없습니다. id=" + dto.getOcrResultId()));

            // 2) DTO -> 엔티티 매핑
            OcrGptResult entity = modelMapper.map(dto, OcrGptResult.class);

            // 3) 연관 관계(OcrResult) 수동으로 세팅
            entity.setOcrResult(ocrResult);

            // 4) JPA 저장
            OcrGptResult saved = ocrGptResultRepository.save(entity);

            log.info("GPT 결과 저장 완료 - id={}, ocrResultId={}, type={}",
                    saved.getId(),
                    saved.getOcrResult().getId(),
                    saved.getResultType());

            // 5) 저장된 엔티티 -> DTO 변환 (ocrResultId는 수동 세팅)
            OcrGptResultDto resultDto = modelMapper.map(saved, OcrGptResultDto.class);
            resultDto.setOcrResultId(saved.getOcrResult().getId());

            return resultDto;

        } catch (IllegalArgumentException e) {
            // 입력값 오류 등
            log.warn("GPT 결과 저장 실패(입력값 오류): {}", e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("GPT 결과 저장 중 예외 발생", e);
            // 프로젝트에서 공통 예외 클래스를 사용 중이면 거기에 맞춰 변경 가능
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

            return entities.stream()
                    .map(entity -> {
                        OcrGptResultDto dto = modelMapper.map(entity, OcrGptResultDto.class);
                        dto.setOcrResultId(entity.getOcrResult().getId());
                        return dto;
                    })
                    .toList();

        } catch (Exception e) {
            log.error("GPT 결과 목록 조회 중 예외 발생 - ocrResultId={}", ocrResultId, e);

            //기존에 OcrProcessingException 같은 공통 예외 클래스 만들어 놓은거로 언제든 교체 가능 예외 처리
            throw new RuntimeException("GPT 결과 목록을 조회하는 중 오류가 발생했습니다.", e);
        }
    }
}
