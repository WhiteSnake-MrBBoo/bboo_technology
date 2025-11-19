package com.example.bboo_technology.Controller;

import com.example.bboo_technology.DTO.OcrResultDto;
import com.example.bboo_technology.Service.OcrFacadeService;
import com.example.bboo_technology.Service.OcrResultService;
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

    /**
     * 세션에 저장할 때 사용할 키 값 상수.
     * - 하드코딩 문자열 대신 상수로 관리하여 오타 및 중복을 방지한다.
     */
    private static final String SESSION_KEY_OCR_RESULT = "OCR_RESULT";

    private final OcrFacadeService ocrFacadeService;
    private final OcrResultService ocrResultService;
    // private final TranslationService translationService; // 번역 연동 시 주입 예정

    /**
     * 1) OCR 콘솔 초기 화면 진입
     *
     * - 사용자가 /ocr 로 GET 요청 시 호출된다.
     * - 세션에 이미 진행 중인 OCR 작업(OcrResultDto)이 있다면 Model 에 올려서 View 에서 그대로 렌더링한다.
     *   (예: 새로고침 또는 다른 페이지 다녀온 경우에도 작업 상태 유지)
     */
    @GetMapping
    public String showOcrConsole(Model model, HttpSession session) {

        // 세션에 기존 OCR_RESULT 가 있으면 꺼내서 모델에 전달
        Object sessionObj = session.getAttribute(SESSION_KEY_OCR_RESULT);
        if (sessionObj instanceof OcrResultDto ocrResultDto) {
            model.addAttribute("ocrResult", ocrResultDto);
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
            redirectAttributes.addFlashAttribute("errorMessage", "세션 정보가 만료되었거나 잘못된 접근입니다. 다시 파일을 업로드해 주세요.");
            return "redirect:/ocr";
        }

        // 2. 제목/텍스트 검증 (필요 시 추가 검증 로직 확장 가능)
        if (title == null || title.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "저장할 제목을 입력해 주세요.");
            return "redirect:/ocr";
        }
        if (ocrText == null || ocrText.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "저장할 텍스트 내용이 비어 있습니다.");
            return "redirect:/ocr";
        }

        // 3. 세션 DTO 업데이트
        ocrResultDto.setTitle(title.trim());
        ocrResultDto.setEditedText(ocrText);

        try {
            // 4. DB 저장 서비스 호출
            //    - 구체 구현은 다음 단계에서 진행
            ocrResultService.saveOcrResult(ocrResultDto);

            // 5. 저장 완료 표시
            ocrResultDto.setSaved(true);

            // (선택) 세션에서 제거하거나, 저장 완료 상태로 유지할지 정책에 따라 결정
            // 여기서는 임시 작업이 끝났다고 보고 제거하는 예시
            session.removeAttribute(SESSION_KEY_OCR_RESULT);

            redirectAttributes.addFlashAttribute("infoMessage", "OCR 결과가 성공적으로 저장되었습니다.");

        } catch (Exception e) {
            log.error("OCR 결과 저장 중 오류 발생", e);
            redirectAttributes.addFlashAttribute("errorMessage", "OCR 결과 저장 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
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


}
