DROP TABLE IF EXISTS red_pack_order;
CREATE TABLE red_pack_order (
    id BIGINT PRIMARY KEY,
    owner_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    red_pack_type INT NOT NULL,
    total_money BIGINT NOT NULL,
    total_red_pack_num INT NOT NULL,
    red_pack_order_status INT NOT NULL,
    created TIMESTAMP NOT NULL,
    modified TIMESTAMP NOT NULL
);
DROP TABLE IF EXISTS red_pack;
CREATE TABLE red_pack (
    id BIGINT PRIMARY KEY,
    red_pack_order_id BIGINT,
    owner_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    red_pack_type INT NOT NULL,
    total_money BIGINT NOT NULL,
    total_red_pack_num INT NOT NULL,
    red_pack_status INT NOT NULL,
    created TIMESTAMP NOT NULL,
    modified TIMESTAMP NOT NULL
);
DROP TABLE IF EXISTS red_pack_record;
CREATE TABLE red_pack_record (
    id BIGINT NOT NULL PRIMARY KEY,
    red_pack_id BIGINT NOT NULL,
    money BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    red_pack_record_status INT NOT NULL,
    created TIMESTAMP NOT NULL,
    modified TIMESTAMP NOT NULL
);