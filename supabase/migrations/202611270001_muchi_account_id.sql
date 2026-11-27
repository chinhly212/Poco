-- MuChi account IDs: stable, searchable public account identifiers.
-- Format: MC-A followed by exactly six digits (example: MC-A710123).

alter table public.poco_profiles
    add column if not exists account_id text;

-- Assign an ID to every profile that existed before this migration.
do $$
declare
    profile_row record;
    candidate text;
begin
    for profile_row in
        select user_id from public.poco_profiles where account_id is null
    loop
        loop
            candidate := 'MC-A' || (100000 + floor(random() * 900000)::integer)::text;
            exit when not exists (
                select 1 from public.poco_profiles where account_id = candidate
            );
        end loop;

        update public.poco_profiles
           set account_id = candidate
         where user_id = profile_row.user_id;
    end loop;
end;
$$;

create unique index if not exists poco_profiles_account_id_uidx
    on public.poco_profiles(account_id);

alter table public.poco_profiles
    alter column account_id set not null;

do $$
begin
    if not exists (
        select 1
          from pg_constraint
         where conname = 'poco_profiles_account_id_format'
           and conrelid = 'public.poco_profiles'::regclass
    ) then
        alter table public.poco_profiles
            add constraint poco_profiles_account_id_format
            check (account_id ~ '^MC-A[0-9]{6}$');
    end if;
end;
$$;

-- Generate a collision-checked ID for every newly registered Supabase Auth user.
create or replace function public.poco_generate_account_id()
returns text
language plpgsql
volatile
security definer
set search_path = public
as $$
declare
    candidate text;
begin
    loop
        candidate := 'MC-A' || (100000 + floor(random() * 900000)::integer)::text;
        exit when not exists (
            select 1 from public.poco_profiles where account_id = candidate
        );
    end loop;
    return candidate;
end;
$$;

revoke all on function public.poco_generate_account_id() from public;

-- Keep automatic profile creation, now with a permanent MuChi ID.
create or replace function public.poco_handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    base_name text;
begin
    base_name := coalesce(
        nullif(new.raw_user_meta_data->>'display_name', ''),
        nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
        'MuChi User'
    );

    insert into public.poco_profiles(user_id, display_name, username, account_id)
    values (
        new.id,
        base_name,
        lower(regexp_replace(base_name, '[^a-zA-Z0-9_]', '', 'g'))
            || '_' || substr(replace(new.id::text, '-', ''), 1, 8),
        public.poco_generate_account_id()
    )
    on conflict (user_id) do nothing;

    return new;
end;
$$;

drop trigger if exists poco_auth_user_created on auth.users;
create trigger poco_auth_user_created
    after insert on auth.users
    for each row execute procedure public.poco_handle_new_user();

comment on column public.poco_profiles.account_id is
    'Permanent MuChi account ID. Format MC-A plus six digits; searchable by other authenticated users.';
