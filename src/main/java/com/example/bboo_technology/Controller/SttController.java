package com.example.bboo_technology.Controller;

import com.example.bboo_technology.Service.Sttservice.SttService;
import com.example.bboo_technology.DTO.Stt.SttResponseDto;
import com.example.bboo_technology.DTO.Stt.SttWebResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

// (추가) STT 파일 업로드/변환 컨트롤러
@Slf4j
@Controller
@RequiredArgsConstructor
@RequestMapping("/api/stt")
public class SttController {

    // (추가) 파일 → STT 변환 비즈니스 로직 담당 서비스
    private final SttService sttService;

    /**
     * (추가) STT 파일 업로드 테스트용 페이지
     *
     * - URL   : GET /api/stt/file
     * - View  : templates/stt/stt-upload.html (예시)
     * - 용도  : 브라우저에서 오디오 파일 선택 → POST /api/stt/file 호출 테스트
     *
     * TODO:
     *  - stt-upload.html 파일을 생성해서
     *    <form method="post" enctype="multipart/form-data" action="/api/stt/file">
     *      <input type="file" name="file" />
     *      <input type="text" name="sessionId" />
     *      <input type="text" name="language" />
     *      <button type="submit">업로드</button>
     *    </form>
     *    이런 식으로 간단히 구성하면, 바로 브라우저에서 테스트 가능
     */
    @GetMapping("/file")
    public String showSttUploadPage() {
        // (주의) 여기서는 그냥 뷰 이름만 반환 (로직 없음)
        return "stt/stt-upload"; // TODO: 실제 템플릿 경로에 맞게 수정
    }

    /**
     * (추가) 업로드된 오디오 파일을 STT 엔진으로 변환하는 API 엔드포인트
     *
     * - URL   : POST /api/stt/file
     * - Form  :
     *      - file      (필수) : 오디오 파일 (wav, mp3 등)
     *      - sessionId (옵션) : 방송/세션 식별자 (없으면 내부에서 생성)
     *      - language  (옵션) : 언어 힌트 (예: "ko", "en" / null 이면 auto)
     * - 응답  :
     *      - HTTP Status : STT 결과/에러 코드에 따라 동적
     *      - Body(JSON) : SttResponseDto
     *
     * TODO:
     *  - REST API만 쓸 거라면 @RestController 로 바꿀 수도 있음.
     *  - 뷰로 리턴해야 한다면, 별도 엔드포인트(/view 등)를 추가해서
     *    ResponseEntity 대신 ModelAndView로 구성하는 것도 가능.
     */


    @PostMapping("/file")
    public ResponseEntity<SttResponseDto> transcribeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam(value = "language", required = false) String languageHint
    ) {

        // =============================
        // 1. 요청 기본 로그
        // =============================
        log.info("[STT-CTRL] /api/stt/file 호출 - fileName={}, sessionId={}, language={}",
                safeFileName(file),
                sessionId,
                languageHint);

        // =============================
        // 2. 서비스 호출 (파일 → STT 웹 응답)
        //    - 비즈니스/에러/HTTP 상태 결정은 전부 서비스에 위임
        // =============================
        SttWebResponse webResponse = sttService.transcribeFileForWeb(file, sessionId, languageHint);

        // =============================
        // 3. 컨트롤러는 단순히 HTTP 응답 포장만 수행
        // =============================
        return new ResponseEntity<>(webResponse.getBody(), webResponse.getHttpStatus());
    }

    // =============================
    // [Private Helper Methods]
    //  - 가독성/유지보수용 공통 로직 분리
    // =============================

    // (추가) MultipartFile 이 null 이거나 이름이 없을 때 NPE 방지용
    private String safeFileName(MultipartFile file) {
        if (file == null) {
            return "null";
        }
        String name = file.getOriginalFilename();
        return (name != null) ? name : "unknown";
    }
}
