package com.example.bboo_technology.Service;


import com.example.bboo_technology.DTO.OcrResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * OcrFacadeService 의 기본 구현체.
 * - 파일의 MIME 타입 및 확장자를 기준으로 이미지/PDF 를 판별하고,
 *   각각 ImageOcrProcessor / PdfOcrProcessor 에 실제 OCR 처리를 위임한다.
 * - 최종적으로 View/Session 에서 사용할 OcrResultDto 를 생성한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OcrFacadeServiceImpl implements OcrFacadeService {

    private final ImageOcrProcessor imageOcrProcessor;
    private final PdfOcrProcessor pdfOcrProcessor;

    @Override
    public OcrResultDto extractText(MultipartFile file) {
        // 1. 파일 기본 정보 추출
        String originalFileName = (file.getOriginalFilename() != null)
                ? file.getOriginalFilename()
                : "unnamed";

        String contentType = (file.getContentType() != null)
                ? file.getContentType()
                : "";

        // 2. 파일 타입 판별 (MIME 타입 우선, 필요 시 확장자로 보조 판단)
        boolean isPdf = isPdfFile(contentType, originalFileName);
        boolean isImage = isImageFile(contentType, originalFileName);

        if (!isPdf && !isImage) {
            // 지원하지 않는 파일 형식
            log.warn("지원하지 않는 파일 형식 - filename={}, contentType={}", originalFileName, contentType);
            throw new OcrProcessingException("지원하지 않는 파일 형식입니다. 이미지(JPG, PNG) 또는 PDF만 업로드해 주세요.");
        }

        String fileTypeLabel;
        String ocrText;
        Integer pageCount = null;

        // 3. 파일 타입에 따라 각 Processor 에 OCR 위임
        if (isPdf) {
            fileTypeLabel = "PDF";

            PdfOcrProcessor.PdfOcrResult result = pdfOcrProcessor.process(file);
            ocrText = result.getText();
            pageCount = result.getPageCount();

        } else {
            // 이미지로 간주
            fileTypeLabel = "IMAGE";

            ocrText = imageOcrProcessor.process(file);
            pageCount = 1; // 단일 이미지이므로 1페이지 취급
        }

        // 4. DTO 구성 (처음에는 title/editedText/translatedText 는 비워둠)
        OcrResultDto dto = OcrResultDto.builder()
                .id(null)                        // 아직 DB 저장 전이므로 null
                .originalFileName(originalFileName)
                .fileType(fileTypeLabel)
                .pageCount(pageCount)
                .title("")                      // View 에서 입력받을 값
                .ocrText(ocrText)
                .editedText(null)               // 저장 시점에 채워질 예정
                .translatedText(null)           // 번역 기능 연동 후 사용
                .createdAt(LocalDateTime.now())
                .updatedAt(null)
                .saved(false)                   // 저장 전이므로 false
                .build();

        log.info("OCR 처리 완료 - filename={}, type={}, pages={}, length={}",
                originalFileName, fileTypeLabel, pageCount, (ocrText != null ? ocrText.length() : 0));

        return dto;
    }

    /**
     * 파일이 PDF 인지 판별하는 유틸 메서드.
     * - contentType 및 파일 확장자를 함께 검사하여 안정성을 높인다.
     */
    private boolean isPdfFile(String contentType, String fileName) {
        if (contentType != null && contentType.equalsIgnoreCase("application/pdf")) {
            return true;
        }
        if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
            return true;
        }
        return false;
    }

    /**
     * 파일이 이미지인지 판별하는 유틸 메서드.
     * - MIME 타입이 image/* 이거나, 확장자가 jpg/jpeg/png/webp 인 경우 등으로 판단.
     */
    private boolean isImageFile(String contentType, String fileName) {
        if (contentType != null && contentType.toLowerCase().startsWith("image/")) {
            return true;
        }
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".png") || lower.endsWith(".bmp")
                || lower.endsWith(".gif") || lower.endsWith(".webp");
    }


}
