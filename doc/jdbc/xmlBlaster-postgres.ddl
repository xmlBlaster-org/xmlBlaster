psql -d xmlblaster

drop index idxstorename;
drop table xbref;
drop table xbmeat;
drop table xbstore;

create table xbmeat (
  -- xbmeatid bigserial not null,
  xbmeatid int8 primary key unique not null,
  -- creationts timestamp default current_timestamp not null,
  -- modifiedts timestamp default current_timestamp not null,
  durable char default 'F' not null,
  refcount int4,
  bytesize int4,
  datatype varchar(32) default '' not null,
  flag1 varchar(32) default '',
  msgqos text default '',
  msgcont bytea default '',
  msgkey text default ''
);

insert into xbmeat (xbmeatid,durable,bytesize,datatype,flag1,msgqos,msgcont,msgkey) values (1,'T',344,'TOPIC_XML','NO FLAG','<qos/>','myBlob','<key oid="34"/>');

create table xbstore (
  xbstoreid int8 primary key unique not null,
  -- xbstoreid  bigserial not null,
  -- creationts timestamp default current_timestamp not null,
  -- modifiedts timestamp default current_timestamp not null,
  -- nodeId + storeType + storeId ("heron", "callback", "joe17"): Java umbauen ist muehsam
  storename varchar(512) not null unique,
  flag1 varchar(32) default ''
);
-- create index idxstorename on xbstore(storename); -- (nodeId, storeType, storeId)

insert into xbstore (xbstoreid,storename,flag1) values (1,'callbackjoe1','');

create table xbref (
  xbrefid int8 primary key unique not null,
  xbstoreid int8 not null,
  xbmeatid int8,
  -- creationts timestamp default current_timestamp not null,
  -- modifiedts timestamp default current_timestamp not null,
  durable char(1) default 'F' not null,
  bytesize int4,
  metainfo text default '',
  flag1 varchar(32) default '',
  prio int4
);

alter table xbref 
        add constraint fkxbstore
        foreign key (xbstoreid) 
        references xbstore;

alter table xbref 
        add constraint fkxbmeat
        foreign key (xbmeatid) 
        references xbmeat;

insert into xbref (xbrefid,xbstoreid,xbmeatid,durable,bytesize,metainfo,flag1,prio) values (1,1,1,'T',200,'subscriptionId=bla,oid=mytopic','',5);
insert into xbref (xbrefid,xbstoreid,xbmeatid,durable,bytesize,metainfo,flag1,prio) values (2,1,1,'T',200,'subscriptionId=b社会保障la,oid=Übßk','',9);

select * from xbref;


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
