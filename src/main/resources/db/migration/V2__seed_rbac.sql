-- Seed roles, permissions, and role-permission grants.
--
-- Authorization is permission-based: @PreAuthorize checks the permission
-- name, never the role name. Adding a new role is just an INSERT into
-- roles + role_permissions - no Java change required.

INSERT INTO roles (name, description) VALUES
    ('USER',  'Standard authenticated user'),
    ('ADMIN', 'Administrator with full access');

INSERT INTO permissions (name, description) VALUES
    ('APPOINTMENT_CREATE',         'Create a new appointment'),
    ('APPOINTMENT_READ_OWN',       'Read own appointments'),
    ('APPOINTMENT_READ_ALL',       'Read all appointments (admin)'),
    ('APPOINTMENT_UPDATE_STATUS',  'Update appointment status (admin)'),
    ('APPOINTMENT_CANCEL_OWN',     'Cancel own appointment'),
    ('CHAT_HISTORY_READ_OWN',      'Read own chat history'),
    ('USER_READ_ALL',              'List all users (admin)'),
    ('USER_MANAGE',                'Manage users (admin)');

-- USER grants - book/read/cancel own appointments and read own chat history.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'USER'
  AND p.name IN (
      'APPOINTMENT_CREATE',
      'APPOINTMENT_READ_OWN',
      'APPOINTMENT_CANCEL_OWN',
      'CHAT_HISTORY_READ_OWN'
  );

-- ADMIN grants - every permission seeded above.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name IN (
      'APPOINTMENT_CREATE',
      'APPOINTMENT_READ_OWN',
      'APPOINTMENT_READ_ALL',
      'APPOINTMENT_UPDATE_STATUS',
      'APPOINTMENT_CANCEL_OWN',
      'CHAT_HISTORY_READ_OWN',
      'USER_READ_ALL',
      'USER_MANAGE'
  );
