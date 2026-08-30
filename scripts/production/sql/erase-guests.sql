START TRANSACTION;
DELETE FROM check_in_correction;
DELETE FROM check_in;
DELETE FROM rsvp;
DELETE FROM guest;
DELETE FROM guest_category;
COMMIT;
