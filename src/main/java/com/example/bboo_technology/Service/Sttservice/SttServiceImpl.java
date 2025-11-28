package com.example.bboo_technology.Service.Sttservice;

import com.example.bboo_technology.DTO.Stt.SttRequest;
import com.example.bboo_technology.DTO.Stt.SttResponseDto;
import com.example.bboo_technology.DTO.Stt.SttResult;
import com.example.bboo_technology.DTO.Stt.SttWebResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

// (추가) STT 서비스 구현체
@Slf4j
@Service
@RequiredArgsConstructor
public class SttServiceImpl implements SttService {

    // (추가) 실제 STT 엔진 구현체(OpenAiWhisperSttEngine / LocalPythonSttEngine)가 주입됨
    private final SttEngine sttEngine;

    @Override
    public SttResult transcribeFile(MultipartFile file, String sessionId, String languageHint) {

        // =============================
        // 1. 업로드 파일 기본 검증
        // =============================
        if (isEmptyFile(file)) {
            log.warn("[STT] 업로드 파일이 비어 있습니다.");
            return buildErrorResult(
                    null,
                    "EMPTY_FILE",
                    "업로드된 파일이 없습니다."
            );
        }

        // =============================
        // 2. 세션 ID 정규화 (없으면 신규 생성)
        // =============================
        String effectiveSessionId = normalizeSessionId(sessionId);

        // =============================
        // 3. MultipartFile → byte[] 변환
        // =============================
        byte[] audioBytes;
        try {
            audioBytes = file.getBytes();
        } catch (IOException e) {
            log.error("[STT] 파일 읽기 실패", e);
            return buildErrorResult(
                    effectiveSessionId,
                    "IO_ERROR",
                    "오디오 파일을 읽는 중 오류가 발생했습니다."
            );
        }

        // =============================
        // 4. STT 요청 DTO(SttRequest) 생성
        // =============================
        SttRequest request = SttRequest.builder()
                .sessionId(effectiveSessionId)
                .languageHint(languageHint)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .audioData(audioBytes)
                .meta(null) // (추가) 추후 방송 채널명, 사용자 정보 등 확장 가능
                .build();

        // =============================
        // 5. STT 엔진 호출
        // =============================
        SttResult result = sttEngine.transcribe(request);

        // =============================
        // 6. 결과 로깅 및 후처리(히스토리 저장 등은 추후 확장)
        // =============================
        log.info("[STT] sessionId={}, success={}, engine={}",
                result.getSessionId(), result.isSuccess(), result.getEngineName());

        // (추가) 여기서 필요 시 DB 히스토리 저장 로직 연동 가능

        return result;
    }

    // =============================
    // [Private Helper Methods]
    //  - 재사용/가독성을 위한 공통 로직 분리
    // =============================

    // (추가) 업로드 파일 비어있는지 체크
    private boolean isEmptyFile(MultipartFile file) {
        return (file == null || file.isEmpty());
    }

    // (추가) 세션 ID가 없으면 UUID 기반으로 신규 생성
    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return sessionId;
    }

    // (추가) 공통 에러 응답 생성용 메서드
    private SttResult buildErrorResult(String sessionId, String errorCode, String errorMessage) {
        return SttResult.builder()
                .success(false)
                .sessionId(sessionId)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .createdAt(Instant.now())
                .build();
    }

    @Override
    public SttWebResponse transcribeFileForWeb(MultipartFile file, String sessionId, String languageHint) {

        // 1) 기존 도메인 로직 그대로 사용
        SttResult result = transcribeFile(file, sessionId, languageHint);

        // 2) 결과 → 사용자 메시지
        String message = buildUserMessage(result);

        // 3) 도메인 결과 + 메시지 → 응답 DTO
        SttResponseDto body = SttResponseDto.builder()
                .success(result.isSuccess())
                .message(message)
                .sessionId(result.getSessionId())
                .transcript(result.getTranscript())
                .language(result.getLanguage())
                .engineName(result.getEngineName())
                .errorMessage(result.getErrorMessage()) // 디버깅용
                .build();

        // 4) HTTP 상태 코드 매핑
        HttpStatus status = resolveHttpStatus(result);

        // 5) 래퍼로 묶어서 반환
        return SttWebResponse.builder()
                .body(body)
                .httpStatus(status)
                .build();
    }

// =============================
// 아래는 기존에 Controller 에 있던 로직을
// ServiceImpl 으로 옮겨온 것
// =============================

    private String buildUserMessage(SttResult result) {
        if (result == null) {
            return "STT 처리 결과가 존재하지 않습니다.";
        }
        if (result.isSuccess()) {
            return "음성 인식이 성공적으로 완료되었습니다.";
        }
        if ("NOT_IMPLEMENTED".equals(result.getErrorCode())) {
            return "현재 STT 엔진은 Stub 상태입니다. OpenAI Whisper 연동이 아직 완료되지 않았습니다.";
        }
        // TODO: EMPTY_FILE, IO_ERROR, ENGINE_TIMEOUT 등 에러 코드별 메시지 세분화
        return "음성 인식 처리 중 오류가 발생했습니다. (code=" + result.getErrorCode() + ")";
    }

    private HttpStatus resolveHttpStatus(SttResult result) {
        if (result == null) {
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
        if (result.isSuccess()) {
            return HttpStatus.OK;
        }
        if ("EMPTY_FILE".equals(result.getErrorCode())) {
            return HttpStatus.BAD_REQUEST;
        }
        if ("NOT_IMPLEMENTED".equals(result.getErrorCode())) {
            return HttpStatus.NOT_IMPLEMENTED;
        }
        // TODO: ENGINE_TIMEOUT, AUTH_ERROR 등 추가 분기
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }



}
