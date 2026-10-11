CREATE TABLE contract_snapshot (
    id UUID PRIMARY KEY,
    service VARCHAR(200) NOT NULL CHECK (length(trim(service)) > 0),
    created_at TIMESTAMPTZ NOT NULL,
    document JSONB NOT NULL CHECK (jsonb_typeof(document) = 'object')
);

CREATE TABLE analysis_report (
    id UUID PRIMARY KEY,
    service VARCHAR(200) NOT NULL CHECK (length(trim(service)) > 0),
    created_at TIMESTAMPTZ NOT NULL,
    before_snapshot_id UUID NOT NULL REFERENCES contract_snapshot(id),
    after_snapshot_id UUID NOT NULL REFERENCES contract_snapshot(id),
    report JSONB NOT NULL CHECK (jsonb_typeof(report) = 'object'),
    dependencies JSONB NOT NULL CHECK (jsonb_typeof(dependencies) = 'object'),
    CHECK (before_snapshot_id <> after_snapshot_id)
);

CREATE INDEX analysis_service_history_idx ON analysis_report(service, created_at DESC, id DESC);
