-- TODO!

-- ---------------------------------------------------------------------------- 
-- Written by Michele Laghi (laghi@swissinfo.org) 2006-01-27                    
-- and Marcel Ruff (mr@marcelruff.info)                                         
--                                                                              
-- Some Comments:                                                               
--                                                                              
--  The effect of triggers has been checked. An open issue is how to determine  
--  wether an action has been caused by a direct operation of the user (primary 
--  action) or if it is a reaction to that as an operation performed by a       
--  trigger (reaction).                                                         
--                                                                              
-- NOTES:                                                                       
-- Avoid the usage of TRUNCATE in the tables to be replicated, since the        
-- deletion seems not to detect such a change.                                  
-- some suggestions on how to debug:                                            
-- with sqplus you can invoke:                                                  
-- show errors trigger|function name                                            
-- and the line number indicated can be retrieved with (for example):           
-- select text from all_source where name='REPL_BASE64_ENC_RAW' AND line=18     

-- ${replPrefix} <-> repl_
-- VARCHAR(50)       VARCHAR(${charWidth})
-- VARCHAR(30)       VARCHAR(${charWidthSmall})

-- CREATE TRIGGER Docu:
-- In a DELETE, INSERT, or UPDATE trigger, SQL Server does not allow text, ntext, or image column references in the inserted and deleted tables if the compatibility level is equal to 70. The text, ntext, and image values in the inserted and deleted tables cannot be accessed. To retrieve the new value in either an INSERT or UPDATE trigger, join the inserted table with the original update table. When the compatibility level is 65 or lower, null values are returned for inserted or deleted text, ntext, or image columns that allow null values; zero-length strings are returned if the columns are not nullable. 
--
-- If the compatibility level is 80 or higher, SQL Server allows the update of text, ntext, or image columns through the INSTEAD OF trigger on tables or views. 
--
-- Note:  
-- Use of text, ntext, and image data will be removed in a future version of SQL Server. The preferred storage for large data is through the varchar(MAX), nvarchar(MAX), and varbinary(MAX) data types. Both AFTER and INSTEAD OF triggers support varchar(MAX), nvarchar(MAX), and varbinary(MAX) data in the inserted and deleted tables. 
-- Convert??
-- Foreign keys??
-- Identity replication?
-- Which DB types and versions?
-- Java code for Meta Informations (DbWriter)?
-- Type conversions? (text->varchar(MAX), date ...)
-- Test if MAX can be huge
-- Test suite

-- CREATE TRIGGER must be the first statement in the batch and can apply to only one table.


-- ---------------------------------------------------------------------------- 


-- ---------------------------------------------------------------------------- 
-- WE FIRST CREATE THE TABLE HOLDING A LIST OF ALL TABLES TO BE REPLICATED      
-- ---------------------------------------------------------------------------- 


-- ---------------------------------------------------------------------------- 
-- This table contains the list of tables to watch.                             
-- tablename is the name of the table to watch                                  
-- actions is the flag being a combination of I (indicating it acts on inserts),
-- D (for deletes) and U (for updates).                                         
-- it will only watch for initial replication.                                  
-- ---------------------------------------------------------------------------- 

