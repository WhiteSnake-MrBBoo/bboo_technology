package com.example.bboo_technology.Service.Sttservice.engine;

import com.example.bboo_technology.Config.PythonSttProperties;
import com.example.bboo_technology.Service.Sttservice.SttEngine;
import com.example.bboo_technology.DTO.Stt.SttRequest;
import com.example.bboo_technology.DTO.Stt.SttResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;

import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.core.io.ByteArrayResource;

import org.springframework.http.client.MultipartBodyBuilder;

import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.util.StringUtils.hasText;

/**
 * (추가) 로컬 Python Faster-Whisper STT 엔진 구현체
 *
 *  - Python FastAPI 서버의 /api/stt/file 엔드포인트를 호출
 *  - request: multipart/form-data (file, language, sessionId)
 *  - response: JSON (Python SttResponse) → SttResult 로 변환
 *
 *  TODO:
 *   - 추후 provider 전략 (@Qualifier or 설정 값)에 따라
 *     OpenAiWhisperSttEngine vs LocalPythonSttEngine 중 어떤 것을 사용할지 스위칭
 */
@Slf4j
@Service
//@Primary // 테스트후 주석 풀기
@RequiredArgsConstructor
public class LocalPythonSttEngine implements SttEngine {

    private final PythonSttProperties pythonSttProperties;

    // =========================
    // SttEngine 인터페이스 구현
    // =========================

    @Override
    public SttResult transcribe(SttRequest request) {

        logRequestSummary(request);

        // 1. 기본 유효성 검사
        if (!isValidRequest(request)) {
            SttResult invalid = buildInvalidRequestResult(request);
            logResultSummary(invalid);
            return invalid;
        }

        // 2. enabled 플래그 체크 (필요 시 확장)
        if (!pythonSttProperties.isEnabled()) {
            SttResult disabled = buildDisabledResult(request);
            logResultSummary(disabled);
            return disabled;
        }

        SttResult finalResult;

        try {
            // 3. Python STT 서버 호출
            PythonSttResponse response = callPythonSttServer(request);

            // 4. 응답 매핑
            finalResult = mapPythonResponseToResult(request, response);

        } catch (WebClientResponseException e) {
            finalResult = buildHttpErrorResult(request, e);
        } catch (Exception e) {
            finalResult = buildUnknownErrorResult(request, e);
        }

        logResultSummary(finalResult);
        return finalResult;
    }

    // =========================
    // Python 서버 호출 로직
    // =========================

