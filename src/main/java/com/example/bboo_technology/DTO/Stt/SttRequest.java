package com.example.bboo_technology.DTO.Stt;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

// (추가) STT 엔진에 전달할 요청 DTO
@Getter
@Builder
public class SttRequest {

    // 방송/세션 구분용 (없으면 null 가능)
    private final String sessionId;

    // 언어 힌트 (null이면 auto-detect)
    private final String languageHint;

    // 원본 파일 이름
    private final String fileName;

    // 파일 크기(bytes)
    private final Long fileSize;

    // 실제 오디오 데이터
    private final byte[] audioData;

    // 추가 메타 정보 (옵션)
    private final Map<String, Object> meta;
}
