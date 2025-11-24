# 🤖 GPT AI Module – OCR 기반 상품 요약 · 쇼호스트 멘트 · 마케팅 포인트

이 문서는 **OCR 결과(OcrResult)** 를 활용해  
**상품 정보 요약 / 쇼호스트 멘트 / 마케팅 & 자막 포인트** 를 생성하는  
**GPT 연동 모듈**의 설계 & 구현 흐름을 정리한 문서입니다.

---

## 🔎 1) 역할 한 줄 요약

> **입력:** DB에 저장된 상품기술서(OCR 결과)  
> **출력:**
> - ① 상품 정보 요약 (SUMMARY)
> - ② 쇼호스트 멘트 스크립트 (HOST_SCRIPT)
> - ③ 홈쇼핑용 마케팅 포인트 & 자막 문구 (MARKETING_POINTS)

이 모듈은 `/ocr/ai` 화면에서 탭 형태로 노출되며,  
각 탭에서 **AI 생성 → 결과 확인 → (원하면) 히스토리 DB 저장** 까지 이어집니다.

---

## 🎯 2) 사용 시나리오 & 모드

### 2.1 공통 입력

- **원본 텍스트:** `OcrResult.editedText`
    - OCR로 추출된 텍스트 + 사용자가 수정한 최종본
- **메타 정보:** 제목, 파일명, 생성일 등은 프롬프트 보조 정보로도 활용 가능

### 2.2 3가지 모드

| 모드 코드 (`resultType`) | 화면 탭 라벨                          | 용도 설명 |
|--------------------------|----------------------------------------|-----------|
| `SUMMARY`                | 3-1. 상품 정보 요약                  | 상품기술서 핵심 내용 요약 |
| `HOST_SCRIPT`            | 3-2. 쇼호스트 멘트                   | 홈쇼핑 방송용 멘트 스크립트 |
| `MARKETING_POINTS`       | 3-3. 마케팅 & 자막 포인트           | 자막·배너·포인트 카피 |

각 모드는 **서로 다른 프롬프트 / 모델 설정을 사용**할 수 있도록 설계되어 있습니다.

---

## ⚙️ 3) OpenAI 설정 구조 (`application.yml`)

```yaml
openai:
  api:
    key: ${OPENAI_API_KEY}          # OS 환경변수에서 주입
    base-url: https://api.openai.com/v1
    default-model: gpt-3.5-turbo    # 기본 모델
    temperature: 0.4                # 정보 위주 답변 → 낮은 값

  models:
    summary: gpt-3.5-turbo          # 상품 요약용
    hostScript: gpt-3.5-turbo       # 쇼호스트 멘트용
    marketingPoints: gpt-3.5-turbo  # 마케팅 포인트용
```

> API 키는 코드에 하드코딩하지 않고,
>OS 환경변수 OPENAI_API_KEY → application.yml → @ConfigurationProperties → @Bean 으로 이어지는 구조.

---

## 🧩 4) OpenAiConfig – 설정 + WebClient Bean
- // com.example.bboo_technology.Config.OpenAiConfig
```java
@Configuration
@ConfigurationProperties(prefix = "openai")
@Data
public class OpenAiConfig {

    private Api api = new Api();
    private Models models = new Models();

    @Data
    public static class Api {
        private String key;
        private String baseUrl;
        private String defaultModel;
        private double temperature;
    }

    @Data
    public static class Models {
        private String summary;
        private String hostScript;
        private String marketingPoints;
    }

    /** OpenAI 호출용 WebClient Bean */
    @Bean
    public WebClient openAiWebClient() {
        return WebClient.builder()
                .baseUrl(api.getBaseUrl()) // 예: https://api.openai.com/v1
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + api.getKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** 기동 시 설정 확인용 로그 */
    @PostConstruct
    public void logConfig() {
        String masked = api.getKey() != null && api.getKey().length() > 8
                ? api.getKey().substring(0, 4) + "****"
                : "(null)";

        log.info("=== OpenAI 설정 확인 ===");
        log.info("API Key 존재 여부: {}", api.getKey() != null);
        log.info("API Key (masked): {}", masked);
        log.info("Base URL: {}", api.getBaseUrl());
        log.info("Default Model: {}", api.getDefaultModel());
        log.info("Summary Model: {}", models.getSummary());
        log.info("Host Script Model: {}", models.getHostScript());
        log.info("Marketing Points Model: {}", models.getMarketingPoints());
        log.info("=======================");
    }
}
```
> 💡 실제 프로젝트에서는 /openai/debug 같은 테스트용 엔드포인트를 만들어
> 설정 값을 JSON으로 한번 더 검증했습니다.

