package com.example.board.file.exception;

/** 허용되지 않는 확장자의 파일 업로드 시 발생 */
public class UnsupportedFileTypeException extends RuntimeException {

    public UnsupportedFileTypeException(String fileName) {
        super("허용되지 않는 파일 형식입니다: " + fileName);
    }
}
