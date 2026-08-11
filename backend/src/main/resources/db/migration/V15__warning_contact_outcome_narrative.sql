-- Contact outcomes are narrative safety evidence, not a short status code.
-- PostgreSQL can widen varchar to text without rewriting existing values.
alter table psy_warning_response_event
    alter column contact_outcome type text using contact_outcome::text;

comment on column psy_warning_response_event.contact_outcome is
    'Narrative outcome of the contact attempt; API input is limited to 2000 characters.';
