package com.example.bboo_technology.Service;


import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * PDF 문서를 대상으로 OCR 을 수행하는 Processor.
 * - PDFBox 를 사용하여 각 페이지를 이미지로 렌더링한 뒤,
 *   Tesseract 로 페이지별 OCR 을 수행하고 결과 텍스트를 합쳐서 반환한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfOcrProcessor {

    private final ITesseract tesseract;

    @PostConstruct
    void afterConstruct() {
        log.info("PdfOcrProcessor initialized.");
    }

    /**
     * PDF 파일 전체에 대해 OCR 을 수행한다.
     *
     * @param file 업로드된 PDF 파일
     * @return PdfOcrResult (전체 텍스트 + 페이지 수)
     * @throws OcrProcessingException OCR 처리 중 오류가 발생한 경우
     */
    public PdfOcrResult process(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {

            int pageCount = document.getNumberOfPages();
            PDFRenderer renderer = new PDFRenderer(document);

            StringBuilder sb = new StringBuilder();

            // (중요) PDF 는 페이지 단위로 루프를 돌면서 OCR 수행
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                // 1) PDF 페이지를 이미지로 렌더링 (DPI 300 권장)
                BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, 300);

                // 2) 해당 페이지 OCR 수행
                String pageText = tesseract.doOCR(pageImage);

                // 3) 페이지 구분선을 넣어주면 나중에 보기 편함
                sb.append("=== PAGE ").append(pageIndex + 1).append(" ===\n");
                sb.append(pageText != null ? pageText : "").append("\n\n");
            }

            String fullText = sb.toString();
            log.debug("PDF OCR 완료 - filename={}, pages={}, length={}",
                    file.getOriginalFilename(), pageCount, fullText.length());

            return new PdfOcrResult(fullText, pageCount);

        } catch (IOException e) {
            log.error("PDF 파일을 읽는 중 오류 발생", e);
            throw new OcrProcessingException("PDF 파일을 읽는 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            log.error("PDF OCR 처리 중 예외 발생", e);
            throw new OcrProcessingException("PDF OCR 처리 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * PDF OCR 결과를 담는 간단한 내부 DTO.
     * - 전체 텍스트 + 페이지 수만 필요하므로 별도의 클래스로 분리하였다.
     */
    @Getter
    @AllArgsConstructor
    public static class PdfOcrResult {
        private final String text;      // PDF 전체 페이지를 OCR 한 결과 텍스트
        private final int pageCount;    // PDF 총 페이지 수
    }

}
