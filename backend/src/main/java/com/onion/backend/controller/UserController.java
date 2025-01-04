package com.onion.backend.controller;

import com.onion.backend.dto.SignUpUser;
import com.onion.backend.entity.User;
import com.onion.backend.jwt.JwtUtil;
import com.onion.backend.service.JwtBlacklistService;
import com.onion.backend.service.UserService;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users")
public class UserController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final UserDetailsService userDetailsService;
    private final JwtBlacklistService jwtBlacklistService;

    @GetMapping("")
    public ResponseEntity<List<User>> getUserS() {
        return ResponseEntity.ok(userService.getUsers());
    }

    @PostMapping("/signUp")
    public ResponseEntity<User> createUser(
            @RequestBody SignUpUser signUpUser
    ) {
        User user = userService.createUser(
                signUpUser.username()
                , signUpUser.password()
                , signUpUser.email()
        );
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(
            @Parameter(description = "ID of the user to be deleted", required = true)
            @PathVariable Long userId
    ) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 사용자 로그인 메서드
     * @param username 사용자가 입력한 사용자 이름
     * @param password 사용자가 입력한 비밀번호
     * @param response HTTP 응답 객체 (토큰을 쿠키에 저장하기 위해 사용)
     * @return 생성된 JWT 토큰
     * @throws AuthenticationException 인증 실패 시 예외 발생
     *
     * 동작:
     * 1. `AuthenticationManager`를 사용해 사용자의 인증 정보를 검증.
     * 2. 사용자 정보가 유효하면 JWT 토큰 생성.
     * 3. 생성된 JWT 토큰을 `HttpOnly` 쿠키로 클라이언트에 전송.
     */
    @PostMapping("/login")
    public String login(@RequestParam String username, @RequestParam String password, HttpServletResponse response) throws AuthenticationException {
        // 사용자가 입력한 인증 정보(username, password) 검증
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));

        // 사용자 정보로드
        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

        // JWT 토큰을 HttpOnly 쿠키에 저장
        String token = jwtUtil.generateToken(userDetails.getUsername());
        Cookie cookie = new Cookie("onion_token", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60);

        response.addCookie(cookie);
        return token;
    }

    /**
     * 사용자 로그아웃 메서드
     * @param response HTTP 응답 객체 (쿠키 삭제를 위해 사용)
     *
     * 동작:
     * 1. 클라이언트에 저장된 `onion_token` 쿠키를 삭제.
     * 2. `HttpOnly` 속성으로 쿠키 삭제를 처리.
     */
    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        Cookie cookie = new Cookie("onion_token", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0); // 쿠키 삭제
        response.addCookie(cookie);
    }

    /**
     * 모든 인증 토큰을 무효화하는 로그아웃 메서드
     * @param requestToken 요청 매개변수로 전달된 토큰 (선택적)
     * @param cookieToken 쿠키로 전달된 "onion_token" 값 (선택적)
     * @param request HTTP 요청 객체 (Authorization 헤더에서 토큰 추출에 사용)
     * @param response HTTP 응답 객체 (쿠키 삭제를 위해 사용)
     *
     * 동작:
     * 1. 요청 매개변수, 쿠키, 또는 Authorization 헤더에서 토큰 추출.
     * 2. 추출한 토큰을 블랙리스트에 추가하여 무효화.
     * 3. 클라이언트의 "onion_token" 쿠키를 삭제.
     */
    @PostMapping("/logout/all")
    public void logout(
            @RequestParam(required = false) String requestToken
            , @CookieValue(value = "onion_token", required = false) String cookieToken
            , HttpServletRequest request
            , HttpServletResponse response
    ) {

        // 1. 요청 매개변수, 쿠키, 또는 Authorization 헤더에서 토큰 추출
        String token = null;
        String bearerToken = request.getHeader("Authorization");

        if (requestToken != null) {
            token = requestToken;
        } else if (cookieToken != null) {
            token = cookieToken;
        } else if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            token = bearerToken.substring(7);
        }

        // 2. 토큰의 만료 시간을 현재 시간으로 설정
        Instant instant = new Date().toInstant(); // 현재 시간을 Instant로 가져옴
        LocalDateTime expirationTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime();

        // 3. 토큰에서 사용자 이름 추출 및 블랙리스트에 추가
        String username = jwtUtil.getUsernameFromToken(token);
        jwtBlacklistService.blacklistToken(token, expirationTime, username);

        // 4. 클라이언트의 "onion_token" 쿠키를 삭제
        Cookie cookie = new Cookie("onion_token", null);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
    }

    @PostMapping("/token/validation")
    @ResponseStatus(HttpStatus.OK)
    public void jwtValidate(@RequestParam String token) {
        if (!jwtUtil.validateToken(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Token is not validation");
        }
    }
}
