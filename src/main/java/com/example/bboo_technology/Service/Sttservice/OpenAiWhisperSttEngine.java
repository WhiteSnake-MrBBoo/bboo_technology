package com.example.bboo_technology.Service.Sttservice;

import com.example.bboo_technology.Config.OpenAiSttProperties;
import com.example.bboo_technology.DTO.Stt.SttRequest;
import com.example.bboo_technology.DTO.Stt.SttResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;

// (변경) OpenAI Whisper 기반 STT 엔진 실제 구현체
//  - 기존 Stub 버전에서 OpenAI /audio/transcriptions 호출 로직으로 교체
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiWhisperSttEngine implements SttEngine {

    // (주입) STT 관련 설정 (model, baseUrl, timeout 등)
    private final OpenAiSttProperties sttProperties;

    // (주입) OpenAI API 키 (기존 GPT 설정과 동일한 키 사용)
    @Value("${openai.api.key}")
    private String openAiApiKey;

    // =============================
    // 1. Public API
    // =============================
    @Override
    public SttResult transcribe(SttRequest request) {

        // 1-1. 요청 기본 로그
        logRequestSummary(request);

        // 1-2. 요청 유효성 검증
        if (!isValidRequest(request)) {
            SttResult invalidResult = buildInvalidRequestResult(request);
            logResultSummary(invalidResult);
            return invalidResult;
        }

        // 1-3. OpenAI Whisper API 호출 + 응답 매핑
        SttResult finalResult;
        try {
            WhisperResponse whisperResponse = callOpenAiWhisper(request);
            finalResult = buildSuccessResult(request, whisperResponse);
        } catch (WebClientResponseException e) {
            // HTTP 통신은 되었으나, 4xx/5xx 응답인 경우
            finalResult = buildHttpErrorResult(request, e);
        } catch (Exception e) {
            // 네트워크 장애, 타임아웃, 기타 예외
            finalResult = buildUnknownErrorResult(request, e);
        }

        // 1-4. 결과 로그
        logResultSummary(finalResult);

        // (주의) return 안에서 빌더/로직 X
        return finalResult;
    }

    // =============================
    // 2. OpenAI Whisper 실제 호출 로직
    // =============================

    /**
     * OpenAI Whisper /audio/transcriptions 호출
     *
     *  - POST {baseUrl}/audio/transcriptions
     *  - Content-Type: multipart/form-data
     *  - 필수 필드:
     *      - file   : 오디오 바이너리
     *      - model  : whisper-1 (또는 yml 설정 값)
     *  - 선택 필드:
     *      - language        : 언어 힌트 (예: ko, en)
     *      - response_format : json (기본값)
     *
     * TODO:
     *  - temperature, prompt, timestamp_granularities 등 옵션 필요 시 추가
     */
    private WhisperResponse callOpenAiWhisper(SttRequest request) {

        WebClient webClient = buildWebClient();

        // =============================
        // 2-1. MultipartBodyBuilder 구성
        // =============================
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        // (중요) 파일 파트: ByteArrayResource + 파일명 설정
        ByteArrayResource fileResource = new ByteArrayResource(request.getAudioData()) {
            @Override
            public String getFilename() {
                return resolveFileName(request);
            }
        };

        bodyBuilder
                .part("file", fileResource)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);

        // 모델명
        bodyBuilder.part("model", sttProperties.getModel());

        // 언어 힌트 (있을 때만)
        if (hasText(request.getLanguageHint())) {
            bodyBuilder.part("language", request.getLanguageHint());
        }

        // 응답 포맷 (json 강제)
        bodyBuilder.part("response_format", "json");

        // (TODO) 필요 시 temperature, prompt 등 파라미터 추가

        // =============================
        // 2-2. Multipart 데이터 생성
        // =============================
        MultiValueMap<String, HttpEntity<?>> multipartData = bodyBuilder.build();

        // =============================
        // 2-3. WebClient 호출 (동기 block)
        // =============================
        WhisperResponse response = webClient.post()
                .uri("/audio/transcriptions")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipartData))
                .retrieve()
                .bodyToMono(WhisperResponse.class)
                .block(resolveTimeout());

        if (response == null) {
            throw new IllegalStateException("OpenAI Whisper 응답이 null 입니다.");
        }

        return response;
    }

    // =============================
    // 3. WebClient / Timeout 설정
    // =============================

    // (추가) STT 전용 WebClient 생성
    //  - baseUrl: openai.stt.base-url
    //  - Authorization: Bearer {OPENAI_API_KEY}
    private WebClient buildWebClient() {
        return WebClient.builder()
                .baseUrl(sttProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // (추가) 타임아웃 Duration 계산
    private Duration resolveTimeout() {
        Integer timeoutMs = sttProperties.getTimeoutMs();
        if (timeoutMs == null || timeoutMs <= 0) {
            // 기본 60초
            return Duration.ofSeconds(60);
        }
        return Duration.ofMillis(timeoutMs);
    }

    // =============================
    // 4. Result 빌더들
    // =============================

    // (성공 케이스) WhisperResponse → SttResult 매핑
    private SttResult buildSuccessResult(SttRequest request, WhisperResponse whisperResponse) {

        String sessionId = request.getSessionId();
        String language = resolveResultLanguage(request, whisperResponse);

        // TODO:
        //  - whisperResponse 에 duration, language 등 추가 정보가 생기면 여기 매핑
        //  - 현재 OpenAI /audio/transcriptions 기본 응답은 text 필드 중심

        SttResult result = SttResult.builder()
                .sessionId(sessionId)
                .transcript(whisperResponse.getText())
                .language(language)
                .durationSeconds(null)              // 필요 시 나중에 채우기
                .engineName(resolveEngineName())
                .success(true)
                .errorCode(null)
                .errorMessage(null)
                .createdAt(Instant.now())
                .build();

        return result;
    }

    // (요청 에러) 요청 자체가 잘못된 경우 (null, 파일 없음 등)
    private SttResult buildInvalidRequestResult(SttRequest request) {

        String sessionId = (request != null) ? request.getSessionId() : null;

        SttResult result = SttResult.builder()
                .sessionId(sessionId)
                .transcript(null)
                .language(null)
                .durationSeconds(null)
                .engineName(resolveEngineName())
                .success(false)
                .errorCode("INVALID_REQUEST")
                .errorMessage("STT 요청이 올바르지 않습니다. (오디오 데이터가 비어 있음)")
                .createdAt(Instant.now())
                .build();

        return result;
    }

    // (HTTP 에러) OpenAI 쪽에서 4xx/5xx 응답을 준 경우
    private SttResult buildHttpErrorResult(SttRequest request, WebClientResponseException e) {

        String sessionId = (request != null) ? request.getSessionId() : null;
        String responseBody = e.getResponseBodyAsString();

        log.error("[STT-OPENAI] HTTP 오류 발생 - status={}, body={}",
                e.getStatusCode(), responseBody);

        String errorCode = "OPENAI_HTTP_" + e.getStatusCode().value();

        // (디버깅/로그용) 상세 메시지 구성
        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append("OpenAI Whisper 호출 중 HTTP 오류가 발생했습니다. (status=")
                .append(e.getStatusCode().value())
                .append(")");

        if (responseBody != null && !responseBody.isBlank()) {
            messageBuilder.append(" / details=").append(responseBody);
        }

        String errorMessage = messageBuilder.toString();

        SttResult result = SttResult.builder()
                .sessionId(sessionId)
                .transcript(null)
                .language(null)
                .durationSeconds(null)
                .engineName(resolveEngineName())
                .success(false)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .createdAt(Instant.now())
                .build();

        return result;
    }

    // (알 수 없는 에러) 네트워크 문제, 타임아웃, 기타 예외
    private SttResult buildUnknownErrorResult(SttRequest request, Exception e) {

        String sessionId = (request != null) ? request.getSessionId() : null;

        log.error("[STT-OPENAI] 알 수 없는 오류 발생", e);

        SttResult result = SttResult.builder()
                .sessionId(sessionId)
                .transcript(null)
                .language(null)
                .durationSeconds(null)
                .engineName(resolveEngineName())
                .success(false)
                .errorCode("OPENAI_UNKNOWN_ERROR")
                .errorMessage("OpenAI Whisper 호출 중 알 수 없는 오류가 발생했습니다.")
                .createdAt(Instant.now())
                .build();

        return result;
    }

    // =============================
    // 5. 로그 / 유틸 / 보조 메서드
    // =============================

    // (요청 로그)
    private void logRequestSummary(SttRequest request) {
        if (request == null) {
            log.warn("[STT-OPENAI] 요청 DTO가 null 입니다.");
            return;
        }

        log.info(
                "[STT-OPENAI] transcribe 호출 - sessionId={}, fileName={}, fileSize={}, languageHint={}, model={}",
                request.getSessionId(),
                request.getFileName(),
                request.getFileSize(),
                request.getLanguageHint(),
                sttProperties.getModel()
        );
    }

    // (결과 로그)
    private void logResultSummary(SttResult result) {
        if (result == null) {
            log.warn("[STT-OPENAI] 결과 DTO가 null 입니다.");
            return;
        }

        log.info(
                "[STT-OPENAI] 결과 - sessionId={}, success={}, engine={}, errorCode={}, errorMessage={}",
                result.getSessionId(),
                result.isSuccess(),
                result.getEngineName(),
                result.getErrorCode(),
                result.getErrorMessage()
        );
    }

    // (요청 유효성 검증) 오디오 데이터 존재 여부만 우선 체크
    private boolean isValidRequest(SttRequest request) {
        if (request == null) {
            return false;
        }
        byte[] data = request.getAudioData();
        return (data != null && data.length > 0);
    }

    // (엔진 이름 생성 로직 분리)
    private String resolveEngineName() {
        // TODO: provider/model 조합에 따라 엔진 이름 동적으로 구성해도 됨.
        //  예) openai-whisper-1, local-whisper-large-v2 등
        return "openai-whisper-" + sttProperties.getModel();
    }

    // (결과 언어 결정)
    //  - 현재는 요청의 languageHint 를 그대로 사용
    //  - 추후 Whisper 응답에 언어 정보가 포함되면 우선 사용하도록 변경 가능
    private String resolveResultLanguage(SttRequest request, WhisperResponse whisperResponse) {
        if (request != null && hasText(request.getLanguageHint())) {
            return request.getLanguageHint();
        }
        // TODO: Whisper 응답에 language 필드가 생기면 whisperResponse.getLanguage() 사용
        return null;
    }

    // 파일 이름 보정
    private String resolveFileName(SttRequest request) {
        if (request == null) {
            return "audio.wav";
        }
        if (hasText(request.getFileName())) {
            return request.getFileName();
        }
        return "audio.wav";
    }

    // 문자열 유틸
    private boolean hasText(String value) {
        return (value != null && !value.isBlank());
    }

    // =============================
    // 6. Whisper JSON 응답 DTO
    // =============================

    /**
     * OpenAI /audio/transcriptions 기본 응답 스펙:
     *  {
     *      "text": "인식된 텍스트 ..."
     *  }
     *
     * TODO:
     *  - 향후 OpenAI 응답에 language, duration 등 필드가 추가되면 여기 확장
     */
    @Getter
    @Setter
    public static class WhisperResponse {
        private String text;
    }
}
