ALTER TABLE analysis_report ADD COLUMN github_source jsonb;
ALTER TABLE analysis_report ADD CONSTRAINT github_source_is_object CHECK
    (github_source IS NULL OR (baseline_id IS NOT NULL AND jsonb_typeof(github_source) = 'object'));
