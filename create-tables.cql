
CREATE KEYSPACE IF NOT EXISTS barker
  WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 2};

USE barker;

CREATE TABLE IF NOT EXISTS users (
  username  text  PRIMARY KEY,
  followers set<text>,
  following set<text>
) WITH comment = 'Users with followers';

CREATE TABLE IF NOT EXISTS timeline (
  username  text,
  slice     timestamp,
  ts        timestamp,
  sender    text,
  message   text,
  PRIMARY KEY ((username, slice), ts, sender)
) WITH comment = 'Timeline of users'
  AND CLUSTERING ORDER BY (ts DESC, sender ASC);

CREATE TABLE IF NOT EXISTS barks (
  username  text,
  slice     timestamp,
  ts        timestamp,
  message   text,
  PRIMARY KEY ((username, slice), ts)
) WITH comment = 'Barks of a user'
  AND CLUSTERING ORDER BY (ts DESC);
