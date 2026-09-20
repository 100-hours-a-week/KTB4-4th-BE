ALTER TABLE users
    ADD COLUMN kakao_friend_synced_at DATETIME(6) NULL;

ALTER TABLE friends
    ADD CONSTRAINT uk_friends_owner_friend UNIQUE (owner_user_id, friend_user_id);
