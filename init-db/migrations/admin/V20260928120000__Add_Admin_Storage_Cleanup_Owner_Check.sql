-- An upload whose RPC outcome is unknown cannot be discarded on assumption:
-- App may have committed the profile row, in which case the object is the live
-- avatar. Keys recorded with `verify_owner_reference = 1` are resolved by the
-- sweep, which asks App for the current avatar before deciding — referenced
-- keys are kept (`kept_at`), unreferenced ones are deleted. Plain staged keys
-- (`verify_owner_reference = 0`) are deleted without an owner round trip.

ALTER TABLE `admin`.`storage_cleanup_outbox`
    ADD COLUMN `verify_owner_reference` tinyint(1) NOT NULL DEFAULT 0,
    ADD COLUMN `kept_at` datetime(3) DEFAULT NULL;
