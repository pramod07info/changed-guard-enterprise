CREATE TABLE application_user (
    username varchar(100) PRIMARY KEY,
    password_hash varchar(255) NOT NULL,
    tenant_id varchar(100) NOT NULL CHECK (tenant_id ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$'),
    enabled boolean NOT NULL DEFAULT true
);
