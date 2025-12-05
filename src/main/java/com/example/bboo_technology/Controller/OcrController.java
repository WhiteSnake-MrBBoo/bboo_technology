package com.example.bboo_technology.Controller;

import com.example.bboo_technology.Config.OpenAiConfig;

//DTO import 부분
import com.example.bboo_technology.DTO.OcrResultDto;
import com.example.bboo_technology.DTO.OcrGptResultDto;

import com.example.bboo_technology.DTO.TranslationDto;
import com.example.bboo_technology.Service.Ocrservice.OcrAiGptService;
import com.example.bboo_technology.Service.Ocrservice.OcrFacadeService;
import com.example.bboo_technology.Service.Ocrservice.OcrGptResultService;
import com.example.bboo_technology.Service.Ocrservice.OcrResultService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;

/*Model 임포트는 무조건 이거 써야함 - Spring MVC에서 Controller → View(Thymeleaf 등) 로 데이터 넘길 때 쓰는 그 Model*/
import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

/**
 * OCR 콘솔 페이지에 대한 메인 컨트롤러.
 * - GET /ocr           : OCR 콘솔 화면 진입
 * - POST /ocr/upload   : 파일 업로드 + OCR 수행 요청
 * - POST /ocr/save     : 제목 + 수정된 텍스트를 DB에 저장
 * - POST /ocr/translate: (향후) 텍스트 번역 요청 처리
 * <p>
 * 중요한 포인트:
 * - 세션(HttpSession)에 "OCR_RESULT" 라는 키로 OcrResultDto 를 통째로 저장/조회한다.
 * - 컨트롤러는 비즈니스 로직(OCR, 번역, DB 저장)을 직접 수행하지 않고 Service 에 위임한다.
 */
@Slf4j
@Controller
@RequestMapping("/ocr")
@RequiredArgsConstructor

public class OcrController {

    /* OCR - AI 변환용 상수 적의
    * SUMMARY(상품내용요약) / HOST_SCRIPT(쇼호스트멘트정의) / MARKETING_POINTS(썸네일 키워드 저의)
    * */
    // GPT 결과 타입 상수
    private static final String GPT_TYPE_SUMMARY          = "SUMMARY";
    private static final String GPT_TYPE_HOST_SCRIPT      = "HOST_SCRIPT";
    private static final String GPT_TYPE_MARKETING_POINTS = "MARKETING_POINTS";


    /**
     * 세션에 저장할 때 사용할 키 값 상수.
     * - 하드코딩 문자열 대신 상수로 관리하여 오타 및 중복을 방지한다.
     */
    private static final String SESSION_KEY_OCR_RESULT = "OCR_RESULT";

    // 번역 정보 같은 URL 에 값 넣어 주기 위한 상수 키 값
    private static final String SESSION_KEY_OCR_TRANSLATION = "OCR_TRANSLATION";


    /** Open AI 추론 서비스 주입용 interface */
    private final OcrAiGptService ocrAiGptService;

    private final OcrGptResultService ocrGptResultService;
    private final OpenAiConfig openAiConfig;

    /** Multifile 인터페이스 주입 */
    private final OcrFacadeService ocrFacadeService;

    /**Ocr용 인터페이스*/
    private final OcrResultService ocrResultService;
    // private final TranslationService translationService; // 번역 연동 시 주입 예정

    /**
     * 1) OCR 콘솔 초기 화면 진입
     * - 사용자가 /ocr 로 GET 요청 시 호출된다.
     * - 세션에 이미 진행 중인 OCR 작업(OcrResultDto)이 있다면 Model 에 올려서 View 에서 그대로 렌더링한다.
     * (예: 새로고침 또는 다른 페이지 다녀온 경우에도 작업 상태 유지)
     */
    @GetMapping("/ppal")
    public String ppal() {
        return "ocr/public_global"; // templates/ocr/ppublic_global.html
    }


