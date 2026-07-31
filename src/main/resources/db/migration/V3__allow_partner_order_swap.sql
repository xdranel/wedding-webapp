alter table partner drop check chk_partner_display_order;
alter table partner add constraint chk_partner_display_order check (display_order in (0, 1, 2));
