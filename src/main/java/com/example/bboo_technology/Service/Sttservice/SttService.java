package com.example.bboo_technology.Service.Sttservice;

import com.example.bboo_technology.DTO.Stt.SttResult;
import com.example.bboo_technology.DTO.Stt.SttWebResponse;
import org.springframework.web.multipart.MultipartFile;

// (추가) 파일 기반 STT 서비스 인터페이스
public interface SttService {

    /**
     * 업로드된 파일을 STT 엔진으로 변환
     *
     * @param file        업로드된 오디오 파일
     * @param sessionId   세션 ID (옵션, null 가능)
     * @param languageHint 언어 힌트 (예: "ko", "en", null이면 auto)
     * @return STT 결과
     */
    SttResult transcribeFile(MultipartFile file, String sessionId, String languageHint);

    // (추가) 웹/API 응답용 메서드
    SttWebResponse transcribeFileForWeb(MultipartFile file, String sessionId, String languageHint);

}
