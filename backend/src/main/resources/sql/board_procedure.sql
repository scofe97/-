create
definer = root@`%` procedure InsertArticlesAndComments()
BEGIN
    DECLARE article_id INT DEFAULT 1; -- Article ID 시작 값
    DECLARE i INT; -- Comment 반복자

    -- Article 데이터 생성
    WHILE article_id <= 300 DO
            INSERT INTO article (id, title, content, author_id, board_id, is_deleted, created_date, updated_date)
            VALUES (article_id, CONCAT('Article ', article_id), CONCAT('Content for Article ', article_id), NULL, 1, FALSE, NOW(), NOW());

            -- Comment 데이터 생성 (각 Article에 100개의 Comment 추가)
            SET i = 1;
            WHILE i <= 30 DO
                    INSERT INTO comment (content, article_id, is_deleted, created_date, updated_date)
                    VALUES (CONCAT('Comment ', i, ' for Article ', article_id), article_id,FALSE, NOW(), NOW());
                    SET i = i + 1; -- Comment ID 증가
END WHILE;

            SET article_id = article_id + 1; -- Article ID 증가
END WHILE;
END;