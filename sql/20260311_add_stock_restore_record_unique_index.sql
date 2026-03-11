ALTER TABLE stock_restore_record
    ADD UNIQUE KEY uk_stock_restore_record_order_scene (order_id, scene);
