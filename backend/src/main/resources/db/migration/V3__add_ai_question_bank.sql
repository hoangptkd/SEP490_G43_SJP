create table if not exists ai_question_sets (
  id uuid primary key default gen_random_uuid(),
  code text not null,
  title text not null,
  description text,
  target_role text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ai_question_sets_code_unique unique (code)
);

create table if not exists ai_question_bank (
  id uuid primary key default gen_random_uuid(),
  question_set_id uuid not null references ai_question_sets(id) on delete cascade,
  order_index integer not null check (order_index > 0),
  question_type text not null check (question_type in ('behavioral', 'technical', 'situational', 'general')),
  difficulty text check (difficulty is null or difficulty in ('easy', 'medium', 'hard')),
  skill_tag text,
  content text not null,
  time_limit_seconds integer check (time_limit_seconds is null or time_limit_seconds > 0),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ai_question_bank_set_order_unique unique (question_set_id, order_index)
);

create index if not exists ai_question_sets_active_idx on ai_question_sets(is_active);
create index if not exists ai_question_bank_set_active_order_idx on ai_question_bank(question_set_id, is_active, order_index);

drop trigger if exists ai_question_sets_set_updated_at on ai_question_sets;
create trigger ai_question_sets_set_updated_at
before update on ai_question_sets
for each row execute function set_updated_at();

drop trigger if exists ai_question_bank_set_updated_at on ai_question_bank;
create trigger ai_question_bank_set_updated_at
before update on ai_question_bank
for each row execute function set_updated_at();

insert into ai_question_sets (code, title, description, target_role, is_active)
values (
  'backend_java_spring_test',
  'Backend Java Spring Boot - Bộ test',
  'Bộ câu hỏi test cố định cho luồng phỏng vấn Backend Java Spring Boot.',
  'Backend Developer',
  true
)
on conflict (code) do update
set title = excluded.title,
    description = excluded.description,
    target_role = excluded.target_role,
    is_active = excluded.is_active;

with seeded_set as (
  select id from ai_question_sets where code = 'backend_java_spring_test'
)
insert into ai_question_bank (
  question_set_id,
  order_index,
  question_type,
  difficulty,
  skill_tag,
  content,
  time_limit_seconds,
  is_active
)
select seeded_set.id, seed.order_index, seed.question_type, seed.difficulty, seed.skill_tag, seed.content, 180, true
from seeded_set
cross join (
  values
    (1, 'general', 'easy', 'Java/Spring Boot', 'Hãy giới thiệu ngắn gọn về kinh nghiệm backend Java/Spring Boot của bạn và dự án gần nhất bạn tham gia.'),
    (2, 'technical', 'medium', 'Spring Boot', 'Trong Spring Boot, bạn thường tổ chức Controller, Service, Repository như thế nào để code dễ bảo trì?'),
    (3, 'technical', 'medium', 'REST API', 'Bạn xử lý validation, exception và response lỗi trong REST API như thế nào?'),
    (4, 'situational', 'medium', 'Performance', 'Khi một API bị chậm, bạn sẽ kiểm tra và tối ưu từ database đến application như thế nào?'),
    (5, 'technical', 'hard', 'Transaction', 'Hãy mô tả cách bạn thiết kế transaction cho một nghiệp vụ có nhiều bước ghi dữ liệu.')
) as seed(order_index, question_type, difficulty, skill_tag, content)
on conflict (question_set_id, order_index) do update
set question_type = excluded.question_type,
    difficulty = excluded.difficulty,
    skill_tag = excluded.skill_tag,
    content = excluded.content,
    time_limit_seconds = excluded.time_limit_seconds,
    is_active = excluded.is_active;
