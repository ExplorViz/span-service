/*
@formatter:off
 */

CREATE KEYSPACE IF NOT EXISTS explorviz WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};

/* Find spans in a specific time range (used to find aggregate call counts)*/
CREATE TABLE IF NOT EXISTS explorviz.span_by_time
(
  landscape_token uuid,
  start_time    bigint,
  method_hash     text,
  span_id         text,
  trace_id        text, /* to be able to select full trace from span_by_traceid? */
  PRIMARY KEY ((landscape_token, start_time), method_hash, span_id)
);

/* Count Spans in a specific time bucket of ten seconds */
CREATE TABLE IF NOT EXISTS explorviz.span_count_per_time_bucket_and_token
(
  landscape_token UUID,
  tenth_second_epoch bigint,
  span_count counter,
  PRIMARY KEY (landscape_token, tenth_second_epoch)
  );

CREATE TABLE IF NOT EXISTS explorviz.span_count_for_token_and_commit_and_time_bucket (
  landscape_token UUID,
  git_commit_checksum text,
  tenth_second_epoch bigint,
  span_count counter,
  PRIMARY KEY ((landscape_token, git_commit_checksum), tenth_second_epoch)
);

/* SELECT trace_id FROM traceid_by_time WHERE landscape_token = X AND start_time_s = X; */

/* Get complete traces by their trace id */
CREATE TABLE IF NOT EXISTS explorviz.span_by_traceid
(
  landscape_token uuid,
  trace_id        text,
  span_id         text,
  parent_span_id  text,
  start_time    bigint,
  end_time      bigint,
  method_hash     text,
  PRIMARY KEY ((landscape_token, trace_id), span_id) /* parent_span_id as first clustering key to find parent=0 quickly? */
  );

/* Find traces that contain a span with a certain method_hash */
CREATE TABLE IF NOT EXISTS explorviz.trace_by_hash
(
  landscape_token uuid,
  method_hash     text,
  time_bucket     int,
  trace_id        text,
  PRIMARY KEY ((landscape_token, method_hash, time_bucket), trace_id)
  );

/* SELECT trace_id FROM trace_by_hash WHERE landscape_token = X AND method_hash = X AND time_bucket = TODAY(); */

/* Find traces by time */
CREATE TABLE IF NOT EXISTS explorviz.trace_by_time
(
  landscape_token uuid,
  git_commit_checksum text,
  tenth_second_epoch bigint,
  start_time      bigint,
  end_time        bigint,
  trace_id        text,
  PRIMARY KEY ((landscape_token, tenth_second_epoch), trace_id)
  );

/* SELECT * FROM span_by_traceid WHERE landscape_token = X AND trace_id IN (previous result); */

/* Map method_hash to metadata */
CREATE TABLE IF NOT EXISTS explorviz.span_structure
(
  landscape_token      uuid,
  method_hash          text,
  node_ip_address      text,
  host_name            text,
  application_name     text,
  application_language text,
  application_instance text,
  method_fqn           text,
  time_seen            bigint,
  k8s_pod_name         text,
  k8s_node_name        text,
  k8s_namespace        text,
  k8s_deployment_name  text,
  PRIMARY KEY ((landscape_token), method_hash)
);
