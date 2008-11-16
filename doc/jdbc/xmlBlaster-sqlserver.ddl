-- xmlBlaster.org database schema 2008 laghi/ruff
-- SQLServer 2005/2008
-- Data types: http://msdn.microsoft.com/en-us/library/ms187752.aspx
-- osql -E -d xmlblaster -i xmlBlaster\doc\jdbc\xmlBlaster-sqlserver.ddl

-- Backup and restore example:
-- osql /E
-- USE xmlblaster
-- go
-- BACKUP DATABASE xmlblaster TO DISK = 'C:\Marcel\backups\xmlBlaster.bak' WITH FORMAT, NAME = 'Full Backup of xmlBlaster'              
-- go
-- use master
-- go
-- RESTORE DATABASE xmlblaster FROM DISK = 'C:\Marcel\backups\xmlBlaster.bak' WITH NORECOVERY
-- go



drop table xbref
go
drop table xbmeat
go
drop table xbstore
go

create table xbstore (
      xbstoreid bigint primary key not null,
      xbnode varchar(256) not null,
      xbtype varchar(32) not null,
      xbpostfix varchar(256) not null,
      xbflag1 varchar(32) default '')
go

create unique index xbstoreidx on xbstore (xbnode, xbtype, xbpostfix)
go
-- insert into xbstore (xbstoreid,xbnode,xbtype,xbpostfix,xbflag1) values (1,'heron','callback','clientjoe1','')

-- Large data: http://msdn.microsoft.com/en-us/library/ms189574.aspx
-- With VARCHAR(MAX) one can't spcify number of bytes??
-- sp_tableoption N'xbmeat', 'text in row', 'ON';  (the limit defaults to 256 bytes)
-- sp_tableoption N'xbmeat', 'text in row', '1000';
--drop table xbtest
--go
--create table xbtest (
--      xbchar varchar(MAX) default ''
--)
--go
----Error:
--EXEC sp_tableoption 'xbtest', 'varchar(MAX) in row', '1000';
----Ok:
--EXEC sp_tableoption 'xbtest', 'large value types out of row', 0;
--go
--insert into xbtest (xbchar) values ('blabla')
--go
--select len(xbchar) from xbtest
--go

-- How to COLLATE UTF-8
create table xbmeat (
      xbmeatid bigint not null,
      xbdurable char not null default 'F',
      xbrefcount int,
      xbrefcount2 int,
-- int=4bytes=2GB, but must be bigint as sum(xbbytesize) fails: Arithmetic overflow SQLServerException
      xbbytesize bigint,
      xbdatatype varchar(32) not null default '',
--Only subscribe remembers sessionName      
--    xbmetainfo varchar(156) default '',
      xbmetainfo varchar(MAX) default '',
      xbflag1 varchar(32) default '',
      xbmsgqos varchar(MAX) default '',
      xbmsgcont varbinary(MAX),
--    xbmsgkey varchar(200) default '',
      xbmsgkey varchar(MAX) default '',
      xbstoreid bigint not null,
      constraint xbmeatpk primary key(xbmeatid, xbstoreid))
go

alter table xbmeat
      add constraint fkxbstoremeat
      foreign key (xbstoreid)
      references xbstore on delete cascade
go

create index xbmeatstix on xbmeat(xbmeatid,xbstoreid);
-- insert into xbmeat (xbmeatid,xbdurable,xbbytesize,xbdatatype,xbmetainfo,xbflag1,xbmsgqos,xbmsgcont,xbmsgkey,xbstoreid) values (1,'T',344,'TOPIC_XML','key1=value1','NO FLAG','<qos/>',cast('myBlob'as varbinary(MAX)),'<key oid="34"/>',1)

create table xbref (
	xbrefid bigint not null,
	xbstoreid bigint not null,
	xbmeatid bigint,
	xbdurable char(1) not null default 'F',
	xbbytesize bigint,
--ServerEntryFactory UPDATE remembers sessionName, oid, subscribeId and short fields      
--  xbmetainfo varchar(600) default '',
	xbmetainfo varchar(MAX) default '',
	xbflag1 varchar(32) default '',
	xbprio int,
	xbmethodname varchar(32) default '',
	xbonetomany char(1) not null default 'F',
constraint xbrefpk primary key(xbrefid, xbstoreid))
go

alter table xbref
            add constraint fkxbstoreref
            foreign key (xbstoreid)
            references xbstore on delete cascade
go

-- insert into xbref (xbrefid,xbstoreid,xbmeatid,xbdurable,xbbytesize,xbmetainfo,xbflag1,xbprio,xbmethodname,xbonetomany) values (1,1,1,'T',200,'subscriptionId=blÄOÜßa,oid=mytopic','',5,'publish','F')


-- select * from xbref;
