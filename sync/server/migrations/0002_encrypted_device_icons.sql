ALTER TABLE devices ADD COLUMN encrypted_icon_nonce TEXT;
ALTER TABLE devices ADD COLUMN encrypted_icon_ciphertext TEXT;

CREATE TRIGGER devices_encrypted_icon_insert
BEFORE INSERT ON devices
WHEN NEW.encrypted_icon_nonce IS NULL OR NEW.encrypted_icon_ciphertext IS NULL
BEGIN
    SELECT RAISE(ABORT, 'encrypted device icon required');
END;

CREATE TRIGGER devices_encrypted_icon_update
BEFORE UPDATE OF encrypted_icon_nonce, encrypted_icon_ciphertext ON devices
WHEN NEW.encrypted_icon_nonce IS NULL OR NEW.encrypted_icon_ciphertext IS NULL
BEGIN
    SELECT RAISE(ABORT, 'encrypted device icon required');
END;
