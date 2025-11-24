package com.example.bboo_technology.Service.Ocrservice;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

//todo:단일 이미지 처리 @Serbvice
/**
 * 이미지 파일(JPG, PNG 등)을 대상으로 OCR 을 수행하는 Processor.
 * 단일 이미지 처리 @Serbvice
 * - 파일의 MIME 타입이 image/* 인 경우 이 클래스가 사용된다.
 * - 단일 페이지 이미지 기준으로 동작하며, pageCount 는 1로 간주한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageOcrProcessor {

    private final ITesseract tesseract;

    @PostConstruct
    void afterConstruct() {
        log.info("ImageOcrProcessor initialized.");
    }

    /**
     * 단일 이미지 파일에 대해 Tesseract 를 사용하여 텍스트를 추출한다.
     *
     * @param file 업로드된 이미지 파일
     * @return 추출된 텍스트 (OCR 결과)
     * @throws OcrProcessingException OCR 처리 중 오류가 발생한 경우
     */
    public String process(MultipartFile file) {
        try {
            // MultipartFile → BufferedImage 변환
            BufferedImage image = ImageIO.read(file.getInputStream());
            if (image == null) {
                // ImageIO.read 가 null 을 반환하는 경우: 이미지 포맷이 아니거나 깨진 파일일 때
                throw new OcrProcessingException("이미지 파일을 읽을 수 없습니다. 파일 형식을 확인해 주세요.");
            }

            // Tesseract 를 사용하여 실제 OCR 수행
            String text = tesseract.doOCR(image);

            log.debug("Image OCR 완료 - filename={}, length={}", file.getOriginalFilename(),
                    (text != null ? text.length() : 0));

            return text;

        } catch (IOException e) {
            // 파일 I/O 관련 오류
            log.error("이미지 파일을 읽는 중 오류 발생", e);
            throw new OcrProcessingException("이미지 파일을 읽는 중 오류가 발생했습니다.", e);
        } catch (Exception e) {
            // Tesseract 예외 등 기타 모든 예외 처리
            log.error("이미지 OCR 처리 중 예외 발생", e);
            throw new OcrProcessingException("이미지 OCR 처리 중 오류가 발생했습니다.", e);
        }
    }
}
