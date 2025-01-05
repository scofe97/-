package com.onion.backend.service;

import com.onion.backend.dto.EditArticleDto;
import com.onion.backend.dto.WriteArticleDto;
import com.onion.backend.entity.Article;
import com.onion.backend.entity.Board;
import com.onion.backend.entity.User;
import com.onion.backend.exception.ForbiddenException;
import com.onion.backend.exception.RateLimitException;
import com.onion.backend.exception.ResourceNotFoundException;
import com.onion.backend.repository.ArticleRepository;
import com.onion.backend.repository.BoardRepository;
import com.onion.backend.repository.CommentRepository;
import com.onion.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ArticleService {
    private final BoardRepository boardRepository;
    private final ArticleRepository articleRepository;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<Article> firstGetArticle(Long boardId) {
        return articleRepository.findTop10ByBoardIdOrderByCreatedDateDesc(boardId);
    }

    @Transactional(readOnly = true)
    public List<Article> getOldArticle(Long boardId, Long articleId) {
        return articleRepository.findTop10ByBoardIdAndArticleIdLessThanOrderByCreatedDateDesc(boardId, articleId);
    }

    @Transactional(readOnly = true)
    public List<Article> getNewArticle(Long boardId, Long articleId) {
        return articleRepository.findTop10ByBoardIdAndArticleIdGreaterThanOrderByCreatedDateDesc(boardId, articleId);
    }

    @Transactional
    public Article writeArticle(Long boardId, WriteArticleDto dto) {
        // 1. 인증된 사용자 정보 가져오기
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 2. 게시글 작성 가능 여부 확인
        if (!isCanWriteArticle()) {
            throw new RateLimitException("article not written by rate limit");
        }

        // 3. 작성자(User) 정보 조회
        User author = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("author not found"));

        // 4. 게시판(Board) 정보 조회
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("board not found"));

        // 5. 게시글 생성 및 설정
        Article article = new Article();
        article.setBoard(board); // 게시판 설정
        article.setAuthor(author); // 작성자 설정
        article.setTitle(dto.getTitle()); // 제목 설정
        article.setContent(dto.getContent()); // 내용 설정

        // 6. 게시글 저장 및 반환
        return articleRepository.save(article);
    }

    @Transactional
    public Article editArticle(Long boardId, Long articleId, EditArticleDto dto) {
        // 1. 인증된 사용자 정보 가져오기
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 2. 작성자(User) 정보 조회
        // 현재 인증된 사용자의 이름으로 작성자 정보(User)를 가져옴
        User author = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("author not found"));

        // 3. 게시판(Board) 정보 조회
        // boardId에 해당하는 게시판을 찾지 못하면 예외를 던짐
        boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("board not found"));

        // 4. 게시글(Article) 정보 조회
        // articleId에 해당하는 게시글을 찾지 못하면 예외를 던짐
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("article not found"));

        // 5. 작성자 확인
        // 게시글 작성자가 현재 사용자와 다른 경우 예외를 던짐
        if (!article.getAuthor().equals(author)) {
            throw new ForbiddenException("article author different");
        }

        // 6. 편집 제한 확인
        // 편집 제한 조건을 만족하지 않으면 예외를 던짐
        if (!isCanEditArticle()) {
            throw new RateLimitException("article not edited by rate limit");
        }

        // 7. 게시글 제목과 내용 업데이트
        // 제목과 내용이 null이 아니면 업데이트, null이면 기존 값 유지
        article.setTitle(dto.title() != null ? dto.title() : article.getTitle());
        article.setContent(dto.content() != null ? dto.content() : article.getContent());

        // 8. 변경된 게시글 저장 및 반환
        return articleRepository.save(article);
    }

    @Transactional
    public boolean deleteArticle(Long boardId, Long articleId) {
        // 1. 인증된 사용자 정보 가져오기
        UserDetails userDetails = (UserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // 2. 작성자(User) 정보 조회
        User author = userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("author not found"));

        // 3. 게시판(Board) 정보 조회
        boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("board not found"));

        // 4. 게시글(Article) 정보 조회
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("article not found"));

        // 5. 게시글 작성자 확인
        if (!article.getAuthor().equals(author)) {
            throw new ForbiddenException("article author different");
        }

        // 6. 편집 제한 확인
        if (!isCanEditArticle()) {
            throw new RateLimitException("article not edited by rate limit");
        }

        // 7. 게시글 삭제 상태로 변경
        article.setIsDeleted(true);

        // 8. 변경된 게시글 저장
        articleRepository.save(article);

        return true; // 삭제 성공
    }

    private boolean isCanWriteArticle() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Article latestArticle = articleRepository.findLatestArticleByAuthorUsernameOrderByCreatedDate(userDetails.getUsername());
        if (latestArticle == null) {
            return true;
        }
        return this.isDifferenceMoreThanFiveMinutes(latestArticle.getCreatedDate());
    }

    private boolean isCanEditArticle() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Article latestArticle = articleRepository.findLatestArticleByAuthorUsernameOrderByUpdatedDate(userDetails.getUsername());
        if (latestArticle == null || latestArticle.getUpdatedDate() == null) {
            return true;
        }
        return this.isDifferenceMoreThanFiveMinutes(latestArticle.getUpdatedDate());
    }

    private boolean isDifferenceMoreThanFiveMinutes(LocalDateTime localDateTime) {
        LocalDateTime dateAsLocalDateTime = new Date().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        Duration duration = Duration.between(localDateTime, dateAsLocalDateTime);

        return Math.abs(duration.toSeconds()) > 5;
    }
}