--- 

## 🧠 5) OcrAiGptService – GPT 호출 서비스
- ### 5.1 역할
    - GPT 호출 공통 로직을 캡슐화
    - 모드별로 서로 다른:
      - 모델 선택
    - 시스템 프롬프트
    - 후처리 전략
      - 추후 토큰 사용량(usage)도 여기서 파싱 → DB로 전달 가능

--- 

## 5.2 인터페이스(개념)
```java
public interface OcrAiGptService {

    String generateSummary(OcrResultDto ocrResultDto);

    String generateHostScript(OcrResultDto ocrResultDto);

    String generateMarketingPoints(OcrResultDto ocrResultDto);

    // (선택) 공통 호출 메서드에서 usage 정보도 함께 반환하고 싶다면
    // OcrAiGptResponse callOpenAi(...);
}

```

## 5.3 구현 예시 (핵심 로직)
```java
// com.example.bboo_technology.Service.Ocrservice.OcrAiGptServiceImpl

@Slf4j
@Service
@RequiredArgsConstructor
public class OcrAiGptServiceImpl implements OcrAiGptService {

    private final OpenAiConfig openAiConfig;
    private final WebClient openAiWebClient;

    @Override
    public String generateSummary(OcrResultDto dto) {
        String prompt = buildSummaryPrompt(dto);
        String model  = openAiConfig.getModels().getSummary();
        return callOpenAi(model, prompt, dto.getEditedText());
    }

    @Override
    public String generateHostScript(OcrResultDto dto) {
        String prompt = buildHostScriptPrompt(dto);
        String model  = openAiConfig.getModels().getHostScript();
        return callOpenAi(model, prompt, dto.getEditedText());
    }

    @Override
    public String generateMarketingPoints(OcrResultDto dto) {
        String prompt = buildMarketingPrompt(dto);
        String model  = openAiConfig.getModels().getMarketingPoints();
        return callOpenAi(model, prompt, dto.getEditedText());
    }

    /**
     * 공통 OpenAI ChatCompletion 호출
     */
    private String callOpenAi(String model, String systemPrompt, String userText) {

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", openAiConfig.getApi().getTemperature(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user",   "content", userText)
                )
        );

        try {
            Map<String, Object> response = openAiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

            // choices[0].message.content 파싱
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.get("choices");
            Map<String, Object> first = choices.get(0);
            Map<String, Object> message =
                    (Map<String, Object>) first.get("message");

            String content = (String) message.get("content");

            // (선택) usage 토큰 파싱
            // Map<String, Object> usage = (Map<String, Object>) response.get("usage");
            // Integer promptTokens = (Integer) usage.get("prompt_tokens");
            // Integer completionTokens = (Integer) usage.get("completion_tokens");
            // Integer totalTokens = (Integer) usage.get("total_tokens");
            //
            // → 이 값들은 OcrGptResultDto에 세팅 후 DB 저장 단계에서 활용 가능

            return content != null ? content.trim() : "";

        } catch (Exception e) {
            log.error("OpenAI 호출 중 오류 발생", e);
            throw new RuntimeException("OpenAI 호출 중 오류가 발생했습니다.", e);
        }
    }

    // =========================
    // 프롬프트 빌더들
    // =========================

    private String buildSummaryPrompt(OcrResultDto dto) {
        return """
               당신은 홈쇼핑 MD를 돕는 상품 기획 요약 전문가입니다.
               아래 텍스트는 하나의 상품기술서입니다.

               - 핵심 스펙, 장점, 주의사항을 항목별로 간결하게 정리해 주세요.
               - 불필요한 문장은 제거하고, 방송·페이지에서 바로 쓸 수 있게 요약합니다.
               - 출력은 한국어로, 마크다운 리스트 형식으로 작성해 주세요.
               """;
    }

    private String buildHostScriptPrompt(OcrResultDto dto) {
        return String.format("""
                당신은 홈쇼핑 쇼호스트입니다.
                상품명(또는 주요 키워드): %s

                아래 상품기술서를 기반으로,
                - 방송에서 실제로 사용할 수 있는 멘트 스크립트를 작성해 주세요.
                - 오프닝 → 문제 제기 → 해결 포인트 → 구성/혜택 → 마무리 흐름으로 구성합니다.
                - 톤은 친근하지만 과장되지 않게, 시청자를 설득하는 방식으로 작성해 주세요.
                """, dto.getTitle());
    }

    private String buildMarketingPrompt(OcrResultDto dto) {
        return """
               당신은 홈쇼핑 마케팅 카피라이터입니다.

               아래 상품기술서를 기반으로,
               1) 메인 카피 3개
               2) 서브 카피 5개
               3) 방송 화면 하단 자막용 짧은 문구 5개
               를 한국어로 생성해 주세요.

               - 각 항목은 번호 매기기와 줄바꿈을 잘 사용해 가독성을 높여 주세요.
               """;
    }
}

```

