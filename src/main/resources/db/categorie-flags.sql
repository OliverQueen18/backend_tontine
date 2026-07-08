ALTER TABLE categories_depenses ADD COLUMN IF NOT EXISTS necessite_mouvement_caisse BOOLEAN DEFAULT true;
ALTER TABLE categories_depenses ADD COLUMN IF NOT EXISTS necessite_client BOOLEAN DEFAULT false;
UPDATE categories_depenses SET necessite_mouvement_caisse = true WHERE necessite_mouvement_caisse IS NULL;
UPDATE categories_depenses SET necessite_client = false WHERE necessite_client IS NULL;

ALTER TABLE depenses ADD COLUMN IF NOT EXISTS client_id BIGINT;
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_depenses_client' AND table_name = 'depenses'
    ) THEN
        ALTER TABLE depenses ADD CONSTRAINT fk_depenses_client FOREIGN KEY (client_id) REFERENCES clients(id);
    END IF;
END $$;

ALTER TABLE platform_settings ADD COLUMN IF NOT EXISTS taux_commission_admin_defaut NUMERIC(8,4) DEFAULT 0.0500;
UPDATE platform_settings SET taux_commission_admin_defaut = 0.0500 WHERE taux_commission_admin_defaut IS NULL;
