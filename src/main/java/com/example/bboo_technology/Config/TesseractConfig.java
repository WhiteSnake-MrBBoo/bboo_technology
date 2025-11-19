package com.example.bboo_technology.Config;


import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tesseract(OCR 엔진) 공통 설정을 담당하는 설정 클래스.
 * - application.properties 의 tesseract.datapath, tesseract.language 값을 읽어와서
 *   ITesseract Bean 을 생성한다.
 * - ImageOcrProcessor / PdfOcrProcessor 에서 주입 받아 사용한다.
 */
@Slf4j
@Configuration
public class TesseractConfig {


    /**
     * 학습 데이터(.traineddata)가 위치한 tessdata 폴더 경로.
     * 예) C:/Program Files/Tesseract-OCR/tessdata
     */
    @Value("${tesseract.datapath}")
    private String dataPath;

    /**
     * 사용할 언어 코드.
     * 예) kor+eng (한글+영어 혼합 텍스트 인식)
     */
    @Value("${tesseract.language:kor+eng}")
    private String language;

    @Bean
    public ITesseract tesseract() {
        Tesseract tesseract = new Tesseract();

        // (중요) tessdata 경로 설정
        tesseract.setDatapath(dataPath);

        // (중요) 사용 언어 설정
        tesseract.setLanguage(language);

        // 필요 시 엔진 모드나 추가 옵션도 설정 가능 (나중에 튜닝)
        log.info("Initialized Tesseract with datapath='{}', language='{}'", dataPath, language);

        return tesseract;
    }


}