>🔁 실제 프로젝트에서는 프롬프트를 .md 템플릿 파일로 분리하거나,
>DB/관리 콘솔에서 수정 가능하게 확장할 수도 있습니다.

--- 

## 🧾 6) 컨트롤러 – /ocr/ai + JSON API
- ### 6.1 화면 진입: /ocr/ai

  - 역할

    - 좌측: OCR 결과 리스트 (OcrResult)

    - 우측: 선택된 OCR 텍스트 + GPT 탭 3종

- ### 컨트롤러에서는 단순히 목록 + 선택된 한 건을 Model로 넘겨줍니다.

```java
// OcrController.java (일부)

@GetMapping("/ai")
public String ocrAiPage(
@RequestParam(name = "id", required = false) Long id,
Model model
) {
// 1) OCR 결과 리스트 (최신순)
List<OcrResultDto> ocrList = ocrResultService.findAllOrderByCreatedAtDesc();
model.addAttribute("ocrList", ocrList);

    // 2) 우측 패널에 표시할 선택된 OCR
    OcrResultDto selected = null;
    if (!ocrList.isEmpty()) {
        if (id != null) {
            selected = ocrList.stream()
                    .filter(o -> id.equals(o.getId()))
                    .findFirst()
                    .orElse(ocrList.get(0)); // 못 찾으면 첫 번째
        } else {
            selected = ocrList.get(0);
        }
    }
    model.addAttribute("selectedOcr", selected);

    return "ocr/ocr_ai";
}
```

- ### 6.2 AI 호출 엔드포인트 (JSON 응답)

  - 프론트에서는 fetch("/ocr/ai/summary", …) 형식으로 호출합니다.
