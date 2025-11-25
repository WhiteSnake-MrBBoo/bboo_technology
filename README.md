<div align="center">
  <h1>T-Commerce · JAVA · SpringBoot · OCR · GPT · Excel · Python · Backoffice</h1>

  <!-- 스택 아이콘 (뱃지 벨트) -->
  <p align="center">
    <img src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white" />
    <img src="https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=springboot&logoColor=white" />
    <img src="https://img.shields.io/badge/Tesseract-OCR-006400?logo=tesseract&logoColor=white" />
    <img src="https://img.shields.io/badge/Thymeleaf-View-005F0F?logo=thymeleaf&logoColor=white" />
    <img src="https://img.shields.io/badge/Bootstrap-5-7952B3?logo=bootstrap&logoColor=white" />
    <img src="https://img.shields.io/badge/MariaDB-Server-003545?logo=mariadb&logoColor=white" />
    <img src="https://img.shields.io/badge/OpenAI-GPT--3.5-412991?logo=openai&logoColor=white" />
    <img src="https://img.shields.io/badge/Apache%20POI-Excel-231F20?logo=apache&logoColor=white" />
  </p>

  <p>
    <!-- 다국어 지원 여유 남겨두기 -->
    <!-- <a href="README_en.md">English</a> · -->
    <a href="mailto:mrbulsapabb@gmail.com">Email</a> ·
    <a href="https://github.com/WhiteSnake-MrBBoo">GitHub</a>
  </p>
</div>

---

## 🔍 프로젝트 개요

> **T-Commerce 환경에서 상품기술서(이미지·PDF)를 OCR로 텍스트화하고,  
> GPT로 쇼호스트 멘트 / 상품 요약 / 마케팅 포인트를 생성한 뒤,  
> AI 히스토리를 DB와 Excel로 관리하는 백오피스 도구입니다.**

- 상품 기술서 원본은 **이미지(JPG/PNG) 또는 PDF(멀티 페이지)** 형태로 제공
- 서버 로컬에 설치된 **Tesseract-OCR**로 텍스트 추출 (tess4j 활용)
- 추출된 텍스트를 기반으로 **GPT-3.5-turbo**가:
    - 상품 요약(**SUMMARY**)
    - 쇼호스트 멘트(**HOST_SCRIPT**)
    - 마케팅 포인트 & 자막 문구(**MARKETING_POINTS**)를 생성
- 생성된 결과는 `ocr_gpt_result` 테이블에 저장되고,
    - **AI 히스토리 화면**에서 관리
    - **Excel Export**로 리포트 형태로 추출 가능

T-커머스/홈쇼핑에서  
“**쇼호스트 멘트·상품 요약·마케팅 포인트를 빠르게 뽑아주는 백엔드 도구**”로 사용할 수 있도록 설계했습니다.

---

## 🧩 모듈 구성

### 1) 📑 OCR 콘솔 (Tesseract-OCR + 세션 + DB 저장)

**기능 요약**

- 이미지(JPG/PNG) & PDF(멀티 페이지) 업로드
- 서버 로컬에 설치된 **Tesseract-OCR** 호출 (tess4j)
- 추출된 텍스트를 `OcrResultDto`로 **세션(HttpSession)** 에 임시 저장
- 사용자 수정 후, `ocr_result` 테이블에 **제목 + 텍스트** 저장

**핵심 포인트**

- **PDF 멀티 페이지 분기**
    - Service 레이어에서 `IMAGE(단일페이지) vs PDF(멀티 페이지)` 분기
    - PDF일 경우 각 페이지별 이미지를 생성 후 반복 OCR → 하나의 텍스트로 병합
- **세션 + DB 2단 구조**
    - 변환 직후: 세션에서 편집
    - 최종 저장: DB에 이력 남김 (`title`, `ocrText`, `editedText` 등)

> 📑 OCR 상세 설계 & 코드 흐름: [OCR Module](./docs/ocr-module.md)


https://github.com/user-attachments/assets/f1fc5ac0-3268-4e63-aad5-d7a0fcafef12


