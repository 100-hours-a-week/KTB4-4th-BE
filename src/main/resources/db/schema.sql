-- NeedU Backend 스키마 (MySQL 8.4)
-- 현재 구현된 Entity 11개 기준. ddl-auto=validate가 이 스키마를 검증한다.
--
-- 규칙
--  * Entity를 바꾸면 이 파일도 함께 바꾼다. 어긋나면 애플리케이션이 부팅에 실패한다
--  * 재기동마다 실행되므로 모든 문장은 CREATE TABLE IF NOT EXISTS로 작성한다
--    이미 만들어진 테이블의 컬럼 추가·변경은 이 파일이 처리하지 못하므로 별도 ALTER로 반영한다
--  * 시각 컬럼은 datetime(6). 애플리케이션이 UTC로 저장한다
--    (application.properties: hibernate.jdbc.time_zone=UTC / compose.yaml: TZ=UTC)
--  * boolean은 tinyint(1). Connector/J 기본 설정(tinyInt1isBit=true)이 Boolean으로 매핑한다
--  * Enum은 MySQL ENUM 대신 varchar. 상수 추가 시 ALTER TABLE이 필요 없도록 한다
--    길이는 Entity의 @Column(length) 값을 따랐고, 미지정 컬럼은 Hibernate 기본값 255를 썼다
--  * Boot 기본 경로(classpath:schema.sql)가 아닌 db/ 아래에 둔다
--    기본 경로에 두면 H2 테스트에서 자동 실행돼 깨진다

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- user
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    id                       BIGINT        NOT NULL AUTO_INCREMENT,
    external_id              BIGINT        NULL COMMENT '카카오 회원번호',
    nickname                 VARCHAR(50)   NOT NULL,
    profile_image_url        VARCHAR(2048) NULL,
    gender                   VARCHAR(255)  NOT NULL COMMENT 'MALE, FEMALE, NONE',
    birth_date               DATE          NULL,
    onboarding_completed     TINYINT(1)    NOT NULL,
    taste_analysis_completed TINYINT(1)    NOT NULL,
    status                   VARCHAR(30)   NOT NULL COMMENT 'ONBOARDING, ACTIVE, BLOCKED, WITHDRAWN',
    blocked_at               DATETIME(6)   NULL,
    last_login_at            DATETIME(6)   NULL,
    kakao_friend_synced_at   DATETIME(6)   NULL,
    created_at               DATETIME(6)   NOT NULL,
    updated_at               DATETIME(6)   NOT NULL,
    deleted_at               DATETIME(6)   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_external_id (external_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- users와 1:1 (@MapsId). PK가 곧 users.id
CREATE TABLE IF NOT EXISTS user_taste_profiles (
    user_id           BIGINT      NOT NULL,
    recent_tastes     TEXT        NULL,
    recent_interests  TEXT        NULL,
    ai_summary        TEXT        NULL,
    onboarding_tastes JSON        NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_taste_profiles_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- auth
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS auth_sessions (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    user_id            BIGINT        NOT NULL,
    refresh_token_hash VARBINARY(32) NOT NULL COMMENT 'SHA-256. 원문 토큰은 저장하지 않는다',
    device_name        VARCHAR(100)  NULL,
    ip_address         VARBINARY(16) NULL COMMENT 'IPv4 4바이트 / IPv6 16바이트',
    refresh_expires_at DATETIME(6)   NOT NULL,
    last_used_at       DATETIME(6)   NULL,
    revoked_at         DATETIME(6)   NULL,
    revoke_reason      VARCHAR(255)  NULL COMMENT 'LOGOUT, WITHDRAWN, SECURITY, EXPIRED',
    created_at         DATETIME(6)   NOT NULL,
    updated_at         DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_sessions_refresh_token_hash (refresh_token_hash)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- friend
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS friends (
    id             BIGINT     NOT NULL AUTO_INCREMENT,
    owner_user_id  BIGINT     NOT NULL,
    friend_user_id BIGINT     NOT NULL,
    is_favorite    TINYINT(1) NOT NULL,
    PRIMARY KEY (id),
    -- owner_user_id 단독 조회도 이 인덱스의 선두 컬럼으로 처리된다
    UNIQUE KEY uk_friends_owner_friend (owner_user_id, friend_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- aichat
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS ai_chat_rooms (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    -- 진행 중(PENDING/ACTIVE/ANALYZING)인 방에만 값이 있다. 유일 제약으로 사용자당 진행 중인 방을 1개로 제한
    active_user_id BIGINT      NULL,
    status         VARCHAR(20) NOT NULL COMMENT 'PENDING, ACTIVE, ANALYZING, COMPLETED, EXPIRED',
    purge_at       DATETIME(6) NULL COMMENT 'AI 세션 만료 예상 시각 = 대화 원문 삭제 예정 시각',
    version        BIGINT      NOT NULL COMMENT '낙관적 락(@Version)',
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    deleted_at     DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_chat_rooms_active_user_id (active_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_messages (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    ai_chat_room_id     BIGINT      NOT NULL,
    -- 클라이언트가 만든 중복 방지 식별자. AI 메시지(인사말/답변)에는 없다
    client_message_id   VARCHAR(36) NULL,
    -- AI 답변이 어떤 사용자 메시지에 대한 것인지. 사용자 메시지와 인사말에는 없다
    reply_to_message_id BIGINT      NULL,
    sender_type         VARCHAR(20) NOT NULL COMMENT 'USER, AI',
    content             TEXT        NOT NULL,
    progress            INT         NULL COMMENT 'AI 답변 생성 진행률(0~100)',
    input_locked        TINYINT(1)  NULL COMMENT 'AI 답변 이후 사용자 입력 잠금 여부',
    created_at          DATETIME(6) NOT NULL,
    deleted_at          DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_messages_room_client_message_id (ai_chat_room_id, client_message_id),
    -- 메시지 목록 커서 조회(where ai_chat_room_id = ? and id < ? order by id desc)
    KEY idx_ai_messages_room_id_id (ai_chat_room_id, id),
    -- 중복 요청 시 저장된 답변 재조회(findByReplyToMessageId)
    KEY idx_ai_messages_reply_to_message_id (reply_to_message_id),
    CONSTRAINT fk_ai_messages_room FOREIGN KEY (ai_chat_room_id) REFERENCES ai_chat_rooms (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 기존 테이블에도 메시지 응답 메타데이터 컬럼을 추가한다.
SET @add_progress = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_messages ADD COLUMN progress INT NULL COMMENT ''AI 답변 생성 진행률(0~100)''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_messages'
      AND column_name = 'progress'
);
PREPARE add_progress_statement FROM @add_progress;
EXECUTE add_progress_statement;
DEALLOCATE PREPARE add_progress_statement;

SET @add_input_locked = (
    SELECT IF(COUNT(*) = 0,
              'ALTER TABLE ai_messages ADD COLUMN input_locked TINYINT(1) NULL COMMENT ''AI 답변 이후 사용자 입력 잠금 여부''',
              'SELECT 1')
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'ai_messages'
      AND column_name = 'input_locked'
);
PREPARE add_input_locked_statement FROM @add_input_locked;
EXECUTE add_input_locked_statement;
DEALLOCATE PREPARE add_input_locked_statement;

-- ---------------------------------------------------------------------------
-- product
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS products (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    platform_type VARCHAR(20)    NOT NULL COMMENT 'COUPANG, KAKAO, ELEVEN_STREET, HOMEPLUS',
    external_id   VARCHAR(255)   NOT NULL COMMENT '플랫폼의 상품 ID',
    name          VARCHAR(300)   NOT NULL,
    category      VARCHAR(100)   NULL,
    description   TEXT           NULL,
    price         DECIMAL(12, 2) NULL,
    image_url     VARCHAR(2048)  NULL,
    purchase_url  VARCHAR(2048)  NULL,
    status        VARCHAR(20)    NOT NULL COMMENT 'ACTIVE, SOLD_OUT, UNAVAILABLE',
    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    deleted_at    DATETIME(6)    NULL,
    PRIMARY KEY (id),
    -- AI 추천 결과의 (platform, externalId)로 상품을 찾는다(findByPlatformTypeAndExternalId)
    UNIQUE KEY uk_products_platform_external_id (platform_type, external_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 내 추천 상품
CREATE TABLE IF NOT EXISTS personal_recommendations (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    user_id    BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    score      DECIMAL(8, 6) NOT NULL,
    reason     VARCHAR(500)  NULL,
    created_at DATETIME(6)   NOT NULL,
    updated_at DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    -- 커서 페이지네이션(order by score desc, id desc)은 이 인덱스를 역방향으로 스캔한다
    KEY idx_personal_recommendations_user_id_score_id (user_id, score, id),
    CONSTRAINT fk_personal_recommendations_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- 친구 선물 추천
CREATE TABLE IF NOT EXISTS gift_recommendations (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    user_id        BIGINT        NOT NULL COMMENT '추천 대상(친구)의 사용자 ID',
    product_id     BIGINT        NOT NULL,
    score          DECIMAL(8, 6) NOT NULL,
    reason         VARCHAR(500)  NULL,
    taste_keywords JSON          NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_gift_recommendations_user_id_score_id (user_id, score, id),
    CONSTRAINT fk_gift_recommendations_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- feedback
-- ---------------------------------------------------------------------------

-- 로그 성격의 테이블이라 user_id에 외래키를 두지 않는다
CREATE TABLE IF NOT EXISTS error_reports (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    idempotency_key VARCHAR(36)  NOT NULL COMMENT '클라이언트가 만든 UUID. 같은 신고의 중복 저장을 막는다',
    problem_type    VARCHAR(50)  NOT NULL,
    detail          VARCHAR(500) NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_error_reports_user_id_idempotency_key (user_id, idempotency_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- taste
-- ---------------------------------------------------------------------------

-- 주의: Entity만 정의돼 있고 이 테이블을 읽거나 쓰는 Repository/Service가 아직 없다.
-- room_id는 @GeneratedValue가 없어 ai_chat_rooms.id를 그대로 쓰는 구조지만, 외래키는 선언돼 있지 않다.
CREATE TABLE IF NOT EXISTS taste_analyses (
    room_id          BIGINT       NOT NULL COMMENT 'ai_chat_rooms.id',
    status           VARCHAR(20)  NOT NULL COMMENT 'QUEUED, RUNNING, SUCCEEDED, FAILED',
    progress         INT          NOT NULL,
    progress_message VARCHAR(255) NULL,
    candidate_result TEXT         NULL,
    decision         VARCHAR(20)  NOT NULL COMMENT 'PENDING, ACCEPTED, REJECTED',
    completed_at     DATETIME(6)  NULL,
    decided_at       DATETIME(6)  NULL,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (room_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
