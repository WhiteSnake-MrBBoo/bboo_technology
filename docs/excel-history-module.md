# 📊 AI 히스토리 & Excel Export 모듈 (`docs/excel-history-module.md`)

> **키워드:** GPT 결과 이력 관리, 상세 조회, 토큰 사용량 추적, 엑셀 내보내기, 옵션 모달, Apache POI

---

## 1) 모듈 개요

이 모듈은 **OCR + GPT**로 생성된 모든 AI 결과를 한 곳에서 관리하고,
필요한 이력을 **엑셀로 내보내기(Export)** 하는 기능을 담당합니다.

구성 요소는 크게 네 가지입니다.

1. **AI 히스토리 리스트 (`/ocr/ai/history`)**
2. **우측 상세 뷰 (선택 결과 미리보기)**
3. **체크박스 기반 선택 / 전체 엑셀 내보내기**
4. **엑셀 옵션 모달(Export 옵션 설정 + Apache POI 연동)**

---

## 2) 화면 구조 (UI 레이아웃)

### 📺 좌측 – AI 결과 목록

- 상단: `AI 결과 목록 (최신순)` 제목
- 컬럼:
    - 체크박스(선택용)
    - `ID`
    - `타입` (SUMMARY / HOST_SCRIPT / MARKETING_POINTS)
    - `OCR 제목` (예: 공공 데이터 api 저장 / linux001 …)
    - `생성 시각`
- 정렬: `createdAt DESC` (최신 결과가 맨 위)

### 📺 우측 – 선택된 AI 결과 상세

- 상단 제목 예
    - `HOST_SCRIPT · 공공 데이터 api 저장`
    - `SUMMARY · linux001`
- 메타 정보
    - 파일명: `[국가교통정보센터] Open_API_매뉴얼.pdf`
    - 모델: `gpt-3.5-turbo`
    - 생성 시각: `2025-11-24 10:41`
- 본문
    - `<textarea>` 로 전체 내용을 스크롤하며 확인 가능
    - 나중에 “비교 모달”, “프롬프트 재사용” 기능 확장 예정

### 🧰 하단 버튼들 (히스토리 화면 기준)

- `내용 확대` (선택 결과 전체 화면 모달 – 향후 확장)
- `현재 목록 엑셀` (필터/정렬 기준 전체 내보내기)
- `선택 항목 엑셀` (체크박스 선택된 항목만 내보내기)
- `엑셀 옵션 내보내기` (옵션 모달 열기 – 범위/컬럼/파일명 등 선택)

---

## 3) 도메인 & DB 구조

### 📌 3-1. `OcrGptResult` 엔티티 (AI 히스토리 테이블)

| 컬럼명             | 타입        | 설명                                              |
|--------------------|------------|---------------------------------------------------|
| `id`               | BIGINT(PK) | GPT 결과 PK                                       |
| `ocr_result_id`    | BIGINT(FK) | 원본 OCR 결과(`OcrResult`)와 연결                 |
| `result_type`      | VARCHAR    | SUMMARY / HOST_SCRIPT / MARKETING_POINTS          |
| `model`            | VARCHAR    | gpt-3.5-turbo 등                                  |
| `temperature`      | DECIMAL    | 모델 temperature                                  |
| `prompt_tokens`    | INT        | 프롬프트 토큰 수                                  |
| `completion_tokens`| INT        | 응답 토큰 수                                      |
| `total_tokens`     | INT        | 전체 토큰 수                                      |
| `content`          | LONGTEXT   | GPT 결과 전체 텍스트 (요약/멘트/포인트)           |
| `created_at`       | DATETIME   | 생성 시각                                         |
| `updated_at`       | DATETIME   | 수정 시각 (옵션)                                  |

> **참고:** `content` 컬럼은 GPT 결과가 길어질 수 있으므로 `LONGTEXT` 타입 사용.

## 📌 3-2. `OcrGptResultDto`

- ### 히스토리 화면 & 엑셀 내보내기에 사용하는 DTO (일부 필드)

```java
public class OcrGptResultDto {

    private Long id;
    private Long ocrResultId;

    private String resultType;   // SUMMARY / HOST_SCRIPT / MARKETING_POINTS
    private String model;
    private Double temperature;

    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;

    private String content;

    // 연관된 OCR 정보 (화면용)
    private String ocrTitle;
    private String ocrFileName;

    private LocalDateTime createdAt;
}
```
> Entity ↔ DTO 변환은 ModelMapper + 수동 매핑으로 처리합니다.
---
## 4) 동작 흐름 (히스토리 & 엑셀)
   - ### 🧭 기본 흐름

