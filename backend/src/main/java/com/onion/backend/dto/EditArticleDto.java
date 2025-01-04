package com.onion.backend.dto;

public record EditArticleDto(String title, String content) {

    public EditArticleDto {
        // 생성자에서 기본값 설정
        title = title != null ? title : "Default Title";
        content = content != null ? content : "Default Content";
    }
}