    /**
     * (추가) Python FastAPI STT 서버로 multipart/form-data 요청을 보내는 부분.
     *
     *  - URL : {baseUrl}{path}  (예: http://localhost:5000/api/stt/file)
     *  - Form:
     *      - file      : 바이너리 오디오
     *      - language  : 언어 힌트 (옵션)
     *      - sessionId : 세션 ID (옵션)
     */
    private PythonSttResponse callPythonSttServer(SttRequest request) {

        WebClient webClient = buildWebClient();

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        // (1) 파일 파트 구성
        ByteArrayResource fileResource = new ByteArrayResource(request.getAudioData()) {
            @Override
            public String getFilename() {
                return resolveFileName(request);
            }
        };

        bodyBuilder
                .part("file", fileResource)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        // (2) language 힌트 (있을 때만)
        if (hasText(request.getLanguageHint())) {
            bodyBuilder.part("language", request.getLanguageHint());
        }

        // (3) sessionId (있을 때만)
        if (hasText(request.getSessionId())) {
            bodyBuilder.part("sessionId", request.getSessionId());
        }

        MultiValueMap<String, HttpEntity<?>> multipartData = bodyBuilder.build();

        // (4) WebClient 요청 (동기 block)
        String path = pythonSttProperties.getPath();
        Duration timeout = resolveTimeout();

        PythonSttResponse response = webClient.post()
                .uri(path)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartData))
                .retrieve()
                .bodyToMono(PythonSttResponse.class)
                .block(timeout);

        if (response == null) {
            throw new IllegalStateException("Python STT 서버 응답이 null 입니다.");
        }

        return response;
    }

    /**
     * (추가) Python STT 서버 호출용 WebClient 생성
     *
     *  TODO:
     *   - 공통 WebClient 설정을 별도 Config로 분리해서 재사용하도록 개선 가능
     */
    private WebClient buildWebClient() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(resolveTimeout());

        return WebClient.builder()
                .baseUrl(pythonSttProperties.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    private Duration resolveTimeout() {
        Integer readTimeoutMs = pythonSttProperties.getReadTimeoutMs();
        if (readTimeoutMs == null || readTimeoutMs <= 0) {
            return Duration.ofSeconds(60);
        }
        return Duration.ofMillis(readTimeoutMs);
    }

    // =========================
    // 결과 매핑/빌드 로직
    // =========================

    private SttResult mapPythonResponseToResult(SttRequest request, PythonSttResponse response) {

        // Python 서버에서 success=false 를 리턴한 경우 그대로 에러로 매핑
        if (response == null) {
            return SttResult.builder()
                    .sessionId(request.getSessionId())
                    .success(false)
                    .engineName(resolveEngineName())
                    .errorCode("PYTHON_STT_NULL_RESPONSE")
                    .errorMessage("Python STT 서버 응답이 null 입니다.")
                    .createdAt(Instant.now())
                    .build();
        }

        if (!response.isSuccess()) {
            return SttResult.builder()
                    .sessionId(response.getSessionId())
                    .success(false)
                    .engineName(response.getEngineName() != null ? response.getEngineName() : resolveEngineName())
                    .transcript(response.getTranscript())
                    .language(response.getLanguage())
                    .errorCode(response.getErrorCode())
                    .errorMessage(response.getErrorMessage())
                    .createdAt(Instant.now())
                    .build();
        }

        // 성공 케이스
        return SttResult.builder()
                .sessionId(response.getSessionId())
                .success(true)
                .transcript(response.getTranscript())
                .language(response.getLanguage())
                .durationSeconds(null) // TODO: Python 쪽에서 total_time 전달 받으면 매핑
                .engineName(response.getEngineName() != null ? response.getEngineName() : resolveEngineName())
                .errorCode(null)
                .errorMessage(null)
                .createdAt(Instant.now())
                .build();
    }

    private SttResult buildInvalidRequestResult(SttRequest request) {
        String sessionId = (request != null) ? request.getSessionId() : null;

        // 최종 객체를 생성하고 변수에 할당합니다.
        SttResult result = SttResult.builder()
                .sessionId(sessionId)
                .success(false)
                .engineName(resolveEngineName())
                .errorCode("INVALID_REQUEST")
                .errorMessage("STT 요청이 올바르지 않습니다. (오디오 데이터가 비어 있음)")
                .createdAt(Instant.now())
                .build(); // 여기서 객체 생성을 완료합니다.

        return result; // 최종 객체를 반환합니다.
    }

    private SttResult buildDisabledResult(SttRequest request) {
        String sessionId = (request != null) ? request.getSessionId() : null;
        return SttResult.builder()
                .sessionId(sessionId)
                .success(false)
                .engineName(resolveEngineName())
                .errorCode("PYTHON_STT_DISABLED")
                .errorMessage("로컬 Python STT 엔진이 비활성화되어 있습니다.")
                .createdAt(Instant.now())
                .build();
    }

    private SttResult buildHttpErrorResult(SttRequest request, WebClientResponseException e) {
        String sessionId = (request != null) ? request.getSessionId() : null;
        String responseBody = e.getResponseBodyAsString();

        log.error("[STT-PYTHON] HTTP 오류 발생 - status={}, body={}",
                e.getStatusCode(), responseBody);

        String errorCode = "PYTHON_HTTP_" + e.getStatusCode().value();

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("Python STT 서버 호출 중 HTTP 오류가 발생했습니다. (status=")
                .append(e.getStatusCode().value())
                .append(")");
        if (responseBody != null && !responseBody.isBlank()) {
            messageBuilder.append(" / details=").append(responseBody);
        }

        return SttResult.builder()
                .sessionId(sessionId)
                .success(false)
                .engineName(resolveEngineName())
                .errorCode(errorCode)
                .errorMessage(messageBuilder.toString())
                .createdAt(Instant.now())
                .build();
    }

    private SttResult buildUnknownErrorResult(SttRequest request, Exception e) {
        String sessionId = (request != null) ? request.getSessionId() : null;
        log.error("[STT-PYTHON] 알 수 없는 오류 발생", e);

        return SttResult.builder()
                .sessionId(sessionId)
                .success(false)
                .engineName(resolveEngineName())
                .errorCode("PYTHON_STT_UNKNOWN_ERROR")
                .errorMessage("Python STT 서버 호출 중 알 수 없는 오류가 발생했습니다.")
                .createdAt(Instant.now())
                .build();
    }

    // =========================
    // 유틸 / 로깅
    // =========================

    private boolean isValidRequest(SttRequest request) {
        if (request == null) return false;
        byte[] data = request.getAudioData();
        return (data != null && data.length > 0);
    }

    private String resolveEngineName() {
        return "local-python-faster-whisper";
    }

    private String resolveFileName(SttRequest request) {
        if (request == null) return "audio.wav";
        if (hasText(request.getFileName())) return request.getFileName();
        return "audio.wav";
    }

    private void logRequestSummary(SttRequest request) {
        if (request == null) {
            log.warn("[STT-PYTHON] 요청 DTO가 null 입니다.");
            return;
        }
        log.info("[STT-PYTHON] transcribe 호출 - sessionId={}, fileName={}, fileSize={}, languageHint={}",
                request.getSessionId(),
                request.getFileName(),
                request.getFileSize(),
                request.getLanguageHint());
    }

    private void logResultSummary(SttResult result) {
        if (result == null) {
            log.warn("[STT-PYTHON] 결과 DTO가 null 입니다.");
            return;
        }
        log.info("[STT-PYTHON] 결과 - sessionId={}, success={}, engine={}, errorCode={}",
                result.getSessionId(),
                result.isSuccess(),
                result.getEngineName(),
                result.getErrorCode());
    }

    // =========================
    // Python 응답용 내부 DTO
    // =========================

    /**
     * (내부) Python FastAPI 서버의 STT 응답 JSON 매핑용 DTO
     *
     * Python 쪽 SttResponse(BaseModel) 구조:
     *  - success: bool
     *  - sessionId: str
     *  - transcript: Optional[str]
     *  - language: Optional[str]
     *  - engineName: str
     *  - errorCode: Optional[str]
     *  - errorMessage: Optional[str]
     */
    @Getter
    @Setter
    public static class PythonSttResponse {
        private boolean success;
        private String sessionId;
        private String transcript;
        private String language;
        private String engineName;
        private String errorCode;
        private String errorMessage;
    }
}
