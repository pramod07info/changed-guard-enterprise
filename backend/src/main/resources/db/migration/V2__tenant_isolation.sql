-- Existing unowned history is quarantined. This value cannot pass the JWT tenant validator.
ALTER TABLE contract_snapshot ADD COLUMN tenant_id VARCHAR(100);
ALTER TABLE analysis_report ADD COLUMN tenant_id VARCHAR(100);
UPDATE contract_snapshot SET tenant_id = '__legacy_unassigned__';
UPDATE analysis_report SET tenant_id = '__legacy_unassigned__';
ALTER TABLE contract_snapshot ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE analysis_report ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE contract_snapshot ADD CONSTRAINT snapshot_tenant_nonempty CHECK (length(trim(tenant_id)) > 0);
ALTER TABLE analysis_report ADD CONSTRAINT report_tenant_nonempty CHECK (length(trim(tenant_id)) > 0);
ALTER TABLE contract_snapshot ADD CONSTRAINT snapshot_tenant_id_unique UNIQUE (tenant_id, id);
ALTER TABLE analysis_report ADD CONSTRAINT report_before_tenant_fk
    FOREIGN KEY (tenant_id, before_snapshot_id) REFERENCES contract_snapshot(tenant_id, id);
ALTER TABLE analysis_report ADD CONSTRAINT report_after_tenant_fk
    FOREIGN KEY (tenant_id, after_snapshot_id) REFERENCES contract_snapshot(tenant_id, id);
DROP INDEX analysis_service_history_idx;
CREATE INDEX analysis_tenant_service_history_idx ON analysis_report(tenant_id, service, created_at DESC, id DESC);
