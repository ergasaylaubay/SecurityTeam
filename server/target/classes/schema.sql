CREATE TABLE IF NOT EXISTS app_user (
 id UUID PRIMARY KEY, username VARCHAR(32) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL, role VARCHAR(16) NOT NULL DEFAULT 'USER',
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE', created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS chat (
 id UUID PRIMARY KEY, direct_key VARCHAR(80) NOT NULL UNIQUE,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE TABLE IF NOT EXISTS chat_member (
 chat_id UUID NOT NULL REFERENCES chat(id), user_id UUID NOT NULL REFERENCES app_user(id),
 PRIMARY KEY(chat_id, user_id)
);
CREATE TABLE IF NOT EXISTS message (
 id UUID PRIMARY KEY, chat_id UUID NOT NULL REFERENCES chat(id),
 sender_id UUID NOT NULL REFERENCES app_user(id), client_message_id UUID NOT NULL,
 ciphertext BYTEA NOT NULL, nonce BYTEA NOT NULL, key_id VARCHAR(32) NOT NULL,
 payload_hash VARCHAR(64) NOT NULL, created_at TIMESTAMP WITH TIME ZONE NOT NULL,
 UNIQUE(sender_id, client_message_id)
);
CREATE INDEX IF NOT EXISTS message_chat_time ON message(chat_id, created_at, id);
CREATE TABLE IF NOT EXISTS audit_event (
 id UUID PRIMARY KEY, actor_id UUID REFERENCES app_user(id), action VARCHAR(64) NOT NULL,
 target_id VARCHAR(80), created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
