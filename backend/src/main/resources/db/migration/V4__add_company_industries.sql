-- 1. Tạo bảng trung gian (IF NOT EXISTS để an toàn khi đã chạy thủ công trước đó)
CREATE TABLE IF NOT EXISTS company_industries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    category_id uuid NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    is_primary boolean NOT NULL DEFAULT false,
    created_at timestamptz DEFAULT now(),
    updated_at timestamptz DEFAULT now(),

    CONSTRAINT unique_company_category UNIQUE (company_id, category_id)
);

-- 2. Trigger updated_at
DROP TRIGGER IF EXISTS company_industries_set_updated_at ON company_industries;
CREATE TRIGGER company_industries_set_updated_at
BEFORE UPDATE ON company_industries
FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- 3. Index
CREATE INDEX IF NOT EXISTS idx_company_industries_company ON company_industries(company_id);
CREATE INDEX IF NOT EXISTS idx_company_industries_category ON company_industries(category_id);
