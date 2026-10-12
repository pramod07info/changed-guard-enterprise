ALTER TABLE analysis_report ADD CONSTRAINT analysis_tenant_id_unique UNIQUE (tenant_id, id);

CREATE TABLE github_webhook_subscription (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    baseline_id uuid NOT NULL,
    repository varchar(201) NOT NULL,
    contract_path varchar(500) NOT NULL,
    dependencies jsonb NOT NULL CHECK (jsonb_typeof(dependencies) = 'object'),
    created_by varchar(200) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, baseline_id, contract_path),
    FOREIGN KEY (tenant_id, baseline_id) REFERENCES contract_baseline(tenant_id, id)
);
CREATE TABLE github_webhook_delivery (
    subscription_id uuid NOT NULL,
    tenant_id varchar(100) NOT NULL,
    delivery_id uuid NOT NULL,
    payload_hash varchar(64) NOT NULL,
    pull_number integer NOT NULL CHECK (pull_number > 0),
    head_sha varchar(40) NOT NULL CHECK (head_sha ~ '^[a-f0-9]{40}$'),
    state varchar(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (state IN ('QUEUED','PROCESSING','SUCCEEDED','SUPERSEDED','FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
    lease_token uuid,
    available_at timestamptz NOT NULL DEFAULT now(),
    analysis_id uuid,
    error_code varchar(50),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (subscription_id, delivery_id),
    FOREIGN KEY (tenant_id, subscription_id) REFERENCES github_webhook_subscription(tenant_id, id),
    FOREIGN KEY (tenant_id, analysis_id) REFERENCES analysis_report(tenant_id, id),
    CHECK ((state = 'SUCCEEDED') = (analysis_id IS NOT NULL))
);
CREATE INDEX github_webhook_pending_idx ON github_webhook_delivery(available_at)
    WHERE state IN ('QUEUED','PROCESSING');
