package com.example.bboo_technology.Service;

/**
 * OCR 처리 중 발생하는 예외를 감싸는 RuntimeException.
 * - 체크 예외(IOException, TesseractException 등)를 한 번에 처리하기 위해 사용한다.
 * - Controller 단에서는 이 예외를 잡아서 사용자 친화적인 메시지를 보여줄 수 있다.
 */
public class OcrProcessingException extends RuntimeException{

    public OcrProcessingException(String message) {
        super(message);
    }

    public OcrProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
