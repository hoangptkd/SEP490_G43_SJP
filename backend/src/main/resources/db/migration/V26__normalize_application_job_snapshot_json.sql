DO $$
DECLARE
  snapshot_record record;
  normalized_snapshot jsonb;
BEGIN
  FOR snapshot_record IN
    SELECT id, job_snapshot_json #>> '{}' AS raw_snapshot
    FROM applications
    WHERE job_snapshot_json IS NOT NULL
      AND jsonb_typeof(job_snapshot_json) = 'string'
  LOOP
    BEGIN
      normalized_snapshot := snapshot_record.raw_snapshot::jsonb;

      IF jsonb_typeof(normalized_snapshot) = 'object' THEN
        UPDATE applications
        SET job_snapshot_json = normalized_snapshot
        WHERE id = snapshot_record.id;
      END IF;
    EXCEPTION WHEN others THEN
      NULL;
    END;
  END LOOP;
END $$;
