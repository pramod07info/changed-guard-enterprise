ALTER TABLE github_webhook_delivery ADD CONSTRAINT github_delivery_tenant_unique
    UNIQUE (tenant_id, subscription_id, delivery_id);

CREATE TABLE github_check_publication (
    tenant_id varchar(100) NOT NULL,
    subscription_id uuid NOT NULL,
    delivery_id uuid NOT NULL,
    installation_id bigint NOT NULL CHECK (installation_id > 0),
    state varchar(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (state IN ('QUEUED','PROCESSING','PUBLISHED','FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
    lease_token uuid,
    available_at timestamptz NOT NULL DEFAULT now(),
    check_run_id bigint CHECK (check_run_id > 0),
    error_code varchar(50),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (subscription_id, delivery_id),
    FOREIGN KEY (tenant_id, subscription_id, delivery_id)
        REFERENCES github_webhook_delivery(tenant_id, subscription_id, delivery_id),
    CHECK (state <> 'PUBLISHED' OR check_run_id IS NOT NULL)
);
CREATE INDEX github_check_pending_idx ON github_check_publication(available_at)
    WHERE state IN ('QUEUED','PROCESSING');
