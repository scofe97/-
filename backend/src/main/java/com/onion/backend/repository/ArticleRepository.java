package com.onion.backend.repository;

import com.onion.backend.entity.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ArticleRepository extends JpaRepository<Article, Long> {
    List<Article> findTop10ByBoardIdAndIsDeletedFalseOrderByCreatedDateDesc(Long boardId);

    List<Article> findTop10ByBoardIdAndIdLessThanAndIsDeletedFalseOrderByCreatedDateDesc(Long boardId, Long articleId);

    List<Article> findTop10ByBoardIdAndIdGreaterThanAndIsDeletedFalseOrderByCreatedDateDesc(Long boardId, Long articleId);

    @Query("SELECT a FROM Article a JOIN a.author u WHERE u.username = :username ORDER BY a.createdDate DESC LIMIT 1")
    Article findLatestArticleByAuthorUsernameOrderByCreatedDate(@Param("username") String username);

    @Query("SELECT a FROM Article a JOIN a.author u WHERE u.username = :username ORDER BY a.updatedDate DESC LIMIT 1")
    Article findLatestArticleByAuthorUsernameOrderByUpdatedDate(@Param("username") String username);
}
