package com.example.bboo_technology.Service.Ocrservice;

import com.example.bboo_technology.DTO.OcrResultDto;
import org.springframework.web.multipart.MultipartFile;

/**
 * OCR 처리의 "관제탑" 역할을 하는 Facade 서비스.
 * - Controller 에서 업로드된 파일을 넘기면,
 *   파일 타입에 따라 적절한 Processor(Image / PDF)를 호출하고
 *   최종적으로 OcrResultDto 를 만들어 반환한다.
 */
public interface OcrFacadeService {

    /**
     * 업로드된 파일 하나에 대해 OCR 을 수행하고, 결과를 OcrResultDto 로 반환한다.
     * - 이미지 파일: ImageOcrProcessor 사용
     * - PDF 파일  : PdfOcrProcessor 사용
     *
     * @param file 업로드된 파일 (이미지 또는 PDF)
     * @return OCR 결과 DTO
     */
    OcrResultDto extractText(MultipartFile file);


}
