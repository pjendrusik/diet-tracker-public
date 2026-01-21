CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS food_item (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    name TEXT NOT NULL,
    brand TEXT,
    default_serving_value NUMERIC(10,2) NOT NULL,
    default_serving_unit TEXT NOT NULL,
    calories_per_serving NUMERIC(10,2) NOT NULL,
    macros_per_serving JSONB,
    macros_per_100g JSONB,
    notes TEXT,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS intake_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    food_id UUID NOT NULL REFERENCES food_item(id),
    logged_at TIMESTAMPTZ NOT NULL,
    quantity NUMERIC(10,2) NOT NULL,
    unit TEXT NOT NULL,
    notes TEXT,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS nutrition_snapshot (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    intake_log_id UUID NOT NULL UNIQUE REFERENCES intake_log(id) ON DELETE CASCADE,
    food_id UUID NOT NULL,
    food_name TEXT NOT NULL,
    food_brand TEXT,
    serving_value NUMERIC(10,2) NOT NULL,
    serving_unit TEXT NOT NULL,
    quantity_logged NUMERIC(10,2) NOT NULL,
    calories NUMERIC(10,2) NOT NULL,
    protein_g NUMERIC(10,2) DEFAULT 0,
    carbs_g NUMERIC(10,2) DEFAULT 0,
    fat_g NUMERIC(10,2) DEFAULT 0,
    food_version INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_food_item_user ON food_item(user_id);
CREATE INDEX IF NOT EXISTS idx_intake_log_user_date ON intake_log(user_id, logged_at);
