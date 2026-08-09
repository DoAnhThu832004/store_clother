-- =============================================================================
-- V3__tenant_lifecycle_improvements.sql
-- Supplements: Tenant Lifecycle Guard + Subscription Expiry Job support
-- =============================================================================
-- All statements are idempotent using SET+PREPARE pattern.
-- No Vietnamese comments to avoid charset encoding issues with Flyway.
-- =============================================================================

-- =============================================================================
-- STEP 1: Composite index for TenantSubscriptionExpiryJob performance
-- Query: WHERE status = 'ACTIVE' AND subscription_expired_at < today
-- =============================================================================

SET @s=IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='tenants' AND INDEX_NAME='idx_tenant_subscription_expiry') > 0,
  'SELECT 1',
  'CREATE INDEX idx_tenant_subscription_expiry ON tenants (status, subscription_expired_at)'
);
PREPARE p FROM @s; EXECUTE p; DEALLOCATE PREPARE p;

-- =============================================================================
-- STEP 2: View for Super Admin tenant health monitoring
-- =============================================================================

CREATE OR REPLACE VIEW v_tenant_health AS
SELECT
    t.id                       AS tenant_id,
    t.name                     AS tenant_name,
    t.code                     AS tenant_code,
    t.status                   AS status,
    t.subscription_expired_at  AS expires_on,
    CASE
        WHEN t.status = 'SUSPENDED' THEN 'SUSPENDED'
        WHEN t.status = 'EXPIRED'   THEN 'EXPIRED'
        WHEN t.subscription_expired_at IS NULL THEN 'UNLIMITED'
        WHEN t.subscription_expired_at < CURDATE() THEN 'OVERDUE'
        WHEN DATEDIFF(t.subscription_expired_at, CURDATE()) <= 7 THEN 'EXPIRING_SOON'
        ELSE 'HEALTHY'
    END                        AS health_status,
    DATEDIFF(t.subscription_expired_at, CURDATE()) AS days_remaining,
    t.contact_email            AS contact_email,
    t.created_at               AS registered_at
FROM tenants t
ORDER BY t.status, t.subscription_expired_at;
