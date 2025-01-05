package com.onion.backend.service;

import com.onion.backend.dto.WriteCommentDto;
import com.onion.backend.entity.Article;
import com.onion.backend.entity.Board;
import com.onion.backend.entity.Comment;
import com.onion.backend.entity.User;
import com.onion.backend.exception.ForbiddenException;
import com.onion.backend.exception.RateLimitException;
import com.onion.backend.exception.ResourceNotFoundException;
import com.onion.backend.repository.ArticleRepository;
import com.onion.backend.repository.BoardRepository;
import com.onion.backend.repository.CommentRepository;
import com.onion.backend.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final BoardRepository boardRepository;
    private final ArticleRepository articleRepository;

    private final CommentRepository commentRepository;

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public CompletableFuture<Article> getArticleWithCommentAsync(Long boardId, Long articleId) {
        CompletableFuture<Article> articleFuture = this.getArticle(boardId, articleId);
        CompletableFuture<List<Comment>> commentsFuture = this.getComments(articleId);

        return CompletableFuture.allOf(articleFuture, commentsFuture)
                .thenApply(voidResult -> {
                    try {
                        Article article = articleFuture.get();
                        List<Comment> comments = commentsFuture.get();
                        article.setComments(comments);
                        return article;
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                        return null;
                    }
                });
    }

    @Async
    protected CompletableFuture<Article> getArticle(Long boardId, Long articleId) {
        boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Board not found"));
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("Article not found"));

        return CompletableFuture.completedFuture(article);
    }

    @Async
    protected CompletableFuture<List<Comment>> getComments(Long articleId) {
        return CompletableFuture.completedFuture(commentRepository.findByArticleId(articleId));
    }

    /**
     * 댓글 작성
     * @param boardId 게시판 ID
     * @param articleId 게시글 ID
     * @param dto 댓글 작성 데이터
     * @return 작성된 댓글
     * @throws RateLimitException 댓글 작성 제한이 걸린 경우
     * @throws ResourceNotFoundException 게시판, 게시글 또는 사용자가 존재하지 않을 경우
     * @throws ForbiddenException 게시글이 삭제된 경우
     */
    @Transactional
    public Comment writeComment(Long boardId, Long articleId, WriteCommentDto dto) {
        if (!isCanWriteComment()) {
            throw new RateLimitException("comment not written by rate limit");
        }

        User author = getAuthenticatedUser();
        getBoard(boardId);
        Article article = getArticle(articleId);

        Comment comment = new Comment();
        comment.setAuthor(author);
        comment.setArticle(article);
        comment.setContent(dto.getContent());

        return commentRepository.save(comment);
    }

    /**
     * 댓글 수정
     * @param boardId 게시판 ID
     * @param articleId 게시글 ID
     * @param commentId 댓글 ID
     * @param dto 댓글 수정 데이터
     * @return 수정된 댓글
     * @throws RateLimitException 댓글 수정 제한이 걸린 경우
     * @throws ResourceNotFoundException 게시판, 게시글, 댓글 또는 사용자가 존재하지 않을 경우
     * @throws ForbiddenException 게시글이 삭제되었거나, 댓글 작성자가 아닌 경우
     */
    @Transactional
    public Comment editComment(Long boardId, Long articleId, Long commentId, WriteCommentDto dto) {
        if (!isCanEditComment()) {
            throw new RateLimitException("comment not written by rate limit");
        }

        User author = getAuthenticatedUser();
        getBoard(boardId);
        getArticle(articleId);

        Comment comment = getComment(commentId);
        if (!comment.getAuthor().equals(author)) {
            throw new ForbiddenException("comment author different");
        }

        if (dto.getContent() != null) {
            comment.setContent(dto.getContent());
        }

        return commentRepository.save(comment);
    }

    /**
     * 댓글 삭제
     * @param boardId 게시판 ID
     * @param articleId 게시글 ID
     * @param commentId 댓글 ID
     * @return 삭제 성공 여부
     * @throws RateLimitException 댓글 삭제 제한이 걸린 경우
     * @throws ResourceNotFoundException 게시판, 게시글, 댓글 또는 사용자가 존재하지 않을 경우
     * @throws ForbiddenException 게시글이 삭제되었거나, 댓글 작성자가 아닌 경우
     */
    @Transactional
    public boolean deleteComment(Long boardId, Long articleId, Long commentId) {
        if (!isCanEditComment()) {
            throw new RateLimitException("comment not written by rate limit");
        }

        User author = getAuthenticatedUser();
        getBoard(boardId);
        getArticle(articleId);

        Comment comment = getComment(commentId);
        if (!comment.getAuthor().equals(author)) {
            throw new ForbiddenException("comment author different");
        }

        comment.setIsDeleted(true);
        commentRepository.save(comment);

        return true;
    }


    /**
     * 인증된 사용자를 가져옵니다.
     * @return User 객체
     * @throws ResourceNotFoundException 사용자가 존재하지 않을 경우
     */
    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return userRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("author not found"));
    }

    /**
     * 주어진 ID로 게시판을 조회합니다.
     * @param boardId 게시판 ID
     * @return Board 객체
     * @throws ResourceNotFoundException 게시판이 존재하지 않을 경우
     */
    private Board getBoard(Long boardId) {
        return boardRepository.findById(boardId)
                .orElseThrow(() -> new ResourceNotFoundException("board not found"));
    }

    /**
     * 주어진 ID로 게시글을 조회합니다.
     * @param articleId 게시글 ID
     * @return Article 객체
     * @throws ResourceNotFoundException 게시글이 존재하지 않을 경우
     * @throws ForbiddenException 게시글이 삭제된 경우
     */
    private Article getArticle(Long articleId) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new ResourceNotFoundException("article not found"));

        if (article.getIsDeleted()) {
            throw new ForbiddenException("article is deleted");
        }
        return article;
    }

    /**
     * 주어진 ID로 댓글을 조회합니다.
     * @param commentId 댓글 ID
     * @return Comment 객체
     * @throws ResourceNotFoundException 댓글이 존재하지 않거나 삭제된 경우
     */
    private Comment getComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("comment not found"));
        if (comment.getIsDeleted()) {
            throw new ResourceNotFoundException("comment not found");
        }
        return comment;
    }

    /**
     * 댓글 작성 가능 여부를 판단합니다.
     * @return true: 작성 가능, false: 작성 불가능
     *
     * 판단 기준:
     * - 사용자가 마지막으로 작성한 댓글이 없거나, 마지막 댓글 작성 시점이 1분 이상 지났을 경우
     */
    private boolean isCanWriteComment() {
        String username = getAuthenticatedUser().getUsername();
        Comment latestComment = commentRepository.findLatestCommentOrderByCreatedDate(username);
        return (latestComment == null) || isDifferenceMoreThanOneMinute(latestComment.getCreatedDate());
    }

    /**
     * 댓글 수정 가능 여부를 판단합니다.
     * @return true: 수정 가능, false: 수정 불가능
     *
     * 판단 기준:
     * - 사용자가 마지막으로 작성한 댓글이 없거나, 마지막 수정 시점이 1분 이상 지났을 경우
     */
    private boolean isCanEditComment() {
        String username = getAuthenticatedUser().getUsername();
        Comment latestComment = commentRepository.findLatestCommentOrderByCreatedDate(username);
        return (latestComment == null || latestComment.getUpdatedDate() == null) ||
                isDifferenceMoreThanOneMinute(latestComment.getUpdatedDate());
    }

    /**
     * 특정 시간과 현재 시간의 차이가 1분 이상인지 확인합니다.
     * @param dateTime 비교할 시간 (LocalDateTime)
     * @return true: 1분 이상 차이 있음, false: 1분 이내
     */
    private boolean isDifferenceMoreThanOneMinute(LocalDateTime dateTime) {
        LocalDateTime currentTime = LocalDateTime.now();
        Duration duration = Duration.between(dateTime, currentTime);
        return Math.abs(duration.toSeconds()) > 5;
    }
}
