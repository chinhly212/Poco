-- MuChi synchronized account profiles and public avatar storage.
-- Existing chat continues using poco_profiles; public.profiles is the admin-facing mirror.

alter table public.poco_profiles
    add column if not exists avatar_url text,
    add column if not exists nickname text not null default '';

-- Admin-facing canonical profile mirror. Keep account_id stable and searchable.
create table if not exists public.profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    account_id text not null unique,
    display_name text not null default '',
    nickname text not null default '',
    avatar_url text,
    updated_at timestamptz not null default now()
);

-- Also handle projects where public.profiles already existed with a partial schema.
alter table public.profiles
    add column if not exists user_id uuid references auth.users(id) on delete cascade,
    add column if not exists account_id text,
    add column if not exists display_name text not null default '',
    add column if not exists nickname text not null default '',
    add column if not exists avatar_url text,
    add column if not exists updated_at timestamptz not null default now();

create unique index if not exists muchi_profiles_user_id_uidx
    on public.profiles(user_id);
create unique index if not exists muchi_profiles_account_id_uidx
    on public.profiles(account_id)
    where account_id is not null;

alter table public.profiles enable row level security;
grant select on public.profiles to authenticated;

drop policy if exists "MuChi profiles readable by authenticated" on public.profiles;
create policy "MuChi profiles readable by authenticated"
    on public.profiles for select to authenticated using (true);

-- Only the signed-in user can modify their chat profile. The mirror is maintained
-- by a SECURITY DEFINER trigger, not by a client-side write.
create or replace function public.muchi_sync_profile_mirror()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles(user_id, account_id, display_name, nickname, avatar_url, updated_at)
    values (new.user_id, new.account_id, coalesce(new.display_name, ''),
            coalesce(new.nickname, ''), new.avatar_url, coalesce(new.updated_at, now()))
    on conflict (user_id) do update set
        account_id = excluded.account_id,
        display_name = excluded.display_name,
        nickname = excluded.nickname,
        avatar_url = excluded.avatar_url,
        updated_at = excluded.updated_at;
    return new;
end;
$$;

revoke all on function public.muchi_sync_profile_mirror() from public;

drop trigger if exists muchi_sync_profile_mirror_trigger on public.poco_profiles;
create trigger muchi_sync_profile_mirror_trigger
    after insert or update
    on public.poco_profiles
    for each row execute procedure public.muchi_sync_profile_mirror();

-- Backfill all existing accounts, including profiles created before this migration.
insert into public.profiles(user_id, account_id, display_name, nickname, avatar_url, updated_at)
select user_id, account_id, coalesce(display_name, ''), coalesce(nickname, ''),
       avatar_url, coalesce(updated_at, now())
from public.poco_profiles
on conflict (user_id) do update set
    account_id = excluded.account_id,
    display_name = excluded.display_name,
    nickname = excluded.nickname,
    avatar_url = excluded.avatar_url,
    updated_at = excluded.updated_at;

-- Public reads allow fast avatar rendering in chat. Writes are restricted to
-- the authenticated user's own folder: avatars/<auth.uid()>/avatar.jpg.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 2097152, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update set
    public = true,
    file_size_limit = 2097152,
    allowed_mime_types = array['image/jpeg', 'image/png', 'image/webp'];

drop policy if exists "MuChi avatar public read" on storage.objects;
create policy "MuChi avatar public read"
    on storage.objects for select
    using (bucket_id = 'avatars');

drop policy if exists "MuChi avatar insert own folder" on storage.objects;
create policy "MuChi avatar insert own folder"
    on storage.objects for insert to authenticated
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "MuChi avatar update own folder" on storage.objects;
create policy "MuChi avatar update own folder"
    on storage.objects for update to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

drop policy if exists "MuChi avatar delete own folder" on storage.objects;
create policy "MuChi avatar delete own folder"
    on storage.objects for delete to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

comment on table public.profiles is
    'Admin-facing synchronized MuChi profile directory. account_id uses MC-A plus six digits.';