CREATE TABLE ${user}.${replPrefix}tables(catalogname VARCHAR(${charWidth}), 
                         schemaname VARCHAR(${charWidth}),
                         tablename VARCHAR(${charWidth}), actions CHAR(3),
                         status VARCHAR(${charWidthSmall}), repl_key INTEGER, 
                         trigger_name VARCHAR(${charWidth}), debug INTEGER, 
                         PRIMARY KEY(catalogname, schemaname, tablename))
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- create the ${replPrefix}current_tables as a placeholder for the current      
-- tables (this is used to detect a CREATE TABLE and a DROP TABLE.              
-- ---------------------------------------------------------------------------- 


-- ---------------------------------------------------------------------------- 
-- We create the table which will be used for the outgoing replica messages and 
-- a sequence needed for a monotone increasing sequence for the primary key.    
-- In postgres this will implicitly create an index "repltest_pkey" for this    
-- table. The necessary generic lowlevel functions are created.                 
-- ---------------------------------------------------------------------------- 
-- IDENTITY
-- CREATE TABLE TZ (
--   Z_id  int IDENTITY(1,1)PRIMARY KEY,
--   Z_name varchar(20) NOT NULL)

-- SET IDENTITY_INSERT dbo.TZ ON
-- INSERT INTO TZ (Z_id, Z_name) VALUES (
--   9, 'sieben')

-- CREATE SEQUENCE ${replPrefix}seq MINVALUE 1 MAXVALUE 1000000000 CYCLE
-- EOC (end of command: needed as a separator for our script parser)            

CREATE TABLE ${user}.${replPrefix}items (repl_key int IDENTITY(1,1)PRIMARY KEY, 
             trans_key VARCHAR(${charWidth}), dbId VARCHAR(${charWidth}), 
             tablename VARCHAR(${charWidth}), guid VARCHAR(${charWidth}), 
             db_action VARCHAR(${charWidth}), db_catalog VARCHAR(${charWidth}),
             db_schema VARCHAR(${charWidth}), content NTEXT, oldContent NTEXT, 
             version VARCHAR(${charWidthSmall}))
-- EOC (end of command: needed as a separator for our script parser)            

-- ---------------------------------------------------------------------------- 
-- ${replPrefix}col2xml_cdata converts a column into a simple xml notation and  
-- wraps the content into a _cdata object.                                      
--   name: the name of the column                                               
--   content the content of the column. If it is a blob use                     
-- ${replPrefix}col2xml_base64                                                  
-- ---------------------------------------------------------------------------- 

-- DROP FUNCTION ${replPrefix}col2xml_cdata
CREATE FUNCTION ${replPrefix}col2xml_cdata(@name VARCHAR, @content NTEXT)
RETURNS NTEXT AS BEGIN
DECLARE
  @ch  NTEXT
BEGIN
   SET @ch = '<col name="' + @name + '"><![CDATA[' + @content + ']]></col>'
END   
   RETURN  @ch
END
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}needs_prot (prot stands for protection) detects wether a        
-- protection to BASE64 is needed or not in a text string.                      
-- returns an integer. If 1 it means CDATA protection will suffice, if 2 it     
-- means it needs BASE64.                                                       
-- ---------------------------------------------------------------------------- 
CREATE FUNCTION ${replPrefix}needs_prot(@content NTEXT) 
RETURNS INTEGER AS BEGIN
--local variables
DECLARE
  @pos       INTEGER,
  @ret       INTEGER,
  @len       INTEGER,
  @offset    INTEGER,
  @tmp       NTEXT,
  @increment INTEGER
BEGIN
   SET @ret = 0
   SET @offset = 1
   SET @increment = 32766
   SET @len = DATALENGTH(@content)

   -- WHAT DOES INSTR DO ?
   --
   WHILE @offset < @len
   BEGIN
     SET @pos = PATINDEX('%]]>%', @tmp) -- INSTR(@tmp, ']]>', 1, 1)
     IF @pos > 0
     BEGIN
       IF @ret < 2
       BEGIN
         SET @ret = 2
       END
     END
      
     SET @pos = PATINDEX('%<%', @tmp)
     IF @pos > 0
     BEGIN
       IF @ret < 1
       BEGIN
         SET @ret = 1
       END
     END
     SET @pos = PATINDEX('%&%', @tmp)
     IF @pos > 0
     BEGIN
       IF @ret < 1
       BEGIN
         SET @ret = 1
       END
     END
   END
END
RETURN @ret
END
-- EOC (end of command: needed as a separator for our script parser)            


--IF object_id('[dbo].[base64_encode]') IS NOT NULL
--  DROP FUNCTION [dbo].[base64_encode]
--GO

CREATE FUNCTION base64_enc_blob
(
  @plain_text varchar(MAX)
)
RETURNS 
          varchar(MAX)
AS BEGIN
--local variables
DECLARE
  @output            varchar(MAX),
  @input_length      integer,
  @block_start       integer,
  @partial_block_start  integer, -- position of last 0, 1 or 2 characters
  @partial_block_length integer,
  @block_val         integer,
  @map               char(64)
