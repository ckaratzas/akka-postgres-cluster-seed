CREATE TABLE akka_cluster_seed (
  systemName  TEXT COLLATE "C" NOT NULL,
  address     TEXT COLLATE "C" NOT NULL,
  STATUS      TEXT COLLATE "C" NOT NULL,
  heartBeatTs TIMESTAMP NOT NULL,
  CONSTRAINT pkey PRIMARY KEY (systemName, address)
);

CREATE EXTENSION IF NOT EXISTS pg_cron CASCADE;

SELECT cron.schedule('0 * * * *', $$DELETE FROM akka_cluster_seed WHERE heartBeatTs < now() - INTERVAL '1 hour'$$);