    @GetMapping
    public String showOcrConsole(Model model, HttpSession session) {

        // 1) OCR 결과 세션 → 모델 :  OCR_RESULT 가 있으면 꺼내서 모델에 전달
        Object sessionObj = session.getAttribute(SESSION_KEY_OCR_RESULT);
        if (sessionObj instanceof OcrResultDto ocrResultDto) {
            model.addAttribute("ocrResult", ocrResultDto);
        }

        // 2) 번역 결과 세션 → 모델
        Object translationObj = session.getAttribute(SESSION_KEY_OCR_TRANSLATION);
        if (translationObj instanceof TranslationDto translationDto) {
            model.addAttribute("translation", translationDto);
        }

        // 뷰 파일: templates/ocr/ocr_console.html
        return "ocr/ocr_console";
    }

    /**
     * 2) 파일 업로드 + OCR 수행 요청
     *
     * - View 에서 "OCR 실행" 버튼 클릭 시 multipart/form-data 로 파일이 넘어온다.
     * - 이 메서드는 단순히:
     *   1) 업로드 파일 검증(널, 빈 파일, 크기 제한 등)
     *   2) OcrFacadeService 에 OCR 처리를 위임
     *   3) 결과 OcrResultDto 를 세션 + Model 에 저장
     *   역할만 수행한다.
     */
    @PostMapping("/upload")
    public String uploadAndOcr(@RequestParam("file") MultipartFile file,
                               Model model,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {

        // 1. 업로드 파일 기본 검증
        if (file == null || file.isEmpty()) {
            // RedirectAttributes 를 사용하면 리다이렉트 후에도 1회성 메시지 전달 가능
            redirectAttributes.addFlashAttribute("errorMessage", "업로드할 파일을 선택해 주세요.");
            return "redirect:/ocr";
        }

        try {
            // 2. OcrFacadeService 를 통해 파일 타입 분기 + OCR 수행
            //    (구체 구현은 다음 단계에서 진행)
            OcrResultDto ocrResultDto = ocrFacadeService.extractText(file);

            // 3. 세션에 OCR 결과 저장
            //    - 이후 "저장하기", "번역" 등에서 재사용할 수 있게 한다.
            session.setAttribute(SESSION_KEY_OCR_RESULT, ocrResultDto);

            // ✅ (중요) 새 파일을 업로드했으므로, 이전 번역 결과는 초기화
//            session.removeAttribute(SESSION_KEY_OCR_TRANSLATION);

            // 4. Model 에도 담아서 즉시 View 에 렌더링
            model.addAttribute("ocrResult", ocrResultDto);

        } catch (Exception e) {
            // (중요) OCR 처리 중 예외 발생 시 사용자에게 안내
            log.error("파일 OCR 처리 중 오류 발생", e);
            redirectAttributes.addFlashAttribute("errorMessage", "파일 OCR 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
            return "redirect:/ocr";
        }

        // 업로드 & OCR 완료 후에도 같은 화면(ocr_console.html)을 재사용
        return "ocr/ocr_console";
    }

    /**
     * 3) OCR 결과 저장 요청
     *
     * - View 오른쪽 패널에서 "저장할 제목" + "OCR 텍스트(수정본)" 을 입력 후 "저장하기" 클릭 시 호출된다.
     * - 핵심 흐름:
     *   1) 세션에서 OcrResultDto 를 꺼낸다. (없으면 세션 만료로 판단)
     *   2) 파라미터로 받은 제목(title)과 텍스트(ocrText)를 DTO 에 반영
     *   3) OcrResultService 에게 DB 저장을 위임
     *   4) 저장 성공 시 세션의 임시 데이터 제거(선택), 성공 메시지 반환
     */

    @PostMapping("/save")
    public String saveOcrResult(@RequestParam("title") String title,
                                @RequestParam("ocrText") String ocrText,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {

        // 1. 세션에서 OCR_RESULT 가져오기
        Object sessionObj = session.getAttribute(SESSION_KEY_OCR_RESULT);
        if (!(sessionObj instanceof OcrResultDto ocrResultDto)) {
            // 세션 만료 또는 잘못된 접근
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "세션 정보가 만료되었거나 잘못된 접근입니다. 다시 파일을 업로드해 주세요."
            );
            return "redirect:/ocr";
        }

        // 1-1. (선택) 세션에서 번역 결과도 가져오기
        //      - 번역이 아직 안 되었으면 null 이어도 상관 없음
        Object transObj = session.getAttribute(SESSION_KEY_OCR_TRANSLATION);
        TranslationDto translationDto = null;
        if (transObj instanceof TranslationDto t) {
            translationDto = t;
        }

        // 2. 제목/텍스트 검증
        String trimmedTitle = (title != null) ? title.trim() : "";
        String trimmedOcrText = (ocrText != null) ? ocrText.trim() : "";

        if (trimmedTitle.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "저장할 제목을 입력해 주세요.");
            // ❗ 검증 실패 시 세션은 건드리지 않는다 → OCR/번역 내용 그대로 유지
            return "redirect:/ocr";
        }

        if (trimmedOcrText.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "저장할 텍스트 내용이 비어 있습니다.");
            // ❗ 검증 실패 시 세션은 건드리지 않는다
            return "redirect:/ocr";
        }

        // 3. 세션 OCR DTO 업데이트 (사용자 입력 반영)
        ocrResultDto.setTitle(trimmedTitle);
        ocrResultDto.setEditedText(trimmedOcrText);

        try {
            // 4. OCR 결과 DB 저장
            //    - 기존에 사용하던 서비스 로직 그대로
            ocrResultService.saveOcrResult(ocrResultDto);

            // 4-1. (향후용) 번역 결과 처리 자리
            //      - 지금은 번역 테이블/엔티티를 아직 결정하지 않았으므로
            //        단순히 로그만 남기고, 나중에 TranslationEntity 만들면
            //        이 위치에서 TranslationService.save(...) 호출만 추가하면 된다.
            if (translationDto != null) {
                log.info("번역 세션 데이터 감지 - 향후 Translation DB 저장 시 사용 예정. sourceLang={}, targetLang={}, engine={}",
                        translationDto.getSourceLang(),
                        translationDto.getTargetLang(),
                        translationDto.getEngine()
                );

                // TODO:
                //  - TranslationEntity / TranslationService 설계가 결정되면
                //    아래와 같은 형태로 OCR 결과와 함께 저장을 수행한다.
                //    translationService.saveTranslation(ocrResultDto, translationDto);
            }

            // 5. 저장 완료 플래그
            ocrResultDto.setSaved(true);

            // 6. 저장 "성공" 시에만 세션 정리
            session.removeAttribute(SESSION_KEY_OCR_RESULT);
            session.removeAttribute(SESSION_KEY_OCR_TRANSLATION);

            redirectAttributes.addFlashAttribute("infoMessage", "OCR 결과가 성공적으로 저장되었습니다.");

        } catch (Exception e) {
            log.error("OCR 결과 저장 중 오류 발생", e);
            // ❗ 예외 발생 시에도 세션은 그대로 유지 → 사용자가 작성한 내용/번역 보존
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "OCR 결과 저장 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
            );
        }

