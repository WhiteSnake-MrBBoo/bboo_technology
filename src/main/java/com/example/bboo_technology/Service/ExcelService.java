package com.example.bboo_technology.Service;

import com.example.bboo_technology.DTO.AiHistoryExcelOptionDto;
import com.example.bboo_technology.DTO.OcrGptResultDto;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * ExcelService
 *
 * - OCR GPT 결과(ocr_gpt_result)를 엑셀 파일로 내보내는 전담 서비스
 * - AiHistoryExcelOptionDto 에 포함된 옵션에 따라:
 *   - 어떤 컬럼을 포함할지(체크박스)
 *   - 어떤 범위를 내보낼지(전체 / 선택된 행들)
 *   - 파일명을 무엇으로 할지
 *   를 제어할 수 있게 설계
 *
 * - 컨트롤러에서:
 *   1) 먼저 내보낼 데이터 List<OcrGptResultDto> 를 준비하고
 *   2) 모달에서 넘어온 AiHistoryExcelOptionDto 를 만든 뒤
 *   3) 이 서비스 메서드를 호출하는 구조로 사용
 */
@Service
public class ExcelService {

    /**
     * GPT 히스토리 엑셀 생성 (옵션 반영 버전)
     *
     * @param data     엑셀로 내보낼 GPT 히스토리 목록
     * @param option   모달에서 선택한 옵션들 (컬럼 포함 여부, 파일명 등)
     * @param response HttpServletResponse (여기에 바로 엑셀 바이너리 전송)
     *
     * 중요 포인트:
     * - "어떤 데이터를 내보낼지"는 컨트롤러에서 결정 (ALL / SELECTED)
     *   → 여기서는 단순히 "받은 리스트 + 옵션"만 가지고 엑셀 파일 생성
     * - 컬럼 순서와 데이터 순서를 반드시 동일하게 맞춰야 하므로
     *   헤더 작성 순서와 데이터 작성 순서를 항상 같이 수정해야 함
     */
    public void writeGptHistoryToExcel(
            List<OcrGptResultDto> data,
            AiHistoryExcelOptionDto option,
            HttpServletResponse response
    ) {

        // 1) 파일명 결정 로직
        //    - 사용자가 모달에서 입력한 파일명이 비어 있으면 기본값 사용
        //    - 확장자는 여기에서 .xlsx 로 통일
        String baseName = (option.getFileName() == null || option.getFileName().isBlank())
                ? "ocr_ai_history"
                : option.getFileName().trim();
        String fileName = baseName.endsWith(".xlsx") ? baseName : baseName + ".xlsx";

        // 2) 날짜 포맷터 (createdAt 출력용)
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("AI History");

            // -------------------------
            // (1) 헤더 행 생성
            // -------------------------
            int rowIdx = 0;
            Row header = sheet.createRow(rowIdx++);
            int colIdx = 0;

            // 각 컬럼은 "옵션이 true일 때만" 생성
            if (option.isIncludeId()) {
                header.createCell(colIdx++).setCellValue("ID");
            }
            if (option.isIncludeResultType()) {
                header.createCell(colIdx++).setCellValue("결과 타입");
            }
            if (option.isIncludeOcrTitle()) {
                header.createCell(colIdx++).setCellValue("OCR 제목");
            }
            if (option.isIncludeOcrFileName()) {
                header.createCell(colIdx++).setCellValue("파일명");
            }
            if (option.isIncludeModel()) {
                header.createCell(colIdx++).setCellValue("모델");
            }
            if (option.isIncludeCreatedAt()) {
                header.createCell(colIdx++).setCellValue("생성 시각");
            }
            if (option.isIncludeTokens()) {
                // 토큰은 한 번에 3개 컬럼을 사용 (프롬프트 / 컴플리션 / 토탈)
                header.createCell(colIdx++).setCellValue("Prompt Tokens");
                header.createCell(colIdx++).setCellValue("Completion Tokens");
                header.createCell(colIdx++).setCellValue("Total Tokens");
            }
            if (option.isIncludeContent()) {
                header.createCell(colIdx++).setCellValue("내용");
            }

            // 최종 컬럼 수 (autoSize용)
            int finalColumnCount = colIdx;

            // -------------------------
            // (2) 데이터 행 생성
            // -------------------------
            for (OcrGptResultDto dto : data) {
                Row row = sheet.createRow(rowIdx++);
                colIdx = 0;

                // 컬럼 생성 순서는 헤더와 반드시 동일해야 함!
                if (option.isIncludeId()) {
                    row.createCell(colIdx++).setCellValue(
                            dto.getId() != null ? dto.getId() : 0L
                    );
                }

                if (option.isIncludeResultType()) {
                    row.createCell(colIdx++).setCellValue(
                            nvl(dto.getResultType())
                    );
                }

                if (option.isIncludeOcrTitle()) {
                    row.createCell(colIdx++).setCellValue(
                            nvl(dto.getOcrTitle())
                    );
                }

                if (option.isIncludeOcrFileName()) {
                    row.createCell(colIdx++).setCellValue(
                            nvl(dto.getOcrFileName())
                    );
                }

                if (option.isIncludeModel()) {
                    row.createCell(colIdx++).setCellValue(
                            nvl(dto.getModel())
                    );
                }

                if (option.isIncludeCreatedAt()) {
                    String created = (dto.getCreatedAt() != null)
                            ? dtf.format(dto.getCreatedAt())
                            : "";
                    row.createCell(colIdx++).setCellValue(created);
                }

                if (option.isIncludeTokens()) {
                    // null 을 0으로 처리해 주면 엑셀에서 합계/평균 내기도 편함
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
                    row.createCell(colIdx++).setCellValue(
                            nvl(dto.getContent())
                    );
                }
            }

            // -------------------------
            // (3) 컬럼 너비 자동 조정
            // -------------------------
            for (int i = 0; i < finalColumnCount; i++) {
                sheet.autoSizeColumn(i);
            }

            // -------------------------
            // (4) HTTP 응답 헤더 설정 & 전송
            // -------------------------
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                    .replaceAll("\\+", "%20"); // 공백 처리

            response.setContentType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            );
            response.setHeader(
                    "Content-Disposition",
                    "attachment; filename=\"" + encoded + "\""
            );