[![OCR_모듈_상세보기](https://img.shields.io/badge/OCR_모듈_상세보기-4CAF50?style=for-the-badge&logo=readme&logoColor=white)](docs/ocr-module.md)

---

### 2) 🤖 OCR + GPT AI 활용 (쇼호스트 멘트 / 요약 / 마케팅 포인트)

**기능 요약**

- `/ocr/ai` 화면에서 **좌측은 OCR 결과 리스트**, **우측은 AI 변환 패널**
- 하나의 OCR 결과에 대해 **3가지 AI 결과** 생성:
    1. **SUMMARY** – 상품 정보 요약
    2. **HOST_SCRIPT** – 쇼호스트 방송 멘트 추론
    3. **MARKETING_POINTS** – 마케팅 포인트 & 자막 문구

- 상단 탭 UI:
    - `3-1. 상품 정보 요약`
    - `3-2. 쇼호스트 멘트`
    - `3-3. 마케팅 & 자막 포인트`
- 각 탭에서 “AI 생성” 버튼 클릭 → GPT 호출 → 결과 textarea에 표시
- “이 결과 저장” 버튼 → `ocr_gpt_result` 테이블에 저장

**내부 동작**

- OpenAI는 **WebClient** 기반 호출
- `application.yml`에 모델 분리 설정 (예: summary/hostScript/marketingPoints)
- 토큰 사용량(`prompt_tokens`, `completion_tokens`, `total_tokens`)도 함께 기록

> 🤖 프롬프트 설계, 모델 설정, WebClient 호출 구조: [GPT AI Module](./docs/gpt-ai-module.md)


https://github.com/user-attachments/assets/eac7f309-2c23-478a-af91-c95d6a8d3ae5


[![GPT_AI_모듈_상세보기](https://img.shields.io/badge/GPT_AI_모듈_상세보기-9C27B0?style=for-the-badge&logo=openai&logoColor=white)](docs/gpt-ai-module.md)

---

### 3) 📊 AI 히스토리 & Excel 내보내기

**AI 히스토리 화면(`/ocr/ai/history`)**

- 좌측: `ocr_gpt_result` 리스트 (최신순)
    - 체크박스 + ID + 타입(SUMMARY/HOST_SCRIPT/...) + OCR 제목 + 파일명 + 모델 + 생성 시각
    - 행 클릭 시 → 우측 상세 패널에 내용 표시
- 우측: 선택된 결과의 전체 텍스트 미리보기
- 모달:
    - 결과 내용 확대
    - 향후 “버전 비교”, “프롬프트 재사용” 모달로 확장 예정

**Excel Export**

1. **현재 목록 전체 엑셀**
    - 현재 히스토리 리스트 기준 전체 행을 엑셀로 다운로드

2. **선택 항목 엑셀**
    - 체크박스 선택 후 “선택 항목 엑셀” → 해당 ID만 엑셀로 생성

3. **엑셀 옵션 모달**
    - 내보낼 범위:
        - ** `전체(ALL)` / `선택(SELECTED)` **
    - 포함 컬럼 옵션:
        - ID / 결과 타입 / OCR 제목 / OCR 파일명
        - 생성 시각 / 모델 / 내용 / 토큰 사용량
    - 파일명:
        - ** 기본값: `ocr_ai_history.xlsx`
        - 사용자가 직접 파일명 지정 가능

- 내부 구현: `ExcelService.writeGptHistoryToExcel(List<OcrGptResultDto>, AiHistoryExcelOptionDto, HttpServletResponse)`
    - **Apache POI**로 동적 컬럼 생성
    - 옵션 DTO 기반으로 헤더/데이터 컬럼 조절

- 📄 엑셀 옵션 DTO, ExcelService 설계, 체크박스 + 모달 연동 흐름:  
> 📊 [Excel History Module](./docs/excel-history-module.md)

https://github.com/user-attachments/assets/fbb0e71d-fd33-49fa-bad8-8624e0212d90


[![Excel_히스토리_모듈_상세보기](https://img.shields.io/badge/Excel_히스토리_모듈_상세보기-2196F3?style=for-the-badge&logo=microsoft-excel&logoColor=white)](docs/excel-history-module.md)

---

## 🧠 전체 아키텍처 개요

```text
[1] 파일 업로드 (JPG/PNG/PDF)
    └ /ocr (OCR 콘솔)
        └ OcrFacadeService
            ├ if IMAGE → Tesseract OCR (tess4j)
            └ if PDF   → 페이지 분할 → 각 페이지 OCR → 텍스트 병합
        └ 결과: OcrResultDto
            ├ HttpSession(OCR_RESULT)에 저장
            └ 사용자가 제목/텍스트 수정 후 /ocr/save → ocr_result DB 저장

[2] OCR + GPT AI 활용 (/ocr/ai)
    ├ 좌측: ocr_result 리스트
    └ 우측: 선택한 OCR 텍스트 + 3가지 AI 탭
        ├ /ocr/ai/summary   (상품 요약)
        ├ /ocr/ai/host      (쇼호스트 멘트)
        └ /ocr/ai/marketing (마케팅 포인트)
        → OpenAI WebClient 호출
        → ocr_gpt_result DB 저장

[3] AI 히스토리 & Excel (/ocr/ai/history)
    ├ 좌측: ocr_gpt_result 리스트 + 체크박스
    ├ 우측: 상세 내용 미리보기
    ├ [현재 목록 엑셀] / [선택 항목 엑셀]
    └ [엑셀 옵션 모달] → ExcelService → XSSFWorkbook → 다운로드
```
---
## ☕ Java / Spring Boot 모듈

### Backend

- Java 21
- Spring Boot 3.x
    - `spring-boot-starter-web`
    - `spring-boot-starter-thymeleaf`
    - `spring-boot-starter-data-jpa`
- ModelMapper (DTO ↔ Entity 변환)
- MariaDB + Spring Data JPA
- Lombok
- Slf4j(로깅)

### Frontend

- Thymeleaf 템플릿
- Bootstrap 5
- Vanilla JS
    - `fetch` / DOM 조작 / 이벤트 핸들링
- 일부 페이지에서 모달 / 탭 / 토스트 UI 활용

### OCR

- 로컬 설치 **Tesseract-OCR**
    - 예: `C:\Program Files\Tesseract-OCR\tessdata`
- Java 래퍼 라이브러리: **tess4j**

### Excel

- Apache POI (XSSF – `.xlsx` 포맷)

---

## 🐍 Python / Vision / STT 확장 계획

> 이 레포는 **Java Spring Boot 기반 OCR + GPT + Excel 파이프라인**에 초점을 맞추지만,  
> 실제 업무에서는 **Vision / STT / RPA / Python 기반 AI**도 함께 다루고 있습니다.

### 향후 확장 방향 (설계 수준)

- **OpenCV + YOLO** 기반 상품 이미지 분석 / 품질 체크
- **Google STT / Whisper** 기반 방송 음성 → 텍스트(STT) 변환
- **Flask/FastAPI + Docker**를 이용한  
  Java ↔ Python 마이크로서비스 연동
- 로컬 LLM / 임베딩 모델을 이용한  
  온프레미스 AI 분석 파이프라인 설계

관련 실전 경험은 별도 포트폴리오 레포에서 정리 중입니다.  
예시:

## 📊 Excel History Module

## 🤖 AI Vision 포트폴리오

[![AI Vision Portfolio](https://img.shields.io/badge/AI_Vision_포트폴리오-2196F3?style=for-the-badge&logo=microsoft-excel&logoColor=white)](https://github.com/WhiteSnake-MrBBoo/information_portfolio)

- CCTV Vision AI Dashboard
- UiPath 리뷰 요약 RPA 파이프라인
- AI Prompt Creative Pipeline 등

---

## 🎬 데모 영상 (추가 예정)

> 이후 아래 항목별로 GitHub Assets / YouTube 링크를 연결할 예정입니다.

- **OCR 콘솔**
    - 이미지/PDF 업로드 → OCR 결과 확인 → 제목 입력 → DB 저장
- **OCR + GPT AI**
    - 상품기술서 → 요약 / 쇼호스트 멘트 / 마케팅 포인트 생성
- **AI 히스토리 & Excel Export**
    - 히스토리 필터링 → 선택 → 옵션 모달 → 엑셀 다운로드

---

## 📘 Docs

> 현재는 링크 구조만 먼저 만들고,  
> 이후 단계에서 실제 내용을 채워 넣을 예정입니다.

- [`docs/ocr-module.md`](docs/ocr-module.md)
    - OCR 파이프라인 & Tesseract 연동 (작성 예정)
- [`docs/gpt-ai-module.md`](docs/gpt-ai-module.md)
    - GPT 연동 / 프롬프트 / 토큰 로깅 (작성 예정)
- [`docs/excel-history-module.md`](docs/excel-history-module.md)
    - 히스토리 & Excel Export (작성 예정)

---

## ⚙️ 실행 방법 (요약)

### 1. 필수 설치

- JDK 21
- Gradle
- MariaDB
- Tesseract-OCR (로컬 설치)
    - 예: `C:\Program Files\Tesseract-OCR\tessdata`

### 2. 환경 변수

- `OPENAI_API_KEY` : OpenAI API 키

### 3. 설정 파일 (`application.yml`)

- DB 설정
- OpenAI 설정
    - `base-url`
    - `default-model`
    - 목적별 모델명(summary/hostScript/marketingPoints 등)
- Tesseract 설정
    - 예: `tesseract.datapath` 경로


