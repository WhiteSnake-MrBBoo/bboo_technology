package com.example.bboo_technology.DTO.Stt;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatus;

// (추가) 웹/API 응답용 래퍼 DTO
@Getter
@Builder
public class SttWebResponse {

    private final SttResponseDto body;   // 실제 응답 데이터
    private final HttpStatus httpStatus; // HTTP 상태 코드

}
