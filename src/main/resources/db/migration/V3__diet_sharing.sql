CREATE TABLE IF NOT EXISTS diet (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    owner_user_id UUID NOT NULL,
    owner_display_name TEXT,
    title TEXT NOT NULL,
    document JSONB NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    archived_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_diet_owner ON diet(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_diet_status ON diet(status);

CREATE TABLE IF NOT EXISTS diet_share (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    diet_id UUID NOT NULL REFERENCES diet(id) ON DELETE CASCADE,
    owner_user_id UUID NOT NULL,
    recipient_user_id UUID NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    activated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    revoked_by UUID,
    last_notified_at TIMESTAMPTZ,
    CONSTRAINT uq_diet_share UNIQUE (diet_id, recipient_user_id),
    CONSTRAINT chk_share_owner_recipient CHECK (owner_user_id <> recipient_user_id)
);

CREATE INDEX IF NOT EXISTS idx_diet_share_owner ON diet_share(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_diet_share_recipient ON diet_share(recipient_user_id);
CREATE INDEX IF NOT EXISTS idx_diet_share_recipient_active
    ON diet_share(recipient_user_id)
    WHERE status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS diet_copy (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    source_diet_id UUID NOT NULL REFERENCES diet(id) ON DELETE CASCADE,
    source_share_id UUID REFERENCES diet_share(id),
    new_diet_id UUID NOT NULL REFERENCES diet(id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL,
    copied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_diet_copy_source ON diet_copy(source_diet_id);
CREATE INDEX IF NOT EXISTS idx_diet_copy_recipient ON diet_copy(recipient_user_id);

CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type TEXT NOT NULL CHECK (event_type IN ('SHARE_CREATED', 'SHARE_REVOKED', 'DIET_COPIED')),
    actor_user_id UUID NOT NULL,
    target_diet_id UUID,
    target_recipient_id UUID,
    metadata JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_log_diet ON audit_log(target_diet_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_recipient ON audit_log(target_recipient_id);

COMMENT ON TABLE diet IS 'Canonical diet storage for owner-authored meal plans.';
COMMENT ON TABLE diet_share IS 'Tracks read-only permissions from an owner to a recipient user.';
COMMENT ON TABLE diet_copy IS 'Records copies of diets created from a share for independent editing.';
COMMENT ON TABLE audit_log IS 'Immutable log of sharing lifecycle events.';