SET @map = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'
--initialise variables
SET @output   = ''
--set length and count
SET @input_length      = LEN( @plain_text + '#' ) - 1
SET @partial_block_length = @input_length % 3
SET @partial_block_start = @input_length - @partial_block_length
SET @block_start       = 1
--for each block
WHILE @block_start < @partial_block_start  BEGIN
  SET @block_val = CAST(SUBSTRING(@plain_text, @block_start, 3) AS BINARY(3))
  --encode the 3 character block and add to the output
  SET @output = @output + SUBSTRING(@map, @block_val / 262144 + 1, 1)
                        + SUBSTRING(@map, (@block_val / 4096 & 63) + 1, 1)
                        + SUBSTRING(@map, (@block_val / 64 & 63  ) + 1, 1)
                        + SUBSTRING(@map, (@block_val & 63) + 1, 1)
  --increment the counter
  SET @block_start = @block_start + 3
END
IF @partial_block_length > 0
BEGIN
  SET @block_val = CAST(SUBSTRING(@plain_text, @block_start, @partial_block_length)
                      + REPLICATE(CHAR(0), 3 - @partial_block_length) AS BINARY(3))
  SET @output = @output
 + SUBSTRING(@map, @block_val / 262144 + 1, 1)
 + SUBSTRING(@map, (@block_val / 4096 & 63) + 1, 1)
 + CASE WHEN @partial_block_length < 2
    THEN REPLACE(SUBSTRING(@map, (@block_val / 64 & 63  ) + 1, 1), 'A', '=')
    ELSE SUBSTRING(@map, (@block_val / 64 & 63  ) + 1, 1) END
 + CASE WHEN @partial_block_length < 3
    THEN REPLACE(SUBSTRING(@map, (@block_val & 63) + 1, 1), 'A', '=')
    ELSE SUBSTRING(@map, (@block_val & 63) + 1, 1) END
END
--return the result
RETURN @output
END

-- ---------------------------------------------------------------------------- 
-- ${replPrefix}col2xml_base64 converts a column into a simple xml notation     
-- where the content will be decoded to base64.                                 
--   name: the name of the column                                               
--   content the content of the column (must be bytea or compatible)            
-- ---------------------------------------------------------------------------- 
CREATE FUNCTION ${replPrefix}col2xml_base64(
   @name VARCHAR, 
   @content VARCHAR(MAX))
RETURNS VARCHAR(MAX) AS BEGIN
DECLARE
   @ch  VARCHAR(MAX)
BEGIN
   SET @ch = '<col name="' + @name +
            '" encoding="base64">' +
            dbo.base64_encode(@content) +
            '</col>'
END
   RETURN  @ch;
END
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}col2xml converts a column into a simple xml notation. The       
-- output of these functions follows the notation of the dbWatcher. More about  
-- that on                                                                      
-- http://www.xmlblaster.org/xmlBlaster/doc/requirements/contrib.dbwatcher.html 
--   name: the name of the column                                               
--   content the content of the column. If it is a blob use                     
-- ${replPrefix}col2xml_base64.                                                 
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}col2xml(name VARCHAR, content VARCHAR(MAX))
RETURN VARCHAR(MAX) AS
   pos INTEGER;
   tmp VARCHAR(MAX);
   ch  VARCHAR(40);
