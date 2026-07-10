ALTER TABLE oc_requirement
    ADD COLUMN generation_token VARCHAR(64);

ALTER TABLE oc_overview_design
    ADD COLUMN generation_token VARCHAR(64);

ALTER TABLE oc_detailed_design
    ADD COLUMN generation_token VARCHAR(64);

ALTER TABLE oc_plan
    ADD COLUMN generation_token VARCHAR(64);