- /ocr/ai/history 접속

- OcrGptResultService.findAllOrderByCreatedAtDesc() 호출

- 좌측 리스트 + 첫 번째 결과를 우측 상세로 표시

- 사용자가 다른 행 클릭 → id 쿼리 파라미터로 다시 /history?id={id} 요청

- 체크박스를 활용해 “현재 목록 / 선택 항목 / 옵션 기반 엑셀” 실행

## 5) 컨트롤러 설계 – OcrAiHistoryController
```java
@Controller
@RequestMapping("/ocr/ai")
@RequiredArgsConstructor
@Slf4j
public class OcrAiHistoryController {

    private final OcrGptResultService ocrGptResultService;
    private final ExcelService excelService;

    /**
     * GET /ocr/ai/history
     * AI 히스토리 기본 리스트 + 우측 상세
     */
    @GetMapping("/history")
    public String showAiHistory(
            @RequestParam(name = "id", required = false) Long id,
            Model model
    ) {
        List<OcrGptResultDto> historyList =
                ocrGptResultService.findAllOrderByCreatedAtDesc();
        model.addAttribute("historyList", historyList);

        // 우측 상세 패널 - 선택된 항목
        OcrGptResultDto selected = null;
        if (!historyList.isEmpty()) {
            if (id != null) {
                selected = historyList.stream()
                        .filter(item -> id.equals(item.getId()))
                        .findFirst()
                        .orElse(historyList.get(0));
            } else {
                selected = historyList.get(0); // 기본: 가장 최신 1건
            }
        }
        model.addAttribute("selectedHistory", selected);

        log.info("AI History 요청 id={}", id);
        return "ocr/ocr_ai_history";
    }

    /**
     * GET /ocr/ai/history/export
     * 1단계: 필터 없이 "현재 목록 전체" 엑셀로 다운로드.
     */
    @GetMapping("/history/export")
    public void exportAiHistory(HttpServletResponse response) {
        List<OcrGptResultDto> historyList =
                ocrGptResultService.findAllOrderByCreatedAtDesc();

        log.info("AI 히스토리 전체 엑셀 다운로드 - count={}", historyList.size());
        excelService.writeGptHistoryToExcel(historyList, response);
    }

    /**
     * POST /ocr/ai/history/export-selected
     * 2단계: 체크박스로 선택된 결과만 엑셀로 다운로드.
     *  - ids=1&ids=3&ids=5 형식으로 넘어옴.
     */
    @PostMapping("/history/export-selected")
    public void exportSelectedAiHistory(
            @RequestParam("ids") List<Long> ids,
            HttpServletResponse response
    ) {
        log.info("선택 항목 엑셀 다운로드 요청 - ids={}", ids);

        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("선택된 항목이 없습니다.");
        }

        List<OcrGptResultDto> selectedList =
                ocrGptResultService.findByIds(ids);

        excelService.writeGptHistoryToExcel(selectedList, response);
    }

    /**
     * POST /ocr/ai/history/export-with-options
     * 3단계: 옵션 모달에서 넘어온 조건(scope/컬럼/파일명)을 반영해서 엑셀 생성.
     */
    @PostMapping("/history/export-with-options")
    public void exportWithOptions(
            AiHistoryExcelOptionDto option,
            HttpServletResponse response
    ) {
        log.info("엑셀 옵션 기반 Export 요청: {}", option);

        List<OcrGptResultDto> data;

        // 1) 범위 결정
        if ("SELECTED".equalsIgnoreCase(option.getScope())
                && option.getSelectedIds() != null
                && !option.getSelectedIds().isEmpty()) {

            data = ocrGptResultService.findByIds(option.getSelectedIds());
        } else {
            data = ocrGptResultService.findAllOrderByCreatedAtDesc();
        }

        // 2) 옵션을 반영해 엑셀 생성
        excelService.writeGptHistoryToExcel(data, option, response);
    }
}

```
---
# 6) 서비스 계층 – OcrGptResultService
```java
public interface OcrGptResultService {

    // "이 결과 저장" 버튼에서 호출
    OcrGptResultDto saveResult(OcrGptResultDto dto);

    // 특정 OCR 결과에 대한 GPT 이력
    List<OcrGptResultDto> findByOcrResultId(Long ocrResultId);

    // 전체 히스토리 (최신순)
    List<OcrGptResultDto> findAllOrderByCreatedAtDesc();

    // 선택된 GPT 결과들만 조회
    List<OcrGptResultDto> findByIds(List<Long> ids);
}

```
> 구현: OcrGptResultServiceImpl

