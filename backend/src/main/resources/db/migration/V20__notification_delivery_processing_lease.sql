-- Fence late notification workers after PROCESSING timeout recovery.
set local lock_timeout = '5s';
set local statement_timeout = '2min';

alter table psy_notification_delivery add column if not exists processing_token varchar(64);

do $$
begin
    if exists (
        select 1 from psy_notification_delivery
        where delivery_channel = 'PUSH' and delivery_status = 'PROCESSING'
    ) then
        raise exception using
            message = 'V20 requires legacy PROCESSING push deliveries to be drained or explicitly failed before migration',
            hint = 'Stop old notification workers, inspect PROCESSING rows, then retry without deleting delivery history.';
    end if;
end
$$;

alter table psy_notification_delivery
    add constraint ck_psy_notification_delivery_processing_lease
    check (
        (delivery_channel = 'PUSH' and delivery_status = 'PROCESSING'
            and processing_started_at is not null and processing_token is not null)
        or (not (delivery_channel = 'PUSH' and delivery_status = 'PROCESSING')
            and processing_token is null)
    ) not valid;
alter table psy_notification_delivery validate constraint ck_psy_notification_delivery_processing_lease;
