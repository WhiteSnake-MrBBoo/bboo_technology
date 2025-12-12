# 🎤 STT 모듈 (Java Spring Boot + OpenAI Whisper)

> 파일 기반 STT(음성 → 텍스트) 파이프라인  
> 패키지 기준: `com.example.bboo_technology.Service.Sttservice`

---

## 1. 처리 흐름 요약

1. 브라우저에서 `GET /api/stt/file` → STT 업로드 테스트 페이지 로드
2. 사용자가 오디오 파일 선택 후 `POST /api/stt/file` 요청
3. `SttController` → `SttService.transcribeFileForWeb(...)` 호출
4. `SttServiceImpl`:
    - 파일/세션/언어 → `SttRequest` 로 변환
    - `SttEngine` 구현체(`OpenAiWhisperSttEngine`) 호출
5. `OpenAiWhisperSttEngine`:
    - `/audio/transcriptions`(OpenAI Whisper)로 `multipart/form-data` 요청
    - 응답(JSON) → `SttResult` 매핑
6. `SttServiceImpl`:
    - `SttResult` + HTTP 상태 → `SttWebResponse` 생성
    - 컨트롤러에서 `SttResponseDto` + `ResponseEntity`로 반환

---

## 2. 설정 (YAML)

```yaml
# application.yml (발췌)

openai:
  api:
    key: ${OPENAI_API_KEY}
    base-url: https://api.openai.com/v1
    default-model: gpt-3.5-turbo
    temperature: 0.4

  # STT 전용 설정
  stt:
    provider: openai
    base-url: https://api.openai.com/v1
    model: whisper-1
    timeout-ms: 60000
```
---
## 3. DTO 설계
   ### 3.1 SttRequest (요청 DTO)

```java
@Builder
@Getter
public class SttRequest {

    // 세션 ID (없으면 서비스에서 UUID 생성)
    private final String sessionId;

    // 언어 힌트 (예: "ko", "en" / null 이면 auto)
    private final String languageHint;

    // 업로드된 파일 메타
    private final String fileName;
    private final long fileSize;

    // 실제 오디오 바이너리
    private final byte[] audioData;

    // 추후 확장용 메타정보 (ex: 방송 채널, 상품 ID 등)
    private final Map<String, Object> meta;
}

```
---

## 3.2 SttResult (도메인 내부용)
```java
@Builder
@Getter
public class SttResult {

    private final boolean success;

    private final String sessionId;
    private final String transcript;
    private final String language;       // 결과 언어 (현재는 hint 기반)
    private final Long durationSeconds;  // TODO: Whisper 응답에 따라 매핑

    private final String engineName;     // ex) "openai-whisper-whisper-1"

    private final String errorCode;      // ex) EMPTY_FILE, OPENAI_HTTP_400, ...
    private final String errorMessage;   // 내부용 상세 메시지

    private final Instant createdAt;
}

```
## 3.3 SttResponseDto (클라이언트 응답용)
```java
@Builder
@Getter
public class SttResponseDto {

    private final boolean success;
    private final String message;    // 사용자 친화적 메시지

    private final String sessionId;
    private final String transcript;
    private final String language;
    private final String engineName;

    // (옵션) 디버깅용, 운영에서 제거 가능
    private final String errorMessage;
}


```
---
## 4. Service 레이어
### 4.1 SttService 인터페이스
```java

public interface SttService {

    /**
     * 업로드된 파일을 STT 엔진으로 변환하고,
     * Web 응답용 DTO + HTTP 상태를 함께 반환.
     */
    SttWebResponse transcribeFileForWeb(MultipartFile file,
                                        String sessionId,
                                        String languageHint);
}

```
## 4.2 SttServiceImpl (핵심 처리)
```java

@Slf4j
@Service
@RequiredArgsConstructor
public class SttServiceImpl implements SttService {

    private final SttEngine sttEngine;

    @Override
    public SttWebResponse transcribeFileForWeb(MultipartFile file,
                                               String sessionId,
                                               String languageHint) {

        // 1. 파일 기본 검증
        if (file == null || file.isEmpty()) {
            SttResult result = buildEmptyFileResult(sessionId);
            return SttWebResponse.from(result, HttpStatus.BAD_REQUEST);
        }

        String effectiveSessionId = resolveSessionId(sessionId);

        byte[] audioBytes;
        try {
            audioBytes = file.getBytes();
        } catch (IOException e) {
            log.error("[STT] 파일 읽기 실패", e);
            SttResult result = buildIoErrorResult(effectiveSessionId);
            return SttWebResponse.from(result, HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // 2. SttRequest 생성
        SttRequest request = SttRequest.builder()
                .sessionId(effectiveSessionId)
                .languageHint(languageHint)
                .fileName(file.getOriginalFilename())
                .fileSize(file.getSize())
                .audioData(audioBytes)
                .meta(null)
                .build();

        // 3. 엔진 호출
        SttResult result = sttEngine.transcribe(request);

        // 4. HTTP 상태 코드 결정
        HttpStatus status = resolveHttpStatus(result);

        return SttWebResponse.from(result, status);
    }

    // (이하: sessionId 생성, 기본 에러 결과 빌더, HttpStatus 매핑 등 헬퍼 메서드들)
}

```
## 4.3 SttWebResponse (응답 래퍼)
```java

@Getter
@AllArgsConstructor(staticName = "of")
public class SttWebResponse {

    private final SttResponseDto body;
    private final HttpStatus httpStatus;

    public static SttWebResponse from(SttResult result, HttpStatus status) {
        String message = buildUserMessage(result);

        SttResponseDto dto = SttResponseDto.builder()
                .success(result.isSuccess())
                .message(message)
                .sessionId(result.getSessionId())
                .transcript(result.getTranscript())
                .language(result.getLanguage())
                .engineName(result.getEngineName())
                .errorMessage(result.getErrorMessage())
                .build();

        return SttWebResponse.of(dto, status);
    }

    private static String buildUserMessage(SttResult result) {
        if (result == null) return "STT 처리 결과가 존재하지 않습니다.";
        if (result.isSuccess()) return "음성 인식이 성공적으로 완료되었습니다.";
        if ("NOT_IMPLEMENTED".equals(result.getErrorCode())) {
            return "현재 STT 엔진은 Stub 상태입니다. OpenAI Whisper 연동이 아직 완료되지 않았습니다.";
        }
        return "음성 인식 처리 중 오류가 발생했습니다. (code=" + result.getErrorCode() + ")";
    }
}

```
---
# 5. Controller & View
### 5.1 SttController

