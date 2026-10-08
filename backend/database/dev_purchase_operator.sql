-- Local development only. Select your application database before executing.
-- A disabled technical identity for audit foreign keys, NOT a login account.
-- Does not overwrite an existing user. No password/token is stored here.
INSERT INTO sys_user(username, password_hash, display_name, enabled)
SELECT 'stockflow-local-operator', '!local-development-no-login!', '本地开发操作者', 0
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE username = 'stockflow-local-operator');
