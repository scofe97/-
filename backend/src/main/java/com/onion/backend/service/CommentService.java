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
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CommentService {
    private final BoardRepository boardRepository;
    private final ArticleRepository articleRepository;

    private final CommentRepository commentRepository;

    private final UserRepository userRepository;

    @Transactional
    public Comment writeComment(Long boardId, Long articleId, WriteCommentDto dto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        if (!this.isCanWriteComment()) {
            throw new RateLimitException("article not written by rate limit");
        }
        Optional<User> author = userRepository.findByUsername(userDetails.getUsername());
        Optional<Board> board = boardRepository.findById(boardId);
        Optional<Article> article = articleRepository.findById(articleId);
        if (author.isEmpty()) {
            throw new ResourceNotFoundException("author not found");
        }
        if (board.isEmpty()) {
            throw new ResourceNotFoundException("board not found");
        }
        if (article.isEmpty()) {
            throw new ResourceNotFoundException("article not found");
        }
        if (article.get().getIsDeleted()) {
            throw new ForbiddenException("article is deleted");
        }
        Comment comment = new Comment();
        comment.setArticle(article.get());
        comment.setAuthor(author.get());
        comment.setContent(dto.getContent());
        commentRepository.save(comment);
        return comment;
    }

    private boolean isCanWriteComment() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Comment latestComment = commentRepository.findLatestCommentOrderByCreatedDate(userDetails.getUsername());
        if (latestComment == null) {
            return true;
        }
        return this.isDifferenceMoreThanOneMinutes(latestComment.getCreatedDate());
    }

    private boolean isCanEditComment() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Comment latestComment = commentRepository.findLatestCommentOrderByCreatedDate(userDetails.getUsername());
        if (latestComment == null) {
            return true;
        }
        return this.isDifferenceMoreThanOneMinutes(latestComment.getUpdatedDate());
    }

    private boolean isDifferenceMoreThanOneMinutes(LocalDateTime localDateTime) {
        LocalDateTime dateAsLocalDateTime = new Date().toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();

        Duration duration = Duration.between(localDateTime, dateAsLocalDateTime);

        return Math.abs(duration.toMinutes()) > 1;
    }
}
