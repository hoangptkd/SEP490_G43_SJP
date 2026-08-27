-- Candidate plans must not carry employer listingPriority.
UPDATE plans
SET features = jsonb_set(
        COALESCE(features, '{}'::jsonb),
        '{listingPriority}',
        '0'::jsonb,
        true
    ),
    updated_at = now()
WHERE LOWER(target_role) = 'job_seeker'
  AND COALESCE((features->>'listingPriority'), '0') <> '0';
