-- sqlite3 xmlblaster.db
-- .read xmlBlaster-sqlite.ddl

drop index xbstoreidx;
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

create unique index xbstoreidx on xbstore (xbnode, xbtype, xbpostfix);
-- insert into xbstore (xbstoreid,storename,flag1) values (1,'callbackjoe1','');

create table xbmeat (
      xbmeatid int8 not null,
      xbdurable char not null default 'F',
      xbrefcount int4,
      xbrefcount2 int4,
      xbbytesize int4,
      xbdatatype varchar(32) not null default '',
      xbmetainfo text default '',
      xbflag1 varchar(32) default '',
      xbmsgqos text default '',
      xbmsgcont bytea default '',
      xbmsgkey text default '',
      xbstoreid int8 not null,
      constraint xbmeatpk primary key(xbmeatid, xbstoreid),
      constraint fkxbstoremeat foreign key (xbstoreid) references xbstore on delete cascade
		);
-- alter table add constraint not supported!

create table xbref (
	xbrefid int8 not null,
	xbstoreid int8 not null,
	xbmeatid int8,
	xbdurable char(1) not null default 'F',
	xbbytesize int4,
	xbmetainfo text default '',
	xbflag1 varchar(32) default '',
	xbprio int4,
	xbmethodname varchar(32) default '',
	constraint xbrefpk primary key(xbrefid, xbstoreid),
	constraint fkxbstoreref foreign key (xbstoreid) references xbstore on delete cascade
);

create index xbmeatstix on xbmeat(xbmeatid,xbstoreid);
--insert into xbmeat (xbmeatid,durable,bytesize,datatype,flag1,msgqos,msgcont,msgkey) values (1,'T',344,'TOPIC_XML','NO FLAG','<qos/>','myBlob','<key oid="34"/>');

--insert into xbref (xbrefid,xbstoreid,xbmeatid,durable,bytesize,metainfo,flag1,prio) values (1,1,1,'T',200,'subscriptionId=bla,oid=mytopic','',5);
--insert into xbref (xbrefid,xbstoreid,xbmeatid,durable,bytesize,metainfo,flag1,prio) values (2,1,1,'T',200,'subscriptionId=b社会保障la,oid=Übßk','',9);