        return "redirect:/ocr";
    }


    /**
     * 4) (향후) 텍스트 번역 요청 처리 엔드포인트
     *
     * - 아직 번역 API 연동 전이므로, Response 형태와 기본 골격만 정의해 둔다.
     * - 실제 구현 시에는 TranslationService 를 호출하여 번역 결과를 반환하게 된다.
     *
     * 예상 사용 방식:
     * - Front에서 AJAX/fetch 로 /ocr/translate 에 POST
     * - 요청 파라미터: text, sourceLang, targetLang 등
     * - 응답: { translatedText: "번역된 문자열..." }
     */
    @PostMapping("/translate")
    @ResponseBody
    public Map<String, Object> translateText(@RequestParam("text") String text,
                                             @RequestParam("source") String sourceLang,
                                             @RequestParam("target") String targetLang) {
        Map<String, Object> response = new HashMap<>();

        // (현재는 구현 전이므로 안내 메시지만 반환)
        response.put("success", false);
        response.put("message", "번역 기능은 추후 API 연동 후 활성화될 예정입니다.");
        response.put("translatedText", text); // 임시로 원문 그대로 반환

        // 실제 구현 시 예:
        // String translated = translationService.translate(text, sourceLang, targetLang);
        // response.put("success", true);
        // response.put("translatedText", translated);

        return response;
    }


    /**
     * 5) OCR AI 활용 페이지
     * - GET /ocr/ai : ocr_result DB 값 리스트로 가져 오기
     * - 파라미터로 선택한 OCR 결과의 ID를 받을 수 있다. (예: /ocr/ai?id=3)
     * - 기본 흐름:
     *   1) 저장된 OCR 결과 전체 리스트를 조회하여 좌측 문서 목록에 표시
     *   2) 선택된 ID가 있으면 해당 문서를 우측 패널에 표시
     *      없으면 (또는 잘못된 ID면) 첫 번째 문서를 기본 선택
     */
    @GetMapping("/ai")
    public String ocrAiPage(@RequestParam(value = "id", required = false) Long id,
                            Model model) {

        // 1. 전체 OCR 결과 목록 조회 (좌측 리스트 용도)
        //    TODO: OcrResultServiceImpl 에서 findAll() 구현 필요
        var ocrList = ocrResultService.findAll();
        model.addAttribute("ocrList", ocrList);

        // 2. 우측 패널에 표시할 "선택된 문서" 결정 : 초기값 설정
        OcrResultDto selected = null;

        if (id != null) {
            // ID 파라미터가 있을 때: 해당 ID를 기준으로 조회 시도
            selected = ocrResultService.findById(id);
        }

        if (selected == null && !ocrList.isEmpty()) {
            // 3. ID가 없거나 잘못된 경우, 또는 findById 결과가 null 인 경우
            //    → 목록의 첫 번째 문서를 기본 선택
            selected = ocrList.get(0);
        }

        // 4. 선택된 문서를 Model 에 담아서 View 에 전달
        model.addAttribute("selectedOcr", selected);

        // 뷰 파일: templates/ocr/ocr_ai.html
        return "ocr/ocr_ai";
    }

    // ========== 6) OCR AI 활용 - 더미 요약/멘트/마케팅 엔드포인트 ==========

    /**
     * 6-1) 상품 정보 요약 생성 (더미 버전)
     *
     * - POST /ocr/ai/summary
     * - 파라미터: id (선택된 OcrResult의 PK)
     * - 역할:
     *   1) 해당 OCR 결과를 조회
     *   2) editedText 일부를 잘라서 "요약된 것처럼" 더미 텍스트 생성
     *   3) JSON 형태로 반환 (success, content)
     *
     *  ※ 추후 여기에서 WebClient + OpenAI 호출로 교체할 예정
     */
    @PostMapping("/ai/summary")
    @ResponseBody
    public Map<String, Object> generateSummary(@RequestParam("id") Long ocrResultId) {
        Map<String, Object> response = new HashMap<>();

        try {
            OcrResultDto dto = ocrResultService.findById(ocrResultId);
            if (dto == null) {
                response.put("success", false);
                response.put("message", "해당 ID의 OCR 문서를 찾을 수 없습니다.");
                return response;
            }

            // (수정) 더미 대신 실제 GPT 서비스 호출
            String resultText = ocrAiGptService.generateSummary(dto);

            response.put("success", true);
            response.put("content", resultText);

        } catch (Exception e) {
            log.error("AI 요약 생성 중 오류 발생", e);
            response.put("success", false);
            response.put("message", "AI 요약 생성 중 오류가 발생했습니다.");
        }

        return response;
    }

    /**
     * 6-2) 쇼호스트 멘트 생성 (더미 버전)
     */
    @PostMapping("/ai/host")
    @ResponseBody
    public Map<String, Object> generateHostScript(@RequestParam("id") Long ocrResultId) {
        Map<String, Object> response = new HashMap<>();

        try {
            OcrResultDto dto = ocrResultService.findById(ocrResultId);
            if (dto == null) {
                response.put("success", false);
                response.put("message", "해당 ID의 OCR 문서를 찾을 수 없습니다.");
                return response;
            }

            String resultText = ocrAiGptService.generateHostScript(dto);

            response.put("success", true);
            response.put("content", resultText);

        } catch (Exception e) {
            log.error("AI 쇼호스트 멘트 생성 중 오류 발생", e);
            response.put("success", false);
            response.put("message", "AI 쇼호스트 멘트 생성 중 오류가 발생했습니다.");
        }

        return response;
    }


    /**
     * 6-3) 마케팅 포인트 & 자막 문구 생성 (더미 버전)
     */
    @PostMapping("/ai/marketing")
    @ResponseBody
    public Map<String, Object> generateMarketingPoints(@RequestParam("id") Long ocrResultId) {
        Map<String, Object> response = new HashMap<>();

        try {
            OcrResultDto dto = ocrResultService.findById(ocrResultId);
            if (dto == null) {
                response.put("success", false);
                response.put("message", "해당 ID의 OCR 문서를 찾을 수 없습니다.");
                return response;
            }

            String resultText = ocrAiGptService.generateMarketingPoints(dto);

            response.put("success", true);
            response.put("content", resultText);

        } catch (Exception e) {
            log.error("AI 마케팅 포인트 생성 중 오류 발생", e);
            response.put("success", false);
            response.put("message", "AI 마케팅 포인트 생성 중 오류가 발생했습니다.");
        }

        return response;
    }


    /**
     * 7) AI 결과 저장 엔드포인트
     *
     * - 요청 파라미터:
     *   - id      : OCR 결과 ID (ocr_result.id)
     *   - type    : 결과 타입 (SUMMARY / HOST_SCRIPT / MARKETING_POINTS)
     *   - content : 저장할 AI 텍스트 (현재 textarea에 표시된 내용)
     *
     * - 응답:
     *   { success: true/false, message: "...", id: 저장된 GPT 결과 PK }
     */
    @PostMapping("/ai/save")
    @ResponseBody
    public Map<String, Object> saveAiResult(@RequestParam("id") Long ocrResultId,
                                            @RequestParam("type") String resultType,
                                            @RequestParam("content") String content) {

        Map<String, Object> response = new HashMap<>();

        try {
            // 1) 기본 검증
            if (ocrResultId == null) {
                response.put("success", false);
                response.put("message", "OCR 문서 ID가 전달되지 않았습니다.");
                return response;
            }

            if (resultType == null || resultType.isBlank()) {
                response.put("success", false);
                response.put("message", "결과 타입이 전달되지 않았습니다.");
                return response;
            }

            if (content == null || content.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "저장할 내용이 비어 있습니다.");
                return response;
            }

            // 2) 타입별로 사용할 모델 선택
            String model;
            switch (resultType) {
                case GPT_TYPE_SUMMARY -> model = openAiConfig.getSummaryModel();
                case GPT_TYPE_HOST_SCRIPT -> model = openAiConfig.getHostScriptModel();
                case GPT_TYPE_MARKETING_POINTS -> model = openAiConfig.getMarketingPointsModel();
                default -> {
                    response.put("success", false);
                    response.put("message", "알 수 없는 결과 타입입니다: " + resultType);
                    return response;
                }
            }

            Double temperature = openAiConfig.getDefaultTemperature();

            // 3) DTO 구성
            OcrGptResultDto dto = OcrGptResultDto.builder()
                    .ocrResultId(ocrResultId)
                    .resultType(resultType)
                    .content(content)
                    .model(model)
                    .temperature(temperature)
                    // 토큰 사용량은 아직 usage 파싱 안 하므로 null로 남겨둠
                    .build();

            // 4) 서비스 호출하여 DB 저장
            OcrGptResultDto saved = ocrGptResultService.saveResult(dto);

            response.put("success", true);
            response.put("id", saved.getId());
            response.put("createdAt", saved.getCreatedAt());
            response.put("message", "AI 결과가 성공적으로 저장되었습니다.");

        } catch (Exception e) {
            log.error("AI 결과 저장 중 예외 발생 - ocrResultId={}, type={}", ocrResultId, resultType, e);
            response.put("success", false);
            response.put("message", "AI 결과 저장 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }

        return response;
    }






}
