CREATE TABLE database_schema_analysis (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL CHECK (length(trim(tenant_id)) > 0),
    service varchar(100) NOT NULL,
    created_at timestamptz NOT NULL,
    before_schema jsonb NOT NULL CHECK (jsonb_typeof(before_schema) = 'object'),
    after_schema jsonb NOT NULL CHECK (jsonb_typeof(after_schema) = 'object'),
    dependencies jsonb NOT NULL CHECK (jsonb_typeof(dependencies) = 'object'),
    report jsonb NOT NULL CHECK (jsonb_typeof(report) = 'object'),
    UNIQUE (tenant_id,id)
);
CREATE INDEX database_schema_history_idx ON database_schema_analysis(tenant_id,service,created_at DESC,id DESC);
