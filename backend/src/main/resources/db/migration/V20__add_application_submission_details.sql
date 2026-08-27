alter table applications
  add column if not exists preferred_location text,
  add column if not exists cover_letter text;
