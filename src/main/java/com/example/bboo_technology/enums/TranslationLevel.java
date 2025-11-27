package com.example.bboo_technology.enums;

/**
 * 번역 품질/비용 레벨을 나타내는 Enum.
 * 번역 활용 GPT 모델별 상수 데이터
 * - BASIC   : 기본 번역 (일반 품질, 비용/속도 균형)
 * - PREMIUM : 고품질 번역 (비즈니스, 쇼호스트 멘트 등)
 * - ECONOMY : 저비용 반복 번역 작업용
 */
public enum TranslationLevel {
    BASIC,
    PREMIUM,
    ECONOMY
}
