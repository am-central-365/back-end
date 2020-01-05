/*
create table logs(
    log_id binary(16) not null
  ,   constraint logs_pk primary key(log_id)
  , log_content      longtext      comment 'for inline logs'
  , location_machine varchar(255)  comment 'DNS name of the machine storing the file'
  , location_path    varchar(4096) comment 'Linux limit on paths'
  , log_beg_ts    timestamp default current_timestamp
  , log_end_ts    timestamp
  , error_count   int
  , warning_count int
  , origin_task_id binary(16)  comment 'for logs initiated by tasks'
*/
);

create table tasks(
    task_id binary(16) not null
  ,   constraint tasks_pk primary key(task_id)
  , task_name        varchar(100) not null
  , task_status      enum('Ready', 'Processing', 'Finished', 'Failed') default 'Ready' not null
  , task_status_ts   timestamp default current_timestamp not null
  , scheduled_run_ts timestamp default current_timestamp not null
  , script_asset_id     binary(16) not null
  , target_asset_id     binary(16) not null
  , executor_role_name  varchar(100) not null
  ,   constraint tasks_script_fk foreign key(script_asset_id) references assets(asset_id)
  ,   constraint tasks_target_fk foreign key(target_asset_id) references assets(asset_id)
  ,   constraint tasks_exrole_fk foreign key(executor_role_name) references roles(name)
  , script_args mediumtext
  /*, script_data mediumtext comment 'used by special jobs, such as ansible/...'*/
  , description mediumtext
  /* execution */
  , started_ts  timestamp null default null
  , finished_ts timestamp null default null
  , submit_worker  varchar(256)
  , execute_worker varchar(256)
  , log_id         binary(16)
  /* progress reporting */
  , progress_text  mediumtext
  , progress_curr  int
  , progress_total int
  /* --- standard fields */
  , created_by   varchar(100)
  , created_ts   timestamp default current_timestamp not null
  , modified_by  varchar(100)
  , modified_ts  timestamp default current_timestamp not null
  /* task scheduler fields */
  , source_template_id binary(16) comment 'for tasks generated from a template, id of the template'
);

/*
create table task_templates(
    template_id binary(16) not null
  ,   constraint task_templates_pk primary key(template_id)
  , schedule json not null
  , last_scheduled_task_id binary(16) comment 'id of the last task sheduled using this template'
  ,  constraint tasks_fk2 foreign key(last_scheduled_task_id) references tasks(task_id)
  \* --- standard fields *\
  , created_by   varchar(100)
  , created_ts   timestamp default current_timestamp not null
  , modified_by  varchar(100)
  , modified_ts  timestamp default current_timestamp not null
);
*/
