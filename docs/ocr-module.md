# 📘 OCR Module – (Tesseract-OCR + Spring Boot) 상세 아키텍처 & 흐름

> 본 문서는 OCR 모듈의 **전체 구조 / 아키텍처 / 코드 흐름 / 핵심 포인트**를 정리한 상세 문서입니다.  
> README.md 메인 → 이 문서로 링크되며, 다른 문서(GPT / Excel 모듈)와 연결됩니다.

---

# 🧩 1. 전체 구조 요약

```text
이미지(PNG/JPG) / PDF 업로드
↓
Tesseract-OCR로 텍스트 추출
↓
세션(Session)에 OCR 결과 저장
↓
사용자가 제목 입력 후 DB 저장 (OcrResult 테이블)
↓
OCR + GPT 페이지에서 AI 추론 기능에 활용

```

---

# 🏗 2. 시스템 아키텍처 (OCR 부분)

```text
[Client Browser]
     │
     │ 이미지/PDF 업로드 (multipart/form-data)
     ▼
[Spring Controller]  ──────▶ OCRService
     │                        │
     │                        ▼
     │                Tesseract (tess4j)
     │                        │
     ▼                        ▼
[OCR 결과 화면]  ◀── 세션 저장 ── 텍스트 추출
     │
     ▼
DB 저장 버튼 → OcrResultRepository.save()
```
---
| 영역           | 상세                              |
| ------------ | ------------------------------- |
| OCR Engine   | **Tesseract-OCR (로컬 설치)**       |
| Java Wrapper | **tess4j 5.x**                  |
| Backend      | Spring Boot 3.x, Java 21        |
| DB           | MariaDB                         |
| Session      | Spring Session (기본 HttpSession) |
| View         | Thymeleaf + Bootstrap           |

---
## 📂 4. Tesseract-OCR 설치
### Windows 설치 경로 예시
```text
C:\Program Files\Tesseract-OCR\
```
### tessdata 경로
```text
C:\Program Files\Tesseract-OCR\tessdata
```

### application.yml 설정
```yaml
tesseract:
  datapath: "C:/Program Files/Tesseract-OCR/tessdata"
  language: "kor+eng"

```
---
## 🧪 5. OCR 업로드 화면 (예시 UI)

- 사용자는 다음 흐름으로 OCR을 실행할 수 있음:

1. 이미지 업로드
2. 텍스트 자동 추출
3. 텍스트 수정 가능
4. 제목 입력 후 DB 저장

---
## 🛠️ 6. OCR 컨트롤러 흐름
- OcrController.java
```java
@PostMapping("/upload")
public String uploadOcrFile(
        @RequestParam("file") MultipartFile file,
        HttpSession session,
        Model model
) {
    // 1) 파일 저장
    String savedPath = fileStorageService.saveOcrFile(file);

    // 2) OCR 실행
    String ocrText = ocrService.extractText(savedPath);

    // 3) 세션 저장
    session.setAttribute("ocrText", ocrText);
    session.setAttribute("ocrFileName", file.getOriginalFilename());

    model.addAttribute("ocrText", ocrText);
    model.addAttribute("fileName", file.getOriginalFilename());

    return "ocr/ocr_console";
}

```
---
## 🔍 7. OCR 텍스트 추출 서비스
- OcrServiceImpl.java
```java
@Override
public String extractText(String filePath) {
    try {
        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(tesseractDataPath); // application.yml 과 매핑
        tesseract.setLanguage("kor+eng");

        return tesseract.doOCR(new File(filePath));

    } catch (Exception e) {
        log.error("OCR 실패", e);
        throw new RuntimeException("OCR 중 오류가 발생했습니다.");
    }
}

```
---
## 🗄️ 8. DB 저장 구조 (OcrResult 테이블)
- ### 테이블 구조 요약
| 컬럼명                | 설명              |
| ------------------ | --------------- |
| `id`               | PK              |
| `title`            | 사용자가 입력한 제목     |
| `originalFileName` | OCR 파일 명        |
| `editedText`       | 사용자가 수정한 OCR 결과 |
| `fileType`         | pdf/jpg/png     |
| `createdAt`        | 생성 시각           |

- ### 저장처리 : OcrController.java
```java
@PostMapping("/save")
public String saveOcrResult(
        @RequestParam("title") String title,
        HttpSession session,
        RedirectAttributes redirect
) {
    String text = (String) session.getAttribute("ocrText");
    String fileName = (String) session.getAttribute("ocrFileName");

    OcrResult result = ocrResultService.save(title, fileName, text);

    redirect.addFlashAttribute("message", "OCR 결과가 저장되었습니다.");
    return "redirect:/ocr/ai?id=" + result.getId();
}

```
---
# 🔄 9. OCR 결과 → GPT 추론 연결

- ### OCR 결과는 다음 페이지에서 GPT 프롬프트의 원본 텍스트로 사용됨:
```text
/ocr/ai?id={ocrResultId}

```
- GPT 모듈에서 조회 시:
```java
OcrResult selectedOcr = ocrResultRepository.findById(id).orElseThrow();
String text = selectedOcr.getEditedText();


```
- 이 텍스트가 SUMMARY / HOST_SCRIPT / MARKETING 포인트 생성에 사용됨.
---
## 🌟 10. 핵심 포인트 정리
* ### ✔ 로컬 Tesseract-OCR + Java tess4j 래퍼
- GPU 없이도 안정적으로 OCR 구현 가능

* ### ✔ 이미지 & PDF 모두 지원
- tess4j 내부에서 PDF 처리 자동 지원

* ### ✔ 세션 기반 임시 저장 → DB 저장 구조
- 사용자가 OCR 결과를 수정할 수 있음

* ### ✔ GPT 추론 모듈과 자연스럽게 연결되는 OCR 파이프라인
---

## 🔗 11. 관련 문서 링크
[![🔙 메인 README.md](https://github.com/WhiteSnake-MrBBoo/bboo_technology/pulls)]


🔙 메인 README.md

🤖 GPT 모듈: docs/gpt-ai-module.md

📊 Excel Export 모듈: docs/excel-history-module.md

---
📌 12. TODO (향후 개선점)

Whisper 기반 STT와 OCR 결과 통합

비정형 문서(표/영수증) 구조화 OCR

PDF → 이미지 분할 OCR 최적화

Vision + OCR 융합 파이프라인 설계

---

작성자: 김밥 (WhiteSnake-MrBBoo)




