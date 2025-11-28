package com.example.bboo_technology.DTO.Stt;


import lombok.Builder;
import lombok.Getter;

// (추가) 컨트롤러에서 JSON 응답으로 내려줄 DTO
// (추가) STT 결과를 클라이언트(REST/뷰)에 내려줄 응답 DTO
@Getter
@Builder
public class SttResponseDto {

    // 전체 처리 성공 여부 (비즈니스 관점)
    private final boolean success;

    // 사용자에게 보여줄 메시지 (성공/실패 사유)
    private final String message;

    // 세션/방송 식별자
    private final String sessionId;

    // 변환된 텍스트
    private final String transcript;

    // 인식된 언어 코드 (예: "ko", "en")
    private final String language;

    // 사용된 STT 엔진 이름 (예: openai-whisper-1, local-whisper 등)
    private final String engineName;

    // (디버깅용) 내부 에러 메시지를 보고 싶을 때 사용
    private final String errorMessage;

    // (TODO) 필요 시 errorCode / errorMessage / durationSeconds 등 확장 가능  private final String engineName;
}