```java
// OcrController.java (일부)

@PostMapping("/ai/summary")
@ResponseBody
public Map<String, Object> generateSummary(@RequestParam("id") Long ocrResultId) {
return doGenerateAi(ocrResultId, "SUMMARY");
}

@PostMapping("/ai/host")
@ResponseBody
public Map<String, Object> generateHost(@RequestParam("id") Long ocrResultId) {
return doGenerateAi(ocrResultId, "HOST_SCRIPT");
}

@PostMapping("/ai/marketing")
@ResponseBody
public Map<String, Object> generateMarketing(@RequestParam("id") Long ocrResultId) {
return doGenerateAi(ocrResultId, "MARKETING_POINTS");
}

/**
* 공통 AI 생성 처리
  */
  private Map<String, Object> doGenerateAi(Long ocrResultId, String type) {
  Map<String, Object> result = new HashMap<>();

  try {
  OcrResultDto dto = ocrResultService.findById(ocrResultId);
  if (dto == null) {
  result.put("success", false);
  result.put("message", "해당 ID의 OCR 결과를 찾을 수 없습니다.");
  return result;
  }

       String content = switch (type) {
           case "SUMMARY"          -> ocrAiGptService.generateSummary(dto);
           case "HOST_SCRIPT"      -> ocrAiGptService.generateHostScript(dto);
           case "MARKETING_POINTS" -> ocrAiGptService.generateMarketingPoints(dto);
           default -> throw new IllegalArgumentException("지원하지 않는 타입: " + type);
       };

       result.put("success", true);
       result.put("content", content);
       result.put("type", type);

  } catch (Exception e) {
  log.error("AI 생성 중 오류 - type={}, ocrResultId={}", type, ocrResultId, e);
  result.put("success", false);
  result.put("message", "AI 생성 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
  }

  return result;
  }

```

## 🎨 7) 프론트엔드 연동 – ocr_ai.html
- ### 7.1 탭 구조

- 좌측: OCR 문서 리스트 (ocrList)

- 우측 상단: 선택된 문서 정보 (selectedOcr)

- 우측 하단: 3개의 탭 + 텍스트 영역
```html
<ul class="nav nav-tabs" id="gptTab" role="tablist">
  <li class="nav-item">
    <button class="nav-link active"
            id="summary-tab"
            data-bs-toggle="tab"
            data-bs-target="#summary-panel">
      3-1. 상품 정보 요약
    </button>
  </li>
  <li class="nav-item">
    <button class="nav-link"
            id="host-tab"
            data-bs-toggle="tab"
            data-bs-target="#host-panel">
      3-2. 쇼호스트 멘트
    </button>
  </li>
  <li class="nav-item">
    <button class="nav-link"
            id="marketing-tab"
            data-bs-toggle="tab"
            data-bs-target="#marketing-panel">
      3-3. 마케팅 &amp; 자막 포인트
    </button>
  </li>
</ul>

```
- ### 7.2 JS – 버튼 클릭 → AI 호출
```html
document.addEventListener('DOMContentLoaded', function () {
const selectedIdInput = document.getElementById('selectedOcrId');
const ocrId = selectedIdInput ? selectedIdInput.value : "";

    function callAi(endpoint, textareaId) {
        const textarea = document.getElementById(textareaId);

        if (!ocrId) {
            alert("좌측에서 먼저 OCR 문서를 선택해 주세요.");
            return;
        }
        if (!textarea) {
            console.error("결과 영역 textarea를 찾을 수 없습니다: " + textareaId);
            return;
        }

        textarea.value = "AI 요청 중입니다...\n잠시만 기다려 주세요.";

        fetch(endpoint, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded"
            },
            body: "id=" + encodeURIComponent(ocrId)
        })
        .then(res => {
            if (!res.ok) throw new Error("HTTP 상태코드: " + res.status);
            return res.json();
        })
        .then(data => {
            if (data.success) {
                textarea.value = data.content;
            } else {
                textarea.value = "";
                alert(data.message || "AI 처리 중 오류가 발생했습니다.");
            }
        })
        .catch(err => {
            console.error("AI 요청 실패:", err);
            textarea.value = "";
            alert("AI 요청 중 통신 오류가 발생했습니다.");
        });
    }

    document.getElementById("btn-summary-generate")
        ?.addEventListener("click", () => callAi("/ocr/ai/summary", "summaryResult"));

    document.getElementById("btn-host-generate")
        ?.addEventListener("click", () => callAi("/ocr/ai/host", "hostResult"));

    document.getElementById("btn-marketing-generate")
        ?.addEventListener("click", () => callAi("/ocr/ai/marketing", "marketingResult"));
});

```
---

## 📊 8) 결과 저장 – OcrGptResult 테이블 연동

### 📌 상세한 히스토리/엑셀 내보내기는
- docs/excel-history-module.md
에서 다룹니다.
- 여기서는 GPT 모듈과의 연결 포인트만 정리합니다.

