package com.example.bboo_technology.DTO.Stt;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

// (추가) STT 엔진 처리 결과 DTO
@Getter
@Builder
public class SttResult {

    private final String sessionId;

    // 변환된 텍스트
    private final String transcript;

    // 인식된 언어 코드 (예: "ko", "en")
    private final String language;

    // 오디오 길이(초) - 알 수 없으면 null
    private final Double durationSeconds;

    // 사용된 엔진 이름 (openai-whisper, local-whisper 등)
    private final String engineName;

    // 성공 여부
    private final boolean success;

    // 실패 시 에러 코드/메시지
    private final String errorCode;
    private final String errorMessage;

    // 결과 생성 시각
    private final Instant createdAt;
}