BEGIN
   tmp := EMPTY_VARCHAR(MAX);
   dbms_lob.createtemporary(tmp, TRUE);
   dbms_lob.open(tmp, dbms_lob.lob_readwrite);
   pos := ${replPrefix}needs_prot(content);
   IF pos = 0 THEN
      ch := '<col name="';
      dbms_lob.writeappend(tmp, length(ch), ch);
      dbms_lob.writeappend(tmp, length(name), name);
      ch := '">';
      dbms_lob.writeappend(tmp, length(ch), ch);
      dbms_lob.append(tmp, content);
      ch := '</col>';
      dbms_lob.writeappend(tmp, length(ch), ch);
      dbms_lob.close(tmp);
      RETURN tmp;
   END IF;
   IF pos = 1 THEN 
      RETURN ${replPrefix}col2xml_cdata(name, content);
   END IF; 
   ch := '<col name="';
   dbms_lob.writeappend(tmp, length(ch), ch);
   dbms_lob.writeappend(tmp, length(name), name);
   ch := '" encoding="base64">';
   dbms_lob.writeappend(tmp, length(ch), ch);
   dbms_lob.append(tmp, ${replPrefix}base64_enc_clob(content));
   ch := '</col>';
   dbms_lob.writeappend(tmp, length(ch), ch);
   dbms_lob.close(tmp);
   RETURN  tmp;
END ${replPrefix}col2xml;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}check_structure is used to check wether a table has been        
-- created, dropped or altered.                                                 
-- ---------------------------------------------------------------------------- 

CREATE FUNCTION ${replPrefix}check_structure()
   RETURNS VARCHAR(50) AS
BEGIN
DECLARE
   @dummy VARCHAR(50)
BEGIN
   set @dummy = 'OK'
END
   RETURN @dummy
END

-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}check_tables is used to check wether a table has been created,  
-- dropped or altered.                                                          
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}check_tables(dbName VARCHAR, schName 
                                          VARCHAR, tblName VARCHAR, op VARCHAR,
					  cont VARCHAR)
   RETURN VARCHAR AS
   transId    VARCHAR(${charWidth});
   res        VARCHAR(${charWidthSmall});
   tmp        INTEGER;
   replKey    INTEGER;
   contClob   VARCHAR(MAX);
BEGIN
   ${replPrefix}debug('CHECK_TABLES ' || schName || '.' || tblName);
--   SELECT count(*) INTO tmp FROM all_tables WHERE (table_name=tblName         
--                   OR table_name=UPPER(tblName) OR table_name=LOWER(tblName)) 
--		   AND (owner=schName OR owner=UPPER(schName) OR                
--		   owner=LOWER(schName));                                       

   SELECT count(*) INTO tmp FROM sys.all_tables WHERE table_name=tblName AND owner=schName;

   ${replPrefix}debug('CHECK_TABLES count=' || TO_CHAR(tmp));
   -- tmp := 1; -- THIS IS A HACK. TODO: Fix this, strangely a foreign schema table returns 0
             -- even if it exists (this hack makes the assumption the table exists)
   IF tmp = 0 THEN 
      res := 'FALSE';
   ELSE
      if cont = NULL THEN
         contClob := EMPTY_VARCHAR(MAX);
         dbms_lob.writeappend(contClob, length(cont), cont);
         dbms_lob.close(contClob);
      ELSE
	contClob := NULL;
      END IF;

      transId := DBMS_TRANSACTION.LOCAL_TRANSACTION_ID(FALSE);
      SELECT ${replPrefix}seq.nextval INTO replKey FROM DUAL;
      INSERT INTO ${replPrefix}items (repl_key, trans_key, dbId, tablename, 
                  guid, db_action, db_catalog, db_schema, content, oldContent,
                  version) values (replKey, transId, dbName, tblName,
                  NULL, op, NULL, schName, cont, NULL,  '0.0');
      res := 'TRUE';
   END IF;        
   RETURN res;
END ${replPrefix}check_tables;
-- EOC (end of command: needed as a separator for our script parser)            

-- TRIGGER for DELETED INSERTED ms-help://MS.SQLCC.v9/MS.SQLSVR.v9.en/udb9/html/ed84567f-7b91-4b44-b5b2-c400bda4590d.htm

-- ---------------------------------------------------------------------------- 
-- ${replPrefix}tables_trigger is invoked when a change occurs on               
-- ${replPrefix}tables.                                                         
-- TODO: need to get DB_NAME and SCHEMA_NAME from system properties             
-- ---------------------------------------------------------------------------- 
CREATE TRIGGER repl_insert_tables_trigger
ON repl_tables
AFTER INSERT
AS 
BEGIN
DECLARE
      @op         VARCHAR(50),
      @tableName  VARCHAR(50),
      @ret        VARCHAR(30),
      @schemaName VARCHAR(50),
      @dbName     VARCHAR(50)
