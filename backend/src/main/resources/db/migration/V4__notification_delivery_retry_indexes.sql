-- This migration is deliberately non-transactional (see the adjacent .conf)
-- because PostgreSQL concurrent index creation cannot run in a transaction.
create index concurrently if not exists idx_psy_notification_delivery_pending_retry
    on psy_notification_delivery(next_retry_at, created_at, id)
    where delivery_channel = 'PUSH' and delivery_status = 'PENDING';

create index concurrently if not exists idx_psy_notification_delivery_processing_started
    on psy_notification_delivery(processing_started_at, id)
    where delivery_channel = 'PUSH' and delivery_status = 'PROCESSING';

create index concurrently if not exists idx_psy_notification_delivery_dead_letter
    on psy_notification_delivery(dead_letter_at desc, id desc)
    where delivery_status = 'DEAD_LETTER';
