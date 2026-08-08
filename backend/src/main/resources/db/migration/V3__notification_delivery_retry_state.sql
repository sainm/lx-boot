alter table psy_notification_delivery
    add column if not exists retry_count integer not null default 0;
alter table psy_notification_delivery
    add column if not exists next_retry_at timestamp;
alter table psy_notification_delivery
    add column if not exists processing_started_at timestamp;
alter table psy_notification_delivery
    add column if not exists dead_letter_at timestamp;

alter table psy_notification_delivery
    add constraint ck_psy_notification_delivery_retry_count
    check (retry_count >= 0) not valid;

alter table psy_notification_delivery
    drop constraint ck_psy_notification_delivery_status;
alter table psy_notification_delivery
    add constraint ck_psy_notification_delivery_status
    check (delivery_status in ('PENDING', 'PROCESSING', 'SENT', 'DELIVERED', 'FAILED', 'CLICKED', 'DEAD_LETTER')) not valid;
