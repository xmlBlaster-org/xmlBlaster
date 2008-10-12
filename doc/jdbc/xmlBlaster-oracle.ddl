-- Example on how to invoke:
--
-- sqlplus xmlblaster/xbl
-- @xmlBlaster-oracle.ddl
--- quit

--drop index idxstorename;
drop table xbref;
drop table xbmeat;
drop table xbstore;

create table xbstore (
      xbstoreid number(20) primary key,
      xbnode varchar(256) not null,
      xbtype varchar(32) not null,
      xbpostfix varchar(256) not null,
      xbflag1 varchar(32) default '');

create unique index xbstoreidx on xbstore (xbnode, xbtype, xbpostfix);
-- insert into xbstore (xbstoreid,storename,flag1) values (1,'callbackjoe1','');


create table xbmeat (
      xbmeatid number(20),
      xbdurable char default 'F' not null,
      xbrefcount number(10),
      xbrefcount2 number(10),
      xbbytesize number(10),
      xbdatatype varchar(32) default '' not null,
      xbflag1 varchar(32) default '',
      xbmsgqos clob default '',
      xbmsgcont blob default '',
      xbmsgkey clob default '',
      xbstoreid number(20), constraint xbmeatpk primary key(xbmeatid));

alter table xbmeat 
      add constraint fkxbstoremeat
      foreign key (xbstoreid) 
      references xbstore on delete cascade;

create index xbmeatstix on xbmeat(xbmeatid,xbstoreid);
-- no content is inserted here
--insert into xbmeat (xbmeatid,durable,bytesize,datatype,flag1,msgqos,msgkey) values (1,'T',344,'TOPIC_XML','NO FLAG','<qos/>','<key oid="34"/>');


create table xbref (
      xbrefid NUMBER(20) primary key,
      xbstoreid NUMBER(20) not null,
      xbmeatid NUMBER(20) ,
      xbdurable char(1) default 'F' not null ,
      xbbytesize NUMBER(10) ,
      xbmetainfo clob default '',
      xbflag1 varchar(32) default '',
      xbprio  NUMBER(10),
      xbmethodname varchar(32) default '',
      xbonetomany char(1) default 'F' not null
    );

alter table xbref 
            add constraint fkxbstoreref
            foreign key (xbstoreid) 
            references xbstore on delete cascade;

--alter table xbref 
--        add constraint fkxbmeatref
--        foreign key (xbmeatid) 
--        references xbmeat;

--insert into xbref (xbrefid,xbstoreid,xbmeatid,durable,bytesize,metainfo,flag1,prio) values (1,1,1,'T',200,'subscriptionId=bla,oid=mytopic','',5);
--insert into xbref (xbrefid,xbstoreid,xbmeatid,durable,bytesize,metainfo,flag1,prio) values (2,1,1,'T',200,'subscriptionId=b社会保障la,oid=Übßk','',9);

--select * from xbref;