BEGIN
   SET @op = 'CREATE'
   SELECT @tableName = tablename FROM inserted
   SELECT @schemaName = schemaname FROM inserted
   SET @ret = test.dbo.repl_check_tables(@dbName, @schemaName, @tableName, @op, NULL)
END
END

-- OLD: Replace
CREATE TRIGGER ${replPrefix}tables_trigger AFTER UPDATE OR DELETE OR INSERT
ON ${replPrefix}tables
FOR EACH ROW
   DECLARE
      op         VARCHAR(${charWidth});
      tableName  VARCHAR(${charWidth});
      ret        VARCHAR(${charWidthSmall});
      -- these need to be replaced later on !!!!
      schemaName VARCHAR(${charWidth});
      dbName     VARCHAR(${charWidth});
BEGIN
   ${replPrefix}debug('TABLES TRIGGER ENTERING');
   schemaName := '';
   dbName := '';

   op := 'UNKNOWN';
   IF INSERTING THEN
      op := 'CREATE';
      tableName := :new.tablename;
      schemaName := :new.schemaname;
   ELSIF DELETING THEN
      op := 'DROP';
      tableName := :old.tablename;
      schemaName := :old.schemaname;
   ELSE
      op := 'REPLMOD';
      tableName := :new.tablename;
   END IF;
   ret := ${replPrefix}check_tables(dbName, schemaName, tableName, op, NULL);
END ${replPrefix}tables_trigger;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}tables_func is invoked by the trigger on ${replPrefix}tables.   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}increment
   RETURN INTEGER AS
   val INTEGER;
BEGIN
   SELECT ${replPrefix}seq.nextval INTO val FROM DUAL;
   RETURN val;
END ${replPrefix}increment;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}add_table is invoked by the triggers on schemas (can also be    
-- invoked outside the triggers by the DbWatcher via JDBC) to determine if a    
-- table has to be replicated. If the table exists it also adds it to the       
-- ${replPrefix}items table.                                                    
-- dbName the name of the database                                              
-- tblName the name of the table to be replicated.                              
-- schemaName the name of the schema containing this table.                     
-- op the name of the operation. It can be CREATE, ALTER or DROP.               
-- returns TRUE if the table exists (has to be replicated) or FALSE otherwise.  
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}add_table(dbName VARCHAR, schName 
                               VARCHAR, tblName VARCHAR, op VARCHAR)
   RETURN VARCHAR AS
   replKey INTEGER;
   transId VARCHAR(${charWidth});
   tmp     NUMBER;
   res     VARCHAR(${charWidthSmall});
BEGIN


   ${replPrefix}debug('ADD_TABLE ' || schName || '.' || tblName);

   SELECT count(*) INTO tmp FROM ${replPrefix}tables WHERE (tablename=tblName 
                   OR tablename=UPPER(tblName) OR tablename=LOWER(tblName))
		   AND (schemaname=schName OR schemaname=UPPER(schName) OR 
		   schemaname=LOWER(schName));
   ${replPrefix}debug('ADD_TABLE count=' || TO_CHAR(tmp));
   IF tmp = 0 THEN
      res := 'FALSE';
   ELSE
      ${replPrefix}debug('ADD_TABLE inserting entry into items table');
      transId := DBMS_TRANSACTION.LOCAL_TRANSACTION_ID(FALSE);
      SELECT ${replPrefix}seq.nextval INTO replKey FROM DUAL;
      INSERT INTO ${replPrefix}items (repl_key, trans_key, dbId, tablename, 
                  guid, db_action, db_catalog, db_schema, content, oldContent,
                  version) values (replKey, transId, dbName, tblName,
                  NULL, op, NULL, schName, NULL, NULL,  '0.0');
      res := 'TRUE';
   END IF;
   RETURN res;
END ${replPrefix}add_table;
-- EOC (end of command: needed as a separator for our script parser)            