### 8.1 엔티티 요약
| 컬럼                | 설명                     |
| ----------------- | ---------------------- |
| id                | PK                     |
| ocr_result_id     | OCR 원본 FK              |
| result_type       | SUMMARY/HOST/MARKETING |
| model             | 사용 모델명                 |
| temperature       | 사용 온도                  |
| content           | GPT 결과물                |
| prompt_tokens     | 선택                     |
| completion_tokens | 선택                     |
| total_tokens      | 선택                     |
| createdAt         | 생성 시각                  |

---

## 8.2 저장 API 개념 – /ocr/ai/save

- ### 프론트에서:
```javascript
async function saveAiResult(type) {
const ocrId = document.getElementById("selectedOcrId")?.value;
const textareaId = type === "SUMMARY"
? "summaryResult"
: type === "HOST_SCRIPT"
? "hostResult"
: "marketingResult";

    const content = document.getElementById(textareaId)?.value.trim();
    if (!ocrId || !content) {
        alert("저장할 내용이 없습니다.");
        return;
    }

    const params = new URLSearchParams();
    params.append("id", ocrId);
    params.append("type", type);
    params.append("content", content);

    const res = await fetch("/ocr/ai/save", {
        method: "POST",
        headers: {"Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"},
        body: params.toString()
    });

    const data = await res.json();
    if (data.success) {
        alert("저장 완료! (ID: " + data.id + ")");
    } else {
        alert("저장 실패: " + (data.message || "알 수 없는 오류"));
    }
}

```

> 백엔드에서는 OcrGptResultService.saveResult() 를 호출하여
> Entity ↔ DTO 변환 + 예외 처리 + 로그 등을 담당합니다.

--- 

## 🧮 9) 토큰 사용량(usage) 설계

현재 구조에서 토큰 사용량을 활용하려면:

callOpenAi() 에서 usage 필드 파싱

OcrGptResultDto 에 promptTokens, completionTokens, totalTokens 세팅

OcrGptResultServiceImpl.saveResult() 에서 엔티티로 매핑

히스토리 화면 & 엑셀 Export에서 표시/다운로드
```java
Map<String, Object> usage = (Map<String, Object>) response.get("usage");
Integer promptTokens     = (Integer) usage.get("prompt_tokens");
Integer completionTokens = (Integer) usage.get("completion_tokens");
Integer totalTokens      = (Integer) usage.get("total_tokens");

// dto.setPromptTokens(promptTokens);
// dto.setCompletionTokens(completionTokens);
// dto.setTotalTokens(totalTokens);
```

> 🔜 현재는 content 위주로 먼저 동작을 안정화하고,
> 추후 토큰 파트만 별도 단계에서 활성화하는 구조로 설계했습니다.

---

## 🚀 10) 요약 & 확장 포인트
- 지금 상태
1) ✅ OCR → DB 저장까지 완료된 텍스트를
2) ✅ 3가지 모드(SUMMARY / HOST / MARKETING)로
3) ✅ WebClient + OpenAI API를 통해 호출하고
4) ✅ 화면에서 결과 확인 + 히스토리로 저장할 수 있는 구조

- 향후 확장 아이디어

> 프롬프트를 DB/관리자 페이지에서 수정 가능하게

> 모델을 목적별로 다른 라인업으로 분리 (예: gpt-4.x / o1 등)

> “같은 조건으로 다시 생성” / “프롬프트 템플릿 저장” 모달

> A/B 테스트용으로 결과 두 개 생성 → 비교 모달

> Python 기반 로컬 LLM 또는 사내 LLM 으로 백엔드 교체도 가능하도록 인터페이스 추상화

---

## 🔗 11) 관련 문서

🔙 [메인 README.md](../README.md)
📘 [OCR 모듈 문서](./ocr-module.md)
📊 [Excel & 히스토리 모듈 문서](./excel-history-module.md)

>✍ 작성자
>김밥 (WhiteSnake-MrBBoo)