```java

@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/stt")
public class SttController {

    private final SttService sttService;

    /**
     * STT 파일 업로드 테스트 페이지
     * - GET /api/stt/file
     * - View: templates/stt/stt-upload.html
     */
    @GetMapping("/file")
    public String showSttUploadPage() {
        return "stt/stt-upload";
    }

    /**
     * 업로드된 오디오 파일을 STT 엔진으로 변환하는 API
     * - POST /api/stt/file
     */
    @PostMapping("/file")
    public ResponseEntity<SttResponseDto> transcribeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "language", required = false) String languageHint
    ) {
        log.info("[STT-CTRL] /api/stt/file 호출 - fileName={}, sessionId={}, language={}",
                safeFileName(file), sessionId, languageHint);

        SttWebResponse webResponse =
                sttService.transcribeFileForWeb(file, sessionId, languageHint);

        return new ResponseEntity<>(webResponse.getBody(), webResponse.getHttpStatus());
    }

    private String safeFileName(MultipartFile file) {
        if (file == null) return "null";
        String name = file.getOriginalFilename();
        return (name != null) ? name : "unknown";
    }
}

```
## 5.2 테스트용 업로드 페이지 (templates/stt/stt-upload.html)
```html
<!DOCTYPE html>
<html lang="ko" xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>STT 테스트 업로드</title>
    <link
        rel="stylesheet"
        href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css"
    />
</head>
<body>
<div class="container mt-5">
    <h3>STT 테스트 업로드</h3>
    <p class="text-muted">
        오디오 파일을 업로드해서 OpenAI Whisper 기반 STT 파이프라인을 테스트합니다.
    </p>

    <form
        method="post"
        action="/api/stt/file"
        enctype="multipart/form-data"
        class="mt-4"
    >
        <div class="mb-3">
            <label for="file" class="form-label">오디오 파일</label>
            <input type="file" id="file" name="file" class="form-control"
                   accept="audio/*" required>
        </div>

        <div class="mb-3">
            <label for="sessionId" class="form-label">세션 ID (옵션)</label>
            <input type="text" id="sessionId" name="sessionId"
                   class="form-control" placeholder="비워두면 서버에서 자동 생성">
        </div>

        <div class="mb-3">
            <label for="language" class="form-label">언어 힌트 (옵션)</label>
            <input type="text" id="language" name="language"
                   class="form-control" placeholder="예: ko, en (비워두면 auto)">
        </div>

        <button type="submit" class="btn btn-primary">STT 변환 실행</button>
    </form>
</div>
</body>
</html>


```
---
## 6. STT 엔진 구현체 (OpenAiWhisperSttEngine)
```java
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenAiWhisperSttEngine implements SttEngine {

    private final OpenAiSttProperties sttProperties;

    @Value("${openai.api.key}")
    private String openAiApiKey;

    @Override
    public SttResult transcribe(SttRequest request) {

        logRequestSummary(request);

        if (!isValidRequest(request)) {
            SttResult invalid = buildInvalidRequestResult(request);
            logResultSummary(invalid);
            return invalid;
        }

        SttResult finalResult;
        try {
            WhisperResponse whisperResponse = callOpenAiWhisper(request);
            finalResult = buildSuccessResult(request, whisperResponse);
        } catch (WebClientResponseException e) {
            finalResult = buildHttpErrorResult(request, e);
        } catch (Exception e) {
            finalResult = buildUnknownErrorResult(request, e);
        }

        logResultSummary(finalResult);
        return finalResult;
    }

    private WhisperResponse callOpenAiWhisper(SttRequest request) {

        WebClient webClient = buildWebClient();
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();

        ByteArrayResource fileResource = new ByteArrayResource(request.getAudioData()) {
            @Override
            public String getFilename() {
                return resolveFileName(request);
            }
        };

        // 파일 + 모델 + 옵션 파라미터 구성
        bodyBuilder
                .part("file", fileResource)
                .contentType(MediaType.APPLICATION_OCTET_STREAM);
        bodyBuilder.part("model", sttProperties.getModel());
        if (hasText(request.getLanguageHint())) {
            bodyBuilder.part("language", request.getLanguageHint());
        }
        bodyBuilder.part("response_format", "json");

        MultiValueMap<String, HttpEntity<?>> multipartData = bodyBuilder.build();

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

    private WebClient buildWebClient() {
        return WebClient.builder()
                .baseUrl(sttProperties.getBaseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + openAiApiKey)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private Duration resolveTimeout() {
        Integer timeoutMs = sttProperties.getTimeoutMs();
        if (timeoutMs == null || timeoutMs <= 0) {
            return Duration.ofSeconds(60);
        }
        return Duration.ofMillis(timeoutMs);
    }

    // (이하: 성공/에러 Result 빌더, logRequestSummary, logResultSummary, hasText 등 헬퍼 메서드)

    @Getter
    @Setter
    public static class WhisperResponse {
        private String text;
    }
}

```
---
## 7. 동작 예시
   ### 7.1 성공 케이스 (.wav 파일 업로드)
