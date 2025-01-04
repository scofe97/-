package com.onion.backend.service;

import com.onion.backend.entity.JwtBlacklist;
import com.onion.backend.jwt.JwtUtil;
import com.onion.backend.repository.JwtBlacklistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class JwtBlacklistService {

    private final JwtBlacklistRepository jwtBlacklistRepository;
    private final JwtUtil jwtUtil;

    /**
     * 토큰을 블랙리스트에 추가하는 메서드
     * @param token 블랙리스트에 추가할 JWT 토큰
     * @param expirationTime 해당 토큰의 만료 시간
     * @param username 해당 토큰과 연관된 사용자 이름
     */
    public void blacklistToken(String token, LocalDateTime expirationTime, String username) {
        // JwtBlacklist 엔티티 객체 생성 및 값 설정
        JwtBlacklist jwtBlacklist = new JwtBlacklist();
        jwtBlacklist.setToken(token); // 블랙리스트에 저장할 토큰 값
        jwtBlacklist.setExpirationTime(expirationTime); // 토큰 만료 시간
        jwtBlacklist.setUsername(username); // 토큰과 연관된 사용자 이름

        // 블랙리스트 데이터 저장
        jwtBlacklistRepository.save(jwtBlacklist);
    }

    /**
     * 주어진 토큰이 블랙리스트에 포함되어 있는지 확인하는 메서드
     * @param currentToken 확인할 JWT 토큰
     * @return 블랙리스트에 포함된 경우 true, 그렇지 않으면 false
     */
    public boolean isTokenBlacklisted(String currentToken) {
        // 현재 토큰에서 사용자 이름 추출
        String username = jwtUtil.getUsernameFromToken(currentToken);

        // 사용자 이름으로 최신 블랙리스트 토큰을 조회 (만료 시간 기준 정렬)
        Optional<JwtBlacklist> blacklistedToken = jwtBlacklistRepository.findTopByUsernameOrderByExpirationTime(username);

        // 블랙리스트에 토큰이 없으면 false 반환
        if (blacklistedToken.isEmpty()) {
            return false;
        }

        // 현재 토큰의 만료 시간을 LocalDateTime으로 변환
        Instant instant = jwtUtil.getExpirationDateFromToken(currentToken).toInstant(); // 토큰의 만료 시간을 Instant로 가져옴
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault()); // Instant를 LocalDateTime으로 변환

        // 블랙리스트에 저장된 만료 시간이 현재 토큰의 만료 시간보다 이후인지 확인
        // 2025-01-01 12:00(요청 만료시간), 2025-01-01 12:30(블랙리스트 만료시간)
        return blacklistedToken.get().getExpirationTime().isAfter(localDateTime.minusMinutes(60));
    }
}