- ModelMapper + toDto() 공통 메서드로 Entity → DTO 변환

- 예외 발생 시 log.error() + 의미 있는 메시지로 래핑
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class OcrGptResultServiceImpl implements OcrGptResultService {

    private final OcrGptResultRepository ocrGptResultRepository;
    private final OcrResultRepository ocrResultRepository;
    private final ModelMapper modelMapper;

    @Override
    @Transactional
    public OcrGptResultDto saveResult(OcrGptResultDto dto) {
        try {
            if (dto.getOcrResultId() == null) {
                throw new IllegalArgumentException("OCR 결과 ID(ocrResultId)는 필수입니다.");
            }

            OcrResult ocrResult = ocrResultRepository.findById(dto.getOcrResultId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "해당 ID의 OCR 결과를 찾을 수 없습니다. id=" + dto.getOcrResultId()
                    ));

            OcrGptResult entity = modelMapper.map(dto, OcrGptResult.class);
            entity.setOcrResult(ocrResult);

            OcrGptResult saved = ocrGptResultRepository.save(entity);

            log.info("GPT 결과 저장 완료 - id={}, ocrResultId={}, type={}",
                    saved.getId(),
                    saved.getOcrResult() != null ? saved.getOcrResult().getId() : null,
                    saved.getResultType());

            return toDto(saved);

        } catch (Exception e) {
            log.error("GPT 결과 저장 중 예외 발생", e);
            throw new RuntimeException("GPT 결과를 저장하는 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OcrGptResultDto> findAllOrderByCreatedAtDesc() {
        try {
            return ocrGptResultRepository.findAllByOrderByCreatedAtDesc()
                    .stream()
                    .map(this::toDto)
                    .toList();
        } catch (Exception e) {
            log.error("GPT 결과 전체 히스토리 조회 중 예외 발생", e);
            throw new RuntimeException("GPT 결과 히스토리를 조회하는 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<OcrGptResultDto> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        try {
            return ocrGptResultRepository.findAllById(ids)
                    .stream()
                    .map(this::toDto)
                    .sorted((a, b) -> {
                        if (a.getCreatedAt() == null || b.getCreatedAt() == null) return 0;
                        return b.getCreatedAt().compareTo(a.getCreatedAt());
                    })
                    .toList();
        } catch (Exception e) {
            log.error("선택된 GPT 결과 조회 중 예외 발생 - ids={}", ids, e);
            throw new RuntimeException("선택된 GPT 결과를 조회하는 중 오류가 발생했습니다.", e);
        }
    }

    // 공통 Entity → DTO 매핑
    private OcrGptResultDto toDto(OcrGptResult entity) {
        if (entity == null) return null;

        OcrGptResultDto dto = modelMapper.map(entity, OcrGptResultDto.class);
        if (entity.getOcrResult() != null) {
            dto.setOcrResultId(entity.getOcrResult().getId());
            dto.setOcrTitle(entity.getOcrResult().getTitle());
            dto.setOcrFileName(entity.getOcrResult().getOriginalFileName());
        }
        return dto;
    }
}

```
---
## 7) 엑셀 Export 서비스 – ExcelService
   - ### 📦 7-1. 옵션 DTO – AiHistoryExcelOptionDto
```java
@Data
public class AiHistoryExcelOptionDto {

    // 내보낼 범위: ALL / SELECTED
    private String scope;

    // 선택된 히스토리 ID 리스트
    private List<Long> selectedIds;

    // 컬럼 포함 여부
    private boolean includeId;
    private boolean includeResultType;
    private boolean includeOcrTitle;
    private boolean includeOcrFileName;
    private boolean includeCreatedAt;
    private boolean includeModel;
    private boolean includeContent;
    private boolean includeTokens; // prompt/completion/total

    // 파일명 (확장자 제외)
    private String fileName;
}

```
  - ## 📦 7-2. ExcelService 구현 (Apache POI)
```java
@Service
public class ExcelService {

    /**
     * AI 히스토리 엑셀 생성 (옵션 반영 버전)
     *
     * @param data    내보낼 GPT 결과 리스트
     * @param option  엑셀 옵션 (범위/컬럼/파일명)
     * @param response HttpServletResponse - 바로 파일 스트림 전송
     */
    public void writeGptHistoryToExcel(
            List<OcrGptResultDto> data,
            AiHistoryExcelOptionDto option,
            HttpServletResponse response
    ) {
        // 1) 파일명 결정
        String baseName = (option.getFileName() == null || option.getFileName().isBlank())
                ? "ocr_ai_history"
                : option.getFileName().trim();
        String fileName = baseName + ".xlsx";

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("AI History");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            // 2) 헤더 작성
            int colCount = buildHeaderRow(sheet, option);

            // 3) 데이터 행 작성
            fillDataRows(sheet, data, option, dtf);

            // 4) 컬럼 너비 자동 조정
            for (int i = 0; i < colCount; i++) {
                sheet.autoSizeColumn(i);
            }

            // 5) HTTP 응답 헤더 설정
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20");

            response.setContentType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );
            response.setHeader(
                    "Content-Disposition",
                    "attachment; filename=\"" + encoded + "\""
            );

            workbook.write(response.getOutputStream());

        } catch (Exception e) {
            throw new RuntimeException("엑셀 파일 생성 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 1단계/2단계용 단순 버전 (기본 옵션 사용)
     */
    public void writeGptHistoryToExcel(
            List<OcrGptResultDto> data,
            HttpServletResponse response
    ) {
        AiHistoryExcelOptionDto defaultOpt = new AiHistoryExcelOptionDto();
        defaultOpt.setScope("ALL");
        defaultOpt.setIncludeId(true);
        defaultOpt.setIncludeResultType(true);
        defaultOpt.setIncludeOcrTitle(true);
        defaultOpt.setIncludeOcrFileName(true);
        defaultOpt.setIncludeCreatedAt(true);
        defaultOpt.setIncludeModel(true);
        defaultOpt.setIncludeContent(true);
        defaultOpt.setIncludeTokens(false);
        defaultOpt.setFileName("ocr_ai_history");

        writeGptHistoryToExcel(data, defaultOpt, response);
    }

    // ===== 내부 메서드들 =====

    /** 헤더 행을 작성하고, 생성된 컬럼 수를 반환 */
    private int buildHeaderRow(Sheet sheet, AiHistoryExcelOptionDto option) {
        Row header = sheet.createRow(0);
        int colIdx = 0;

        if (option.isIncludeId()) {
            header.createCell(colIdx++).setCellValue("ID");
        }
        if (option.isIncludeOcrTitle()) {
            header.createCell(colIdx++).setCellValue("OCR 제목");
        }
        if (option.isIncludeOcrFileName()) {
            header.createCell(colIdx++).setCellValue("파일명");
        }
        if (option.isIncludeResultType()) {
            header.createCell(colIdx++).setCellValue("결과 타입");
        }
        if (option.isIncludeModel()) {
            header.createCell(colIdx++).setCellValue("모델");
        }
        if (option.isIncludeCreatedAt()) {
            header.createCell(colIdx++).setCellValue("생성 시각");
        }
        if (option.isIncludeTokens()) {
            header.createCell(colIdx++).setCellValue("Prompt Tokens");
            header.createCell(colIdx++).setCellValue("Completion Tokens");
            header.createCell(colIdx++).setCellValue("Total Tokens");
        }
        if (option.isIncludeContent()) {
            header.createCell(colIdx++).setCellValue("내용");
        }

        return colIdx;
    }

    /** 데이터 행을 채워 넣는 메서드 */
    private void fillDataRows(
            Sheet sheet,
            List<OcrGptResultDto> data,
            AiHistoryExcelOptionDto option,
            DateTimeFormatter dtf
    ) {
        int rowIdx = 1;

        for (OcrGptResultDto dto : data) {
            Row row = sheet.createRow(rowIdx++);
            int colIdx = 0;

            if (option.isIncludeId()) {
                row.createCell(colIdx++).setCellValue(dto.getId());
            }
            if (option.isIncludeOcrTitle()) {
                row.createCell(colIdx++).setCellValue(dto.getOcrTitle());
            }
            if (option.isIncludeOcrFileName()) {
                row.createCell(colIdx++).setCellValue(dto.getOcrFileName());
            }
            if (option.isIncludeResultType()) {
                row.createCell(colIdx++).setCellValue(dto.getResultType());
            }
            if (option.isIncludeModel()) {
                row.createCell(colIdx++).setCellValue(dto.getModel());
            }
            if (option.isIncludeCreatedAt()) {
                String created = dto.getCreatedAt() != null
                        ? dtf.format(dto.getCreatedAt())
                        : "";
                row.createCell(colIdx++).setCellValue(created);
            }
            if (option.isIncludeTokens()) {
                row.createCell(colIdx++).setCellValue(
                        dto.getPromptTokens() != null ? dto.getPromptTokens() : 0
                );
                row.createCell(colIdx++).setCellValue(
                        dto.getCompletionTokens() != null ? dto.getCompletionTokens() : 0
                );
                row.createCell(colIdx++).setCellValue(
                        dto.getTotalTokens() != null ? dto.getTotalTokens() : 0
                );
            }
            if (option.isIncludeContent()) {
                row.createCell(colIdx++).setCellValue(dto.getContent());
            }
        }
    }
}

```
---
## 8) 프론트엔드 – ocr_ai_history.html의 핵심 JS
   - ### ✅ 체크박스 & 엑셀 옵션 모달 처리
```html
<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"
        integrity="sha384-YvpcrYf0tY3lHB60NNkmXc5s9fDVZLESaAA55NDzOxhy9GkcIdslK1eN7N6jIeHz"
        crossorigin="anonymous"></script>

<script>
document.addEventListener("DOMContentLoaded", function () {

    // 공통 유틸 ==========================
    function getSelectedIds() {
        const ids = [];
        document.querySelectorAll(".history-checkbox:checked").forEach(cb => {
            ids.push(cb.value);
        });
        return ids;
    }

    function clearChildren(elem) {
        while (elem.firstChild) {
            elem.removeChild(elem.firstChild);
        }
    }

    function addHidden(form, name, value) {
        const input = document.createElement("input");
        input.type = "hidden";
        input.name = name;
        input.value = value;
        form.appendChild(input);
    }

    // 0) 체크박스 클릭 시 행 클릭 이벤트 막기
    const rowCheckboxes = document.querySelectorAll(".history-checkbox");
    const headerCheckbox = document.getElementById("chkAll");

    rowCheckboxes.forEach(cb => {
        cb.addEventListener("click", function (event) {
            event.stopPropagation(); // 행 클릭과 분리
        });
    });

    if (headerCheckbox) {
        headerCheckbox.addEventListener("click", function (event) {
            event.stopPropagation();
        });
    }

    // 1) 전체 선택 / 해제
    if (headerCheckbox) {
        headerCheckbox.addEventListener("change", function () {
            const checked = headerCheckbox.checked;
            rowCheckboxes.forEach(cb => cb.checked = checked);
        });
    }

    // 2) (구버전) 선택 항목만 바로 엑셀로 보내기
    const btnExportSelected = document.getElementById("btnExportSelected");
    const selectedForm = document.getElementById("selectedExportForm");

    if (btnExportSelected && selectedForm) {
        btnExportSelected.addEventListener("click", function () {
            const selectedIds = getSelectedIds();

            if (selectedIds.length === 0) {
                alert("엑셀로 내보낼 항목을 먼저 선택해 주세요.");
                return;
            }

            clearChildren(selectedForm);
            selectedIds.forEach(id => addHidden(selectedForm, "ids", id));

            selectedForm.submit();
        });
    }

    // 3) 엑셀 옵션 모달 → 서버로 옵션 전달해서 엑셀 생성
    const btnExcelOptionConfirm = document.getElementById("btnExcelOptionConfirm");
    const excelForm = document.getElementById("excelExportForm"); // 숨겨진 폼

    if (btnExcelOptionConfirm && excelForm) {
        btnExcelOptionConfirm.addEventListener("click", function () {

            // 범위 (ALL / SELECTED)
            const scopeRadio = document.querySelector("input[name='excelScope']:checked");
            const scope = scopeRadio ? scopeRadio.value : "ALL";

            // 체크된 히스토리 ID들
            const selectedIds = getSelectedIds();

            if (scope === "SELECTED" && selectedIds.length === 0) {
                alert("체크한 항목만 내보내기를 선택하셨습니다.\n좌측 리스트에서 먼저 항목을 선택해 주세요.");
                return;
            }

            // 컬럼 포함 여부
            const includeId         = document.getElementById("includeId")?.checked         ?? false;
            const includeResultType = document.getElementById("includeResultType")?.checked ?? false;
            const includeOcrTitle   = document.getElementById("includeOcrTitle")?.checked   ?? false;
            const includeOcrFile    = document.getElementById("includeOcrFileName")?.checked?? false;
            const includeCreatedAt  = document.getElementById("includeCreatedAt")?.checked  ?? false;
            const includeModel      = document.getElementById("includeModel")?.checked      ?? false;
            const includeContent    = document.getElementById("includeContent")?.checked    ?? false;
            const includeTokens     = document.getElementById("includeTokens")?.checked     ?? false;

            const fileNameInput = document.getElementById("excelFileName");
            const fileName = fileNameInput ? fileNameInput.value.trim() : "";

            // 숨겨진 폼 채우기
            clearChildren(excelForm);

            addHidden(excelForm, "scope", scope);
            selectedIds.forEach(id => addHidden(excelForm, "selectedIds", id));

            addHidden(excelForm, "includeId", includeId);
            addHidden(excelForm, "includeResultType", includeResultType);
            addHidden(excelForm, "includeOcrTitle", includeOcrTitle);
            addHidden(excelForm, "includeOcrFileName", includeOcrFile);
            addHidden(excelForm, "includeCreatedAt", includeCreatedAt);
            addHidden(excelForm, "includeModel", includeModel);
            addHidden(excelForm, "includeContent", includeContent);
            addHidden(excelForm, "includeTokens", includeTokens);

            if (fileName) {
                addHidden(excelForm, "fileName", fileName);
            }

            excelForm.method = "post";
            excelForm.action = "/ocr/ai/history/export-with-options";

            excelForm.submit();

            // 모달 닫기
            const modalEl = document.getElementById("excelOptionModal");
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) {
                modal.hide();
            }
        });
    }
});
</script>

```
---
## 📈 9) 토큰 사용량 추적 포인트

GPT API 응답에서 제공되는 **token usage** 값을 `OcrGptResult` 테이블에 저장하여  
월별 분석, 모델별 비용 추적, 상품별 데이터 분석에 활용할 수 있습니다.

### 📌 저장되는 토큰 데이터
- **Prompt Tokens**  
  프롬프트 입력 시 사용된 토큰 수
- **Completion Tokens**  
  GPT가 생성한 응답 토큰 수
- **Total Tokens**  
  전체 사용량(Prompt + Completion)

### 📦 Excel 옵션 모달에서 "토큰 사용량 포함" 체크 시
엑셀 파일에 아래 3개의 컬럼이 함께 출력됩니다.

| Column | 설명 |
|-------|------|
| **Prompt Tokens** | 프롬프트 토큰 |
| **Completion Tokens** | 응답 토큰 |
| **Total Tokens** | 전체 토큰 |

📊 **활용 예시**
- 월별 토큰 사용량 보고서 자동 생성
- 모델별(예: GPT-3.5 / GPT-4o-mini) 비용 비교
- 특정 상품군의 AI 분석 비용 추적
- 팀 내 API 비용 회계 정산 용도

---

## 🚀 10) 확장 아이디어 (고급 기능 제안)

### 🔁 프롬프트 재사용 모달
- 특정 히스토리 선택 → "같은 조건으로 재생성"
- 기존 파라미터(모델, temperature, 프롬프트 템플릿)를 자동 채움
- 빠른 반복 실험/튜닝 가능

---

### 🆚 비교 모달
- 동일한 OCR 결과에서 생성된 SUMMARY 2개를 **좌/우 비교**
- GPT 모델/파라미터별 품질 비교에 활용
- PRD/마케팅/카피라이팅 검수에 유용

---

### 🔎 고급 필터링 + 페이징
추가할 수 있는 고급 기능:

- **결과 타입 필터** (SUMMARY / HOST_SCRIPT / MARKETING)
- **날짜/기간 검색**
- **OCR 제목 검색**
- **GPT 모델별 필터**
- **페이징 처리** → 대규모 데이터에도 안정적

---

### 📥 다운로드 포맷 확장
엑셀 외에도 아래 포맷 지원 가능:

- **CSV**
- **JSON**
- **정적 HTML 보고서**
- **PDF Export (폰트 포함 버전)**

---

### 📊 BI 도구 연동
엑셀 Export 결과를 활용하여:

- **Power BI**
- **Tableau**
- **Google Data Studio**

와 연동하면 **자동 리포트 대시보드** 구축 가능.

→ 상품군별 성능 / 토큰 사용량 / 모델별 추세를 시각적으로 분석할 수 있음.

---

## 📎 11) 관련 문서 링크

- 🔙 [메인 README](../README.md)
- 📄 [OCR 모듈 문서](./ocr-module.md)
- 🤖 [GPT AI 모듈 문서](./gpt-ai-module.md)

> ### ✍ 작성자: 김밥 (WhiteSnake-MrBBoo)