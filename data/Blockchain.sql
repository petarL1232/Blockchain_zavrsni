CREATE TABLE IF NOT EXISTS blocks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    block_index INTEGER NOT NULL UNIQUE,
    previous_hash TEXT NOT NULL,
    current_hash TEXT NOT NULL UNIQUE,
    created_on INTEGER NOT NULL,

    merkle_root TEXT NOT NULL,
    miner_address TEXT,

    nonce INTEGER NOT NULL,
    difficulty INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS wallets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    address TEXT NOT NULL UNIQUE,
    public_key TEXT NOT NULL UNIQUE,
    private_key TEXT,

    initial_balance_units INTEGER NOT NULL DEFAULT 0,
    is_local INTEGER NOT NULL DEFAULT 0
        CHECK (is_local IN (0, 1))
);

CREATE TABLE IF NOT EXISTS transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    tx_hash TEXT NOT NULL UNIQUE,
    block_id INTEGER,
    position_in_block INTEGER,

    transaction_type TEXT NOT NULL
        CHECK (transaction_type IN ('REGULAR', 'SYSTEM')),

    sender TEXT NOT NULL,
    sender_public_key TEXT,
    receiver TEXT NOT NULL,

    amount_units INTEGER NOT NULL,
    nonce INTEGER NOT NULL,
    signature TEXT,

    received_on INTEGER NOT NULL,

    FOREIGN KEY (block_id)
        REFERENCES blocks(id)
        ON DELETE CASCADE,

    UNIQUE (block_id, position_in_block)
);

CREATE TABLE IF NOT EXISTS peers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,

    node_id TEXT NOT NULL UNIQUE,
    host TEXT NOT NULL,
    port INTEGER NOT NULL
        CHECK (port BETWEEN 1 AND 65535),

    node_type TEXT,
    last_seen INTEGER NOT NULL,

    UNIQUE (host, port)
);