            workbook.write(response.getOutputStream());

        } catch (Exception e) {
            // 실제 서비스에서는 로깅 + 커스텀 예외로 감싸는 패턴 권장
            throw new RuntimeException("엑셀 파일 생성 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 🔁 기존의 "기본 옵션" 버전
     *
     * - 과거에 사용하던 단순 버전과의 호환성을 위해 유지
     * - 기본 옵션(AiHistoryExcelOptionDto)을 만들어서
     *   위의 메서드로 위임하는 래퍼(wrapper) 역할
     */
    public void writeGptHistoryToExcel(List<OcrGptResultDto> data,
                                       HttpServletResponse response) {
        AiHistoryExcelOptionDto defaultOpt = new AiHistoryExcelOptionDto();
        defaultOpt.setScope("ALL");
        defaultOpt.setIncludeId(true);
        defaultOpt.setIncludeResultType(true);
        defaultOpt.setIncludeOcrTitle(true);
        defaultOpt.setIncludeOcrFileName(true);
        defaultOpt.setIncludeModel(true);
        defaultOpt.setIncludeCreatedAt(true);
        defaultOpt.setIncludeContent(true);
        defaultOpt.setIncludeTokens(false); // 기본은 토큰 컬럼 제외
        defaultOpt.setFileName("ocr_ai_history");

        writeGptHistoryToExcel(data, defaultOpt, response);
    }

    // ===========================
    // 내부 편의 메서드
    // ===========================

    /**
     * null-safe String 변환
     * - 엔티티/DTO에서 null 이 나와도 엑셀에 "null" 이라는 글자가 안 찍히도록 비어 있는 문자열로 처리
     */
    private String nvl(String s) {
        return (s != null) ? s : "";
    }
}
