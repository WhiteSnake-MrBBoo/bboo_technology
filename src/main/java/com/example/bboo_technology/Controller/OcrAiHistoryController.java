package com.example.bboo_technology.Controller;

import com.example.bboo_technology.DTO.AiHistoryExcelOptionDto;
import com.example.bboo_technology.DTO.OcrGptResultDto;
import com.example.bboo_technology.Service.ExcelService;
import com.example.bboo_technology.Service.Ocrservice.OcrGptResultService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@Slf4j
@Controller
@RequestMapping("/ocr/ai")
@RequiredArgsConstructor
public class OcrAiHistoryController {

    private final OcrGptResultService ocrGptResultService;
    private final ExcelService excelService;   // Excel 파일 생성 서비스

    /**
     * AI 히스토리 기본 리스트 + 우측 상세 패널
     *
     * @param id 선택된 히스토리 ID (옵션)
     */
    @GetMapping("/history")
    public String showAiHistory(
            @RequestParam(name = "id", required = false) Long id,
            Model model
    ) {
        // 1) 전체 히스토리 목록 조회
        List<OcrGptResultDto> historyList =
                ocrGptResultService.findAllOrderByCreatedAtDesc();

        model.addAttribute("historyList", historyList);

        // 2) 우측에 표시할 선택된 항목 결정
        OcrGptResultDto selected = resolveSelectedHistory(historyList, id);
        model.addAttribute("selectedHistory", selected);

        log.info("AI History 화면 요청 - id={}, listSize={}", id, historyList.size());

        // 템플릿: templates/ocr/ocr_ai_excel.html
//        return "ocr/ocr_ai_history";
        return "ocr/ocr_ai_excelgoogl";
    }

    /**
     * 엑셀 구현 1단계: 현재 AI 히스토리 전체를 엑셀로 다운로드.
     *
     * - GET /ocr/ai/history/export
     * - 현재는 필터 없이 전체 목록 기준 (findAllOrderByCreatedAtDesc)
     * - 나중에 타입/기간 필터 추가 시, 동일 조건의 리스트를 넘기면 된다.
     */
    @GetMapping("/history/export")
    public void exportAiHistory(HttpServletResponse response) {

        List<OcrGptResultDto> historyList =
                ocrGptResultService.findAllOrderByCreatedAtDesc();

        log.info("AI 히스토리 엑셀 다운로드 요청 (전체) - count={}", historyList.size());

        excelService.writeGptHistoryToExcel(historyList, response);
    }

    /**
     * 엑셀 구현 2단계: 체크박스로 선택한 AI 히스토리만 엑셀로 다운로드.
     *
     * - POST /ocr/ai/history/export-selected
     * - 파라미터: ids=1&ids=3&ids=5 형식으로 여러 개 넘어옴
     */
    @PostMapping("/history/export-selected")
    public void exportSelectedAiHistory(
            @RequestParam("ids") List<Long> ids,
            HttpServletResponse response
    ) {
        log.info("선택 항목 엑셀 다운로드 요청 - ids={}", ids);

        if (ids == null || ids.isEmpty()) {
            // JS에서 선행 체크를 하지만, 방어 코드 차원에서 한 번 더 검증
            throw new IllegalArgumentException("선택된 항목이 없습니다.");
        }

        List<OcrGptResultDto> selectedList =
                ocrGptResultService.findByIds(ids);

        log.info("선택 항목 엑셀 다운로드 - 대상 건수={}", selectedList.size());

        excelService.writeGptHistoryToExcel(selectedList, response);
    }
    /**
     * ocr_ai_excel.html modal : 선택 영역별 컬럼 엑셀로 내보기
     * */
// Excel 옵션 기반 다운로드
    @PostMapping("/history/export-with-options")
    public void exportAiHistoryWithOptions(
            @ModelAttribute AiHistoryExcelOptionDto option,
            HttpServletResponse response
    ) {
        // 옵션 로그로 먼저 확인
        log.info("엑셀 옵션 요청 - scope={}, selectedIds={}, fileName={}",
                option.getScope(), option.getSelectedIds(), option.getFileName());

        // 1) 내보낼 데이터 결정
        List<OcrGptResultDto> data;

        // scope == "SELECTED" 이고, 선택 ID가 하나라도 있으면 → 그 ID들만 조회
        if ("SELECTED".equalsIgnoreCase(option.getScope())
                && option.getSelectedIds() != null
                && !option.getSelectedIds().isEmpty()) {

            data = ocrGptResultService.findByIds(option.getSelectedIds());

        } else {
            // 그 외에는 현재는 "전체 목록" 기준
            data = ocrGptResultService.findAllOrderByCreatedAtDesc();
        }

        log.info("엑셀 내보내기 대상 건수 = {}", data.size());

        // 2) 엑셀 생성 서비스 호출 (옵션 + 데이터 전달)
        excelService.writeGptHistoryToExcel(data, option, response);
    }





    // ====================================================
    // private helper 메서드
    // ====================================================

    /**
     * 히스토리 리스트와 id 파라미터를 기준으로
     * 우측 패널에 보여줄 선택된 항목을 결정한다.
     *
     * - 리스트가 비었으면 null
     * - id가 존재하면 해당 ID 우선
     * - id가 없거나 못 찾으면 가장 최근 1건(historyList[0])
     */
    private OcrGptResultDto resolveSelectedHistory(List<OcrGptResultDto> historyList, Long id) {

        if (historyList == null || historyList.isEmpty()) {
            return null;
        }

        // id 파라미터가 있는 경우: 해당 ID 우선
        if (id != null) {
            for (OcrGptResultDto dto : historyList) {
                if (id.equals(dto.getId())) {
                    return dto;
                }
            }
            // 못 찾으면 fall-back: 첫 번째 (최신)
            return historyList.get(0);
        }

        // id 파라미터가 없으면: 가장 최근 1건
        return historyList.get(0);
    }
}
