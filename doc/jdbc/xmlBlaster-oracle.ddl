-- Example on how to invoke:
--
-- sqlplus xmlblaster/xbl
-- @xmlBlaster-oracle.ddl
--- quit

--drop index idxstorename;
drop table xbref;
drop table xbmeat;
drop table xbstore;


create table xbmeat (
  xbmeatid NUMBER(20) primary key,
  durable char default 'F' not null,
  refcount NUMBER(10),
  bytesize NUMBER(10),
  datatype varchar(32) default '' not null,
  flag1 varchar(32) default '',
  msgqos clob default '',
  msgcont blob default '',
  msgkey clob default ''
);

-- no content is inserted here
insert into xbmeat (xbmeatid,durable,bytesize,datatype,flag1,msgqos,msgkey) values (1,'T',344,'TOPIC_XML','NO FLAG','<qos/>','<key oid="34"/>');

create table xbstore (
  xbstoreid NUMBER(20) primary key,
  storename varchar(512) not null unique,
  flag1 varchar(32) default ''
);

-- next line not needed in oracle since unique implies already an index
--create index idxstorename on xbstore(storename);

insert into xbstore (xbstoreid,storename,flag1) values (1,'callbackjoe1','');

create table xbref (
  xbrefid NUMBER(20) primary key,
  xbstoreid NUMBER(20) not null,
  xbmeatid NUMBER(20) ,
  durable char(1) default 'F' not null ,
  bytesize NUMBER(10) ,
  metainfo clob default '',
  flag1 varchar(32) default '',
  prio  NUMBER(10)
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
