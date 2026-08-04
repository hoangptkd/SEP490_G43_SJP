-- Drop the old AI ranking trigger and function that checked ranking_job_id
DROP TRIGGER IF EXISTS ai_ranking_results_check_job ON ai_ranking_results;
DROP FUNCTION IF EXISTS check_ai_ranking_result_job();
