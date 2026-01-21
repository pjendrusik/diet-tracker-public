CREATE INDEX IF NOT EXISTS idx_food_item_user_lower_name
    ON food_item (user_id, LOWER(name));

CREATE INDEX IF NOT EXISTS idx_food_item_user_lower_brand
    ON food_item (user_id, LOWER(COALESCE(brand, '')));

ALTER TABLE nutrition_snapshot
    ADD CONSTRAINT fk_snapshot_food
    FOREIGN KEY (food_id)
    REFERENCES food_item(id);
