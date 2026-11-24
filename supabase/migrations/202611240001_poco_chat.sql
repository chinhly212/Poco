-- POCO Chat: use the existing Supabase Auth identity for every device.
-- Apply this migration in the Supabase SQL Editor before enabling the chat UI.

create table if not exists public.poco_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null default '',
    username text not null unique,
    avatar_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.poco_conversations (
    id uuid primary key default gen_random_uuid(),
    is_group boolean not null default false,
    title text,
    created_by uuid not null references auth.users(id) on delete cascade,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table if not exists public.poco_conversation_members (
    conversation_id uuid not null references public.poco_conversations(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    role text not null default 'member' check (role in ('owner', 'admin', 'member')),
    joined_at timestamptz not null default now(),
    last_read_at timestamptz,
    primary key (conversation_id, user_id)
);

create table if not exists public.poco_messages (
    id uuid primary key default gen_random_uuid(),
    conversation_id uuid not null references public.poco_conversations(id) on delete cascade,
    sender_id uuid not null references auth.users(id) on delete cascade,
    body text not null check (char_length(body) between 1 and 10000),
    created_at timestamptz not null default now(),
    edited_at timestamptz,
    deleted_at timestamptz
);

create index if not exists poco_members_user_idx
    on public.poco_conversation_members(user_id, joined_at desc);
create index if not exists poco_messages_conversation_time_idx
    on public.poco_messages(conversation_id, created_at desc);
create index if not exists poco_conversations_updated_idx
    on public.poco_conversations(updated_at desc);

alter table public.poco_profiles enable row level security;
alter table public.poco_conversations enable row level security;
alter table public.poco_conversation_members enable row level security;
alter table public.poco_messages enable row level security;

-- SECURITY DEFINER helper avoids recursive RLS evaluation on the membership table.
create or replace function public.poco_is_conversation_member(target_conversation_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $
    select exists (
        select 1 from public.poco_conversation_members
        where conversation_id = target_conversation_id
          and user_id = auth.uid()
    );
$;

revoke all on function public.poco_is_conversation_member(uuid) from public;
grant execute on function public.poco_is_conversation_member(uuid) to authenticated;

drop policy if exists "poco profiles readable by authenticated" on public.poco_profiles;
create policy "poco profiles readable by authenticated"
    on public.poco_profiles for select to authenticated using (true);

drop policy if exists "poco profile insert own" on public.poco_profiles;
create policy "poco profile insert own"
    on public.poco_profiles for insert to authenticated with check (user_id = auth.uid());

drop policy if exists "poco profile update own" on public.poco_profiles;
create policy "poco profile update own"
    on public.poco_profiles for update to authenticated
    using (user_id = auth.uid()) with check (user_id = auth.uid());

drop policy if exists "poco conversations visible to members" on public.poco_conversations;
create policy "poco conversations visible to members"
    on public.poco_conversations for select to authenticated
    using (public.poco_is_conversation_member(id));

drop policy if exists "poco members visible to conversation members" on public.poco_conversation_members;
create policy "poco members visible to conversation members"
    on public.poco_conversation_members for select to authenticated
    using (public.poco_is_conversation_member(conversation_id));

drop policy if exists "poco messages visible to conversation members" on public.poco_messages;
create policy "poco messages visible to conversation members"
    on public.poco_messages for select to authenticated
    using (public.poco_is_conversation_member(conversation_id));

drop policy if exists "poco members can send messages as themselves" on public.poco_messages;
create policy "poco members can send messages as themselves"
    on public.poco_messages for insert to authenticated
    with check (
        sender_id = auth.uid()
        and public.poco_is_conversation_member(conversation_id)
    );

create or replace function public.poco_create_direct_conversation(other_user_id uuid)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    current_user_id uuid := auth.uid();
    conversation_id uuid;
begin
    if current_user_id is null then
        raise exception 'Authentication required';
    end if;
    if other_user_id is null or other_user_id = current_user_id then
        raise exception 'Choose another valid user';
    end if;
    if not exists (select 1 from public.poco_profiles p where p.user_id = other_user_id) then
        raise exception 'User not found';
    end if;

    select c.id into conversation_id
    from public.poco_conversations c
    where c.is_group = false
      and exists (
          select 1 from public.poco_conversation_members a
          where a.conversation_id = c.id and a.user_id = current_user_id
      )
      and exists (
          select 1 from public.poco_conversation_members b
          where b.conversation_id = c.id and b.user_id = other_user_id
      )
      and (
          select count(*) from public.poco_conversation_members x
          where x.conversation_id = c.id
      ) = 2
    limit 1;

    if conversation_id is null then
        insert into public.poco_conversations(is_group, created_by)
        values (false, current_user_id)
        returning id into conversation_id;

        insert into public.poco_conversation_members(conversation_id, user_id, role)
        values
            (conversation_id, current_user_id, 'owner'),
            (conversation_id, other_user_id, 'member');
    end if;

    return conversation_id;
end;
$$;

revoke all on function public.poco_create_direct_conversation(uuid) from public;
grant execute on function public.poco_create_direct_conversation(uuid) to authenticated;

-- Create a POCO profile automatically for new Supabase Auth users.
create or replace function public.poco_handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    base_name text;
begin
    base_name := coalesce(nullif(new.raw_user_meta_data->>'display_name', ''),
                          nullif(split_part(coalesce(new.email, ''), '@', 1), ''),
                          'POCO User');
    insert into public.poco_profiles(user_id, display_name, username)
    values (
        new.id,
        base_name,
        lower(regexp_replace(base_name, '[^a-zA-Z0-9_]', '', 'g'))
            || '_' || substr(replace(new.id::text, '-', ''), 1, 8)
    )
    on conflict (user_id) do nothing;
    return new;
end;
$$;

drop trigger if exists poco_auth_user_created on auth.users;
create trigger poco_auth_user_created
    after insert on auth.users
    for each row execute procedure public.poco_handle_new_user();

-- Backfill profiles for accounts that already existed before this migration.
insert into public.poco_profiles(user_id, display_name, username)
select
    u.id,
    coalesce(nullif(u.raw_user_meta_data->>'display_name', ''),
             nullif(split_part(coalesce(u.email, ''), '@', 1), ''),
             'POCO User'),
    lower(regexp_replace(
        coalesce(nullif(u.raw_user_meta_data->>'display_name', ''),
                 nullif(split_part(coalesce(u.email, ''), '@', 1), ''),
                 'poco_user'),
        '[^a-zA-Z0-9_]', '', 'g'
    )) || '_' || substr(replace(u.id::text, '-', ''), 1, 8)
from auth.users u
on conflict (user_id) do nothing;

-- Enable Realtime for message inserts (safe to run more than once).
do $$
begin
    if not exists (
        select 1 from pg_publication_tables
        where pubname = 'supabase_realtime'
          and schemaname = 'public'
          and tablename = 'poco_messages'
    ) then
        alter publication supabase_realtime add table public.poco_messages;
    end if;
end;
$$;


-- Create group conversations atomically. The creator is always the owner.
create or replace function public.poco_create_group(
    group_title text,
    member_user_ids uuid[]
)
returns uuid
language plpgsql
security definer
set search_path = public
as $$
declare
    current_user_id uuid := auth.uid();
    conversation_id uuid;
    requested_count integer;
    found_count integer;
begin
    if current_user_id is null then
        raise exception 'Authentication required';
    end if;

    if nullif(trim(coalesce(group_title, '')), '') is null
       or char_length(trim(group_title)) > 80 then
        raise exception 'Group name must contain 1 to 80 characters';
    end if;

    -- Always include the creator, remove duplicates and reject an empty group.
    select count(*) into requested_count
    from (
        select distinct member_id
        from unnest(coalesce(member_user_ids, array[]::uuid[]) || array[current_user_id]) as member_id
        where member_id is not null
    ) members;

    if requested_count < 2 or requested_count > 100 then
        raise exception 'A group must contain 2 to 100 members';
    end if;

    select count(*) into found_count
    from public.poco_profiles p
    where p.user_id = any(coalesce(member_user_ids, array[]::uuid[]) || array[current_user_id]);

    if found_count <> requested_count then
        raise exception 'One or more users do not exist';
    end if;

    insert into public.poco_conversations(is_group, title, created_by)
    values (true, trim(group_title), current_user_id)
    returning id into conversation_id;

    insert into public.poco_conversation_members(conversation_id, user_id, role)
    select
        conversation_id,
        members.user_id,
        case when members.user_id = current_user_id then 'owner' else 'member' end
    from (
        select distinct member_id as user_id
        from unnest(coalesce(member_user_ids, array[]::uuid[]) || array[current_user_id]) as member_id
        where member_id is not null
    ) members;

    return conversation_id;
end;
$$;

revoke all on function public.poco_create_group(text, uuid[]) from public;
grant execute on function public.poco_create_group(text, uuid[]) to authenticated;

-- Keep conversation ordering current whenever a new message is sent.
create or replace function public.poco_touch_conversation_on_message()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.poco_conversations
       set updated_at = new.created_at
     where id = new.conversation_id;
    return new;
end;
$$;

drop trigger if exists poco_message_updates_conversation on public.poco_messages;
create trigger poco_message_updates_conversation
    after insert on public.poco_messages
    for each row execute procedure public.poco_touch_conversation_on_message();
