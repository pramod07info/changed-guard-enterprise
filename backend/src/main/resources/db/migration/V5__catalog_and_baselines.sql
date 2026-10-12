CREATE TABLE catalog_project (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    name varchar(200) NOT NULL CHECK (length(trim(name)) > 0),
    created_by varchar(200) NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, name)
);

CREATE TABLE catalog_service (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    project_id uuid NOT NULL,
    name varchar(100) NOT NULL CHECK (name ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$'),
    owner varchar(200) NOT NULL CHECK (length(trim(owner)) > 0),
    repository_url varchar(500),
    created_at timestamptz NOT NULL,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, id, name),
    UNIQUE (tenant_id, name),
    FOREIGN KEY (tenant_id, project_id) REFERENCES catalog_project(tenant_id, id)
);

CREATE TABLE contract_baseline (
    id uuid PRIMARY KEY,
    tenant_id varchar(100) NOT NULL,
    service_id uuid NOT NULL,
    service_name varchar(100) NOT NULL,
    version varchar(100) NOT NULL CHECK (length(trim(version)) > 0),
    environment varchar(100) NOT NULL CHECK (environment ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$'),
    document jsonb NOT NULL CHECK (jsonb_typeof(document) = 'object'),
    published_by varchar(200) NOT NULL,
    published_at timestamptz NOT NULL,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, id, service_name),
    UNIQUE (tenant_id, service_id, environment, version),
    FOREIGN KEY (tenant_id, service_id, service_name) REFERENCES catalog_service(tenant_id, id, name)
);

ALTER TABLE analysis_report ADD COLUMN baseline_id uuid;
ALTER TABLE analysis_report ADD CONSTRAINT analysis_baseline_tenant_service_fk
    FOREIGN KEY (tenant_id, baseline_id, service) REFERENCES contract_baseline(tenant_id, id, service_name);
CREATE INDEX catalog_service_project_idx ON catalog_service(tenant_id, project_id, name);
CREATE INDEX baseline_service_history_idx ON contract_baseline(tenant_id, service_id, published_at DESC, id DESC);
