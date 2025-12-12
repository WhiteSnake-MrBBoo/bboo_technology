package com.example.bboo_technology.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

// (추가) openai.stt.* 설정 바인딩용
// @Value("{openai.stt.provider}")
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "openai.stt")
public class OpenAiSttProperties {

    /**
     * openai / local 등 STT 제공자 타입
     */
    private String provider;

    /**
     * STT API base URL (OpenAI 또는 Python STT 서버)
     */
    private String baseUrl;

    /**
     * Whisper 모델명 (예: whisper-1)
     */
    private String model;

    /**
     * STT 요청 타임아웃(ms)
     */
    private Integer timeoutMs;
}
