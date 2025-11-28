package com.example.bboo_technology.Service.Sttservice;

import com.example.bboo_technology.DTO.Stt.SttRequest;
import com.example.bboo_technology.DTO.Stt.SttResult;

// (추가) STT 엔진 공통 인터페이스
public interface SttEngine {

    /**
     * 단일 오디오 파일에 대한 STT 수행 (동기식)
     */
    SttResult transcribe(SttRequest request);
}
