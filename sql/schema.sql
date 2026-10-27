-- Money Transfer System - schema (MySQL 8.0.16+ required: CHECK constraints are enforced)
CREATE DATABASE IF NOT EXISTS moneytransfer_db CHARACTER SET utf8mb4;
USE moneytransfer_db;

CREATE TABLE IF NOT EXISTS users (
    user_id         INT          NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(150) NOT NULL,
    password_hash   VARCHAR(255) NOT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    DATETIME     NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS accounts (
    account_id INT           NOT NULL AUTO_INCREMENT,
    user_id    INT           NOT NULL,
    balance    DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    status     ENUM('ACTIVE','FROZEN') NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (account_id),
    KEY idx_accounts_user (user_id),
    CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (user_id),
    -- Last line of defence: even a buggy query can never make a balance negative.
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id      BIGINT        NOT NULL AUTO_INCREMENT,
    sender_account_id   INT           NOT NULL,
    receiver_account_id INT           NOT NULL,
    amount              DECIMAL(15,2) NOT NULL,
    request_id          VARCHAR(64)   NOT NULL,
    created_at          TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (transaction_id),
    -- Idempotency: the DATABASE guarantees one transfer per request id, even under races.
    UNIQUE KEY uq_transactions_request (request_id),
    KEY idx_tx_sender   (sender_account_id),
    KEY idx_tx_receiver (receiver_account_id),
    CONSTRAINT fk_tx_sender   FOREIGN KEY (sender_account_id)   REFERENCES accounts (account_id),
    CONSTRAINT fk_tx_receiver FOREIGN KEY (receiver_account_id) REFERENCES accounts (account_id),
    CONSTRAINT chk_tx_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_tx_different_accounts CHECK (sender_account_id <> receiver_account_id)
) ENGINE=InnoDB;

-- Double-entry ledger: every transfer writes one DEBIT and one CREDIT row, each with the
-- account's balance after the entry. This gives an auditable statement with running balance.
CREATE TABLE IF NOT EXISTS ledger_entries (
    entry_id       BIGINT        NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT        NOT NULL,
    account_id     INT           NOT NULL,
    entry_type     ENUM('DEBIT','CREDIT') NOT NULL,
    amount         DECIMAL(15,2) NOT NULL,
    balance_after  DECIMAL(15,2) NOT NULL,
    created_at     TIMESTAMP(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (entry_id),
    KEY idx_ledger_account (account_id, entry_id),
    KEY idx_ledger_tx (transaction_id),
    CONSTRAINT fk_ledger_tx      FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id),
    CONSTRAINT fk_ledger_account FOREIGN KEY (account_id)     REFERENCES accounts (account_id)
) ENGINE=InnoDB;
