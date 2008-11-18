-- Export / Import example:
-- cd ~/xmlBlaster/doc/jdbc
-- pg_dump xmlblaster > xmlBlaster-Postgres-2008-11-12-orig-xb_entries.dump
-- dropdb xmlblaster; createdb xmlblaster; psql xmlblaster < xmlBlaster-Postgres-2008-11-12-orig-xb_entries.dump 

psql -d xmlblaster

drop index idxstorename;
drop table xbref;
drop table xbmeat;
drop table xbstore;

create table xbstore (
      xbstoreid int8 primary key unique not null,
      xbnode varchar(256) not null,
      xbtype varchar(32) not null,
      xbpostfix varchar(256) not null,
      xbrefcounted char(1) not null default 'F',
      xbflag1 varchar(32) default '');
  -- xbstoreid  bigserial not null,
  -- creationts timestamp default current_timestamp not null,
  -- modifiedts timestamp default current_timestamp not null,
  -- nodeId + storeType + storeId ("heron", "callback", "joe17"): Java umbauen ist muehsam
--  NOTICE:  CREATE TABLE / PRIMARY KEY will create implicit index "xbstore_pkey" for table "xbstore"

create unique index xbstoreidx on xbstore (xbnode, xbtype, xbpostfix);
-- insert into xbstore (xbstoreid,xbnode,xbtype,xbpostfix,flag1) values (1,'heron','callback','clientjoe1','');


create table xbmeat (
      xbmeatid int8 not null,
      xbdurable char not null default 'F',
      xbrefcount int4,
      xbrefcount2 int4,
      xbbytesize int8,
      xbdatatype varchar(32) not null default '',
      xbmetainfo text default '',
      xbflag1 varchar(32) default '',
      xbmsgqos text default '',
      xbmsgcont bytea default '',
      xbmsgkey text default '',
      xbstoreid int8 not null,
      constraint xbmeatpk primary key(xbmeatid, xbstoreid));
--      xbstoreid int8 unique not null, constraint xbmeatpk primary key(xbmeatid));
-- xbmeatid bigserial not null,
-- creationts timestamp default current_timestamp not null,
-- modifiedts timestamp default current_timestamp not null,
-- NOTICE:  CREATE TABLE / PRIMARY KEY will create implicit index "xbmeatpk" for table "xbmeat"
-- NOTICE:  CREATE TABLE / UNIQUE will create implicit index "xbmeat_xbstoreid_key" for table "xbmeat"

alter table xbmeat
      add constraint fkxbstoremeat
      foreign key (xbstoreid)
      references xbstore on delete cascade;

create index xbmeatstix on xbmeat(xbmeatid,xbstoreid);
--insert into xbmeat (xbmeatid,durable,bytesize,datatype,flag1,msgqos,msgcont,msgkey) values (1,'T',344,'TOPIC_XML','NO FLAG','<qos/>','myBlob','<key oid="34"/>');

create table xbref (
	xbrefid int8 not null,
	xbstoreid int8 not null,
	xbmeatid int8,
	-- creationts timestamp not null default current_timestamp,
	-- modifiedts timestamp not null default current_timestamp,
	xbdurable char(1) not null default 'F',
	xbbytesize int8,
	xbmetainfo text default '',
	xbflag1 varchar(32) default '',
	xbprio int4,
	xbmethodname varchar(32) default '',
constraint xbrefpk primary key(xbrefid, xbstoreid));

alter table xbref
            add constraint fkxbstoreref
            foreign key (xbstoreid)
            references xbstore on delete cascade;

--insert into xbref (xbrefid,xbstoreid,xbmeatid,durable,bytesize,metainfo,flag1,prio) values (1,1,1,'T',200,'subscriptionId=bla,oid=mytopic','',5);
--insert into xbref (xbrefid,xbstoreid,xbmeatid,durable,bytesize,metainfo,flag1,prio) values (2,1,1,'T',200,'subscriptionId=b社会保障la,oid=Übßk','',9);

--select * from xbref;


-- -- Two ways to work with serial sequence:
--create table xbserial (
--  xbserialid  bigserial not null,
--  flag1 varchar(32) default ''
--);
--select nextval('xbserial_xbserialid_seq');
-- -- new_id is returned
--INSERT INTO xbserial (xbserialid, flag1) VALUES (new_id, 'Test1');

--INSERT INTO xbserial (flag1) VALUES ('Test2');
--SELECT currval('xbserial_xbserialid_seq');