```json
{
  "success": true,
  "message": "음성 인식이 성공적으로 완료되었습니다.",
  "sessionId": "f6b04048-60ae-425f-9e3e-7d9113f8f2fa",
  "transcript": "맛있어요, 맛있어요, 맛있습니다.",
  "language": null,
  "engineName": "openai-whisper-whisper-1",
  "errorMessage": null
}

```
### 7.2 잘못된 파일 형식 (avi 등) 업로드 시
```json
{
  "success": false,
  "message": "음성 인식 처리 중 오류가 발생했습니다. (code=OPENAI_HTTP_400)",
  "sessionId": "c2653391-4157-48b5-94be-1933d3acf976",
  "transcript": null,
  "language": null,
  "engineName": "openai-whisper-whisper-1",
  "errorMessage": "OpenAI Whisper 호출 중 HTTP 오류가 발생했습니다. (status=400) / details={\"error\":{\"message\":\"잘못된 파일 형식입니다. 지원되는 형식: ['flac', 'm4a', 'mp3', 'mp4', 'mpeg', 'mpga', 'oga', 'ogg', 'wav', 'webm']\",\"type\":\"invalid_request_error\"...}}"
}

```
> 이후에는 서비스 레벨에서 파일 확장자를 선필터링하여
UNSUPPORTED_AUDIO_FORMAT 에러로 사용자에게 안내하는 방식으로 확장 가능.
---
## 8. 로드맵

- OpenAI Whisper 클라우드 STT 기본 연동
- 파일 업로드 테스트 페이지 (/api/stt/file)
- DTO / Service / Engine 분리 설계
- LocalPythonSttEngine (Python 서버 + 로컬 Whisper + CUDA)
- STT 결과 → GPT 파이프라인 연동 (요약 / 멘트 / 자막 / 번역)
- WebSocket 기반 실시간 STT + LLM 후처리
- STT 히스토리 DB 저장 및 관리 화면 추가


<details>
  <summary>JAVA 코드 웹/API 응답용 래퍼 DTO</summary>


  ```java
// (추가) 웹/API 응답용 래퍼 DTO
@Getter
@Builder
public class SttWebResponse {

    private final SttResponseDto body;   // 실제 응답 데이터
    private final HttpStatus httpStatus; // HTTP 상태 코드

}
  ```
</details>
<details>
  <summary>JAVA 코드 웹/API 응답용 래퍼 DTO</summary>

  ```java
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
  ```

</details>

