package com.example.bboo_technology.Config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * (추가) 로컬 Python STT 서버 설정 값 바인딩
 *
 * - prefix: stt.python
 *   - base-url           : 파이썬) http://localhost:5000
 *   - path               : 파이썬) /api/stt/file
 *   - connect-timeout-ms : 연결 타임아웃(ms)
 *   - read-timeout-ms    : 읽기 타임아웃(ms)
 *   - enabled            : 사용 여부 플래그
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "stt.python")
public class PythonSttProperties {

    /**
     * 로컬 STT 서버 Base URL
     * 예) http://localhost:5000
     */
    private String baseUrl;

    /**
     * 파일 업로드 STT 엔드포인트 경로
     * 예) /api/stt/file
     */
    private String path = "/api/stt/file";

    /**
     * 연결 타임아웃 (ms)
     */
    private Integer connectTimeoutMs = 3000;

    /**
     * 읽기 타임아웃 (ms)
     */
    private Integer readTimeoutMs = 60000;

    /**
     * 로컬 Python STT 사용 여부
     *  - false 로 두면 엔진에서 곧바로 NOT_ENABLED 에러를 반환하도록 확장 가능
     */
    private boolean enabled = true;
}
