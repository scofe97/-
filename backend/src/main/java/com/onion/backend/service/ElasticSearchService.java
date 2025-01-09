package com.onion.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ElasticSearchService {
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    /**
     * 특정 키워드를 기준으로 Elasticsearch에서 검색하고, 결과로 반환된 문서의 ID 리스트를 반환합니다.
     *
     * @param keyword 검색 키워드
     * @return 문서 ID 리스트를 담은 Mono 객체
     */
    public Mono<List<Long>> articleSearch(String keyword) {
        // Elasticsearch에 전달할 쿼리 생성 (JSON 형태로 문자열 구성)
        String query = String.format(
                "{\"_source\": false, \"query\": {\"match\": {\"content\": \"%s\"}}, \"fields\": [\"_id\"], \"size\": 10}",
                keyword
        );

        // WebClient를 통해 Elasticsearch의 /article/_search 엔드포인트 호출
        return webClient.post()
                .uri("/article/_search") // 검색 API 호출
                .header("Content-Type", "application/json") // 요청 Content-Type 설정
                .header("Accept", "application/json") // 응답 Accept 타입 설정
                .bodyValue(query) // HTTP 요청 본문에 검색 쿼리 포함
                .retrieve() // 응답 처리 시작
                .bodyToMono(String.class) // 응답 데이터를 Mono<String>으로 변환
                .flatMap(this::extractIds); // 응답에서 ID 리스트를 추출하는 메서드 호출
    }

    /**
     * Elasticsearch에 특정 인덱스에 문서를 색인합니다.
     *
     * @param index 색인을 수행할 인덱스 이름
     * @param id 문서 ID
     * @param document 색인할 문서 내용 (JSON 문자열)
     * @return 색인 결과를 담은 Mono<String> 객체
     */
    public Mono<String> indexDocument(String index, String id, String document) {
        // WebClient를 통해 Elasticsearch의 PUT 요청으로 문서를 색인
        return webClient.put()
                .uri("/{index}/_doc/{id}", index, id) // 색인 대상 인덱스와 ID 설정
                .header("Content-Type", "application/json") // 요청 Content-Type 설정
                .header("Accept", "application/json") // 응답 Accept 타입 설정
                .bodyValue(document) // HTTP 요청 본문에 색인할 문서 데이터 포함
                .retrieve() // 응답 처리 시작
                .bodyToMono(String.class); // 응답 데이터를 Mono<String>으로 변환
    }

    /**
     * Elasticsearch 검색 응답에서 문서 ID 리스트를 추출합니다.
     *
     * @param responseBody Elasticsearch 검색 응답 본문 (JSON 문자열)
     * @return 문서 ID 리스트를 담은 Mono 객체
     */
    private Mono<List<Long>> extractIds(String responseBody) {
        List<Long> ids = new ArrayList<>();
        try {
            // 응답 본문(JSON)을 ObjectMapper로 파싱하여 필요한 데이터 추출
            JsonNode hits = objectMapper.readTree(responseBody).path("hits").path("hits");
            hits.forEach(hit -> ids.add(hit.path("_id").asLong())); // 각 문서의 _id를 리스트에 추가
        } catch (IOException e) {
            // JSON 파싱 실패 시 Mono.error 반환
            return Mono.error(e);
        }
        // 추출된 ID 리스트를 Mono로 감싸 반환
        return Mono.just(ids);
    }
}
