-- What a database holds, to compare a restored dump with production (restore-test.sh, deploy/README.md).
select (select version from flyway_schema_history where success
        order by installed_rank desc limit 1) as migration,
       (select count(*) from app_user) as users,
       (select count(*) from topic) as topics,
       (select count(*) from question) as questions,
       (select count(*) from attempt) as attempts,
       (select count(*) from review_state) as review_states;
