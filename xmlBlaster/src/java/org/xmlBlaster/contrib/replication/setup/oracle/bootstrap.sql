-- ---------------------------------------------------------------------------- 
-- Written by Michele Laghi (laghi@swissinfo.org) 2005-08-09                    
--                                                                              
-- Some Comments:                                                               
--  The effect of triggers has been checked. An open issue is how to determine  
--  wether an action has been caused by a direct operation of the user (primary 
--  action) or if it is a reaction to that as an operation performed by a       
--  trigger (reaction).                                                         
--                                                                              
-- NOTES:                                                                       
-- Avoid the usage of TRUNCATE in the tables to be replicated, since the        
-- deletion seems not to detect such a change.                                  
-- ---------------------------------------------------------------------------- 


-- ---------------------------------------------------------------------------- 
-- WE FIRST CREATE THE TABLE HOLDING A LIST OF ALL TABLES TO BE REPLICATED      
-- ---------------------------------------------------------------------------- 


DROP TRIGGER repl_create_trigger_xmlblaster
-- FLUSH (dropped repl_create_trigger_xmlblaster)                               
DROP TRIGGER repl_drop_trigger_xmlblaster
-- FLUSH (dropped repl_drop_trigger_xmlblaster)                                 
DROP TRIGGER repl_alter_trigger_xmlblaster
-- FLUSH (dropped repl_alter_trigger_xmlblaster)                                
DROP VIEW repl_cols_view
-- FLUSH (dropped repl_cols_view)                                               
DROP TABLE repl_tables
-- FLUSH (dropped repl_tables)                                                  
DROP TABLE repl_current_tables
-- FLUSH (dropped repl_current_tables)                                          
DROP TABLE repl_cols_table
-- FLUSH (dropped repl_cols_table)                                              
DROP SEQUENCE repl_seq
-- FLUSH (dropped repl_seq)                                                     
DROP TABLE repl_items
-- FLUSH (dropped repl_items)                                                   

-- ---------------------------------------------------------------------------- 
-- This table contains the list of tables to watch.                             
-- tablename is the name of the table to watch                                  
--- replicate is the flag indicating 't' will replicate, 'f' will not replicate,
-- it will only watch for initial replication.                                  
-- ---------------------------------------------------------------------------- 

CREATE TABLE repl_tables(catalogname VARCHAR(30), schemaname VARCHAR(30),
                         tablename VARCHAR(30), replicate CHAR(1),
			 status VARCHAR(10), repl_key INTEGER, 
			 PRIMARY KEY(catalogname, schemaname, tablename))
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- create the repl_current_tables as a placeholder for the current tables (this 
-- is used to detect a CREATE TABLE and a DROP TABLE.                           
-- ---------------------------------------------------------------------------- 

CREATE TABLE repl_current_tables AS SELECT table_name AS relname FROM all_tables
       WHERE table_name IN (SELECT tablename FROM repl_tables)
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- We create the table which will be used for the outgoing replica messages and 
-- a sequence needed for a monotone increasing sequence for the primary key.    
-- In postgres this will implicitly create an index "repltest_pkey" for this    
-- table. The necessary generic lowlevel functions are created.                 
-- ---------------------------------------------------------------------------- 

CREATE SEQUENCE repl_seq MINVALUE 1 MAXVALUE 1000000000 CYCLE
-- EOC (end of command: needed as a separator for our script parser)            

CREATE TABLE repl_items (repl_key INTEGER, 
             trans_key VARCHAR(30), dbId VARCHAR(30), tablename VARCHAR(30), 
	     guid VARCHAR(30), db_action VARCHAR(15), db_catalog VARCHAR(30),
	     db_schema VARCHAR(30), content CLOB, oldContent CLOB, 
	     version VARCHAR(10), PRIMARY KEY (repl_key))
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- repl_col2xml_cdata converts a column into a simple xml notation and wraps    
-- the content into a _cdata object.                                            
--   name: the name of the column                                               
--   content the content of the column. If it is a blob use repl_col2xml_base64 
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_col2xml_cdata(name CLOB, content CLOB) 
RETURN CLOB AS
BEGIN
   RETURN '<col name="' || name || '"><![CDATA[' || content || ']]></col>';
END repl_col2xml_cdata;
-- EOC (end of command: needed as a separator for our script parser)            



-- ---------------------------------------------------------------------------- 
-- repl_needs_prot (prot stands for protection) detects wether a protection to  
-- BASE64 is needed or not in a text string.                                    
-- returns an integer. If 1 it means CDATA protection will suffice, if 2 it     
-- means it needs BASE64.                                                       
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_needs_prot(content CLOB) 
RETURN INTEGER AS
   pos INTEGER;
BEGIN
   pos := INSTR(content, ']]>', 1, 1);
   IF POS > 0 THEN
      RETURN 2;
   END IF;
   pos := INSTR(content, '<', 1, 1);
   IF POS > 0 THEN 
      RETURN 1;
   END IF;
   pos := INSTR(content, '&', 1, 1);
   IF POS > 0 THEN 
      RETURN 1;
   END IF;
   RETURN 0;
END repl_needs_prot;
-- EOC (end of command: needed as a separator for our script parser)            



-- ---------------------------------------------------------------------------- 
-- repl_base64_helper is only needed in ORACLE previous to version 9 and is     
-- used by repl_base64_enc_raw and repl_base64_enc_char.                        
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_base64_helper(zeros SMALLINT, val INTEGER)
   RETURN VARCHAR AS
     numBuild INTEGER;
     char1 SMALLINT;
     char2 SMALLINT;
     char3 SMALLINT;
     char4 SMALLINT;
BEGIN
   numBuild := val;
   char1    := TRUNC(numBuild / 262144); -- 64^3 (first number)                 
   numBuild := MOD(numBuild, 262144);
   char2    := TRUNC(numBuild / 4096);   --64^2 (second number)                 
   numBuild := MOD(numBuild, 4096);
   char3    := TRUNC(numBuild / 64);     --64^1 (third number)                  
   numBuild := MOD(numBuild, 64);
   char4    := numBuild;                 --64^0 (fifth number)                  
   --Convert from actual base64 to ascii representation of base 64              

   IF char1 BETWEEN 0 AND 25 THEN
      char1 := char1 + ASCII('A');
   ELSE
      IF char1 BETWEEN 26 AND 51 THEN
         char1 := char1 + ASCII('a') - 26;
      ELSE
         IF char1 BETWEEN 52 AND 63 THEN
            IF char1 = 62 THEN
               char1 := 43;
            ELSE  
               char1 := char1 + ASCII('0') - 52;
            END IF;
         END IF;
      END IF;
   END IF;
   IF char1 = 59 THEN
      char1 := 47;
   END IF;

   IF char2 BETWEEN 0 AND 25 THEN
      char2 := char2 + ASCII('A');
   ELSE
      IF char2 BETWEEN 26 AND 51 THEN
         char2 := char2 + ASCII('a') - 26;
      ELSE
         IF char2 BETWEEN 52 AND 63 THEN
            IF char2 = 62 THEN
               char2 := 43;
            ELSE  
               char2 := char2 + ASCII('0') - 52;
            END IF; 
         END IF;
      END IF;
   END IF;
   IF char2 = 59 THEN
      char2 := 47;
   END IF;

   IF char3 BETWEEN 0 AND 25 THEN
      char3 := char3 + ASCII('A');
   ELSE
      IF char3 BETWEEN 26 AND 51 THEN
         char3 := char3 + ASCII('a') - 26;
      ELSE
         IF char3 BETWEEN 52 AND 63 THEN
            IF char3 = 62 THEN
               char3 := 43;
            ELSE  
               char3 := char3 + ASCII('0') - 52;
            END IF;
         END IF;
      END IF;
   END IF;
   IF char3 = 59 THEN
      char3 := 47;
   END IF;

   IF char4 BETWEEN 0 AND 25 THEN
      char4 := char4 + ASCII('A');
   ELSE
      IF char4 BETWEEN 26 AND 51 THEN
         char4 := char4 + ASCII('a') - 26;
      ELSE
         IF char4 BETWEEN 52 AND 63 THEN
            IF char4 = 62 THEN
               char4 := 43;
            ELSE  
               char4 := char4 + ASCII('0') - 52;
            END IF;
         END IF;
      END IF;
   END IF;
   IF char4 = 59 THEN
      char4 := 47;
   END IF;

   IF zeros > 0 THEN
      char4 := 61;
      IF zeros > 1 THEN
         char3 := 61;
         IF zeros > 2 THEN
            char2 := 61;
         END IF;
      END IF;
   END IF;

   --Add these four characters to the string                                    
   RETURN CHR(char1) || CHR(char2) || CHR(char3) || CHR(char4);
END repl_base64_helper;
-- EOC (end of command: needed as a separator for our script parser)            



-- ---------------------------------------------------------------------------- 
-- repl_base64_enc_raw converts a RAW msg to a base64 encoded string. This is   
-- needed for ORACLE versions previous to Oracle9.                              
-- content will be encoded to base64.                                           
--   name: the name of content to be encoded.                                   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_base64_enc_raw(msg RAW)
   RETURN CLOB AS
     numBuilder INTEGER;
     char1 SMALLINT;
     char2 SMALLINT;
     char3 SMALLINT;
     char4 SMALLINT;
     res   CLOB;
     i     INTEGER;
-- divisible by 3 (to avoid '=' characters at end of chunks)                    
     source RAW(32766); 
     zeros SMALLINT;
     ch    CHAR(10);
BEGIN
   source := msg;
   WHILE utl_raw.length(source) > 0 LOOP
      i := 1;
      numBuilder := 0;
      zeros := 0;
      WHILE i <= 3 LOOP
      	 IF i > utl_raw.length(source) THEN
      	    numBuilder := numBuilder*256;
	    zeros := zeros + 1;
      	 ELSE
	    ch := utl_raw.cast_to_varchar2(utl_raw.substr(source, i, 1));
      	    numBuilder := numBuilder*256 + ASCII(ch);
         END IF;
      	 i := i + 1;
      END LOOP;					  
      if utl_raw.length(source) > 3 THEN
      	source := utl_raw.substr(source, 4, utl_raw.length(source)-3);
      ELSE
      	source := '';
      END IF;
      res := res || repl_base64_helper(zeros, numBuilder);
   END LOOP;
   RETURN res;
END repl_base64_enc_raw;
-- EOC (end of command: needed as a separator for our script parser)            



-- ---------------------------------------------------------------------------- 
-- repl_base64_enc_varchar2 converts a VARCHAR2 msg to a base64 encoded string. 
-- This is needed for ORACLE versions previous to Oracle9.                      
-- content will be encoded to base64.                                           
--   name: the name of content to be encoded.                                   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_base64_enc_varchar2(msg VARCHAR2)
   RETURN CLOB AS
     numBuilder INTEGER;
     char1 SMALLINT;
     char2 SMALLINT;
     char3 SMALLINT;
     char4 SMALLINT;
     res   CLOB;
     i     INTEGER;
-- divisible by 3 (to avoid '=' characters at end of chunks)                    
     source VARCHAR2(32766); 
     zeros SMALLINT;
     ch    CHAR(2);
BEGIN
   source := msg;
   WHILE LENGTHB(source) > 0 LOOP
      i := 1;
      numBuilder := 0;
      zeros := 0;
      WHILE i <= 3 LOOP
      	 IF i > LENGTHB(source) THEN
      	    numBuilder := numBuilder*256;
	    zeros := zeros + 1;
      	 ELSE
	    ch := SUBSTRB(source, i, 1);
      	    numBuilder := numBuilder*256 + ASCII(ch);
         END IF;
      	 i := i + 1;
      END LOOP;					  
      if LENGTHB(source) > 3 THEN
      	source := SUBSTRB(source, 4, LENGTHB(source)-3);
      ELSE
      	source := '';
      END IF;
      res := res || repl_base64_helper(zeros, numBuilder);
   END LOOP;
   RETURN res;
END repl_base64_enc_varchar2;
-- EOC (end of command: needed as a separator for our script parser)            



-- ---------------------------------------------------------------------------- 
-- repl_base64_enc_blob converts a BLOB msg to a base64 encoded string. This is 
-- needed for ORACLE versions previous to Oracle9.                              
-- content will be encoded to base64.                                           
--   name: the name of content to be encoded.                                   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_base64_enc_blob(msg BLOB)
   RETURN CLOB AS
     source    BLOB;
     len       INTEGER;
     offset    INTEGER;
     tmp       RAW(32766);
     res       CLOB;
     increment INTEGER;
BEGIN
   offset := 1;

   increment := 32766;
   res := '';
   len := dbms_lob.getlength(msg);

   WHILE offset < len LOOP
      dbms_lob.read(msg, increment, offset, tmp);
      offset := offset + increment;
      -- the next line would be used for oracle from version 9 up.              
      -- res := res || utl_raw.cast_to_varchar2(utl_encode.base64_encode(tmp)); 
      res := res || repl_base64_enc_raw(tmp);
   END LOOP;
   RETURN res;
END repl_base64_enc_blob;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- repl_base64_enc_clob converts a CLOB msg to a base64 encoded string. This is 
-- needed for ORACLE versions previous to Oracle9.                              
-- content will be encoded to base64.                                           
--   name: the name of content to be encoded.                                   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_base64_enc_clob(msg CLOB)
   RETURN CLOB AS
     source CLOB;
     len    INTEGER;
     offset INTEGER;
     tmp    VARCHAR2(32766);
     res    CLOB;
BEGIN
   offset := 1;
   res := '';
   len := dbms_lob.getlength(msg);
   WHILE offset < len LOOP
      dbms_lob.read(msg, len, offset, tmp);
      offset := offset + len;
      -- the next line would be used for oracle from version 9 up.              
      -- res := res || utl_raw.cast_to_varchar2(utl_encode.base64_encode(tmp)); 
      res := res || repl_base64_enc_varchar2(tmp);
   END LOOP;
   RETURN res;
END repl_base64_enc_clob;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- repl_col2xml_base64 converts a column into a simple xml notation where the   
-- content will be decoded to base64.                                           
--   name: the name of the column                                               
--   content the content of the column (must be bytea or compatible)            
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_col2xml_base64(name CLOB, content BLOB) 
RETURN CLOB AS
  tmp CLOB;

BEGIN
  tmp := '<col name="' || name || '" encoding="base64">' ||  
          repl_base64_enc_blob(content) || '</col>';
  RETURN  tmp;
END repl_col2xml_base64;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- repl_col2xml converts a column into a simple xml notation. The output of     
-- these functions follows the notation of the dbWatcher. More about that on    
-- http://www.xmlblaster.org/xmlBlaster/doc/requirements/contrib.dbwatcher.html 
--   name: the name of the column                                               
--   content the content of the column. If it is a blob use repl_col2xml_base64.
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_col2xml(name CLOB, content CLOB) 	
RETURN CLOB AS
   pos INTEGER;
BEGIN
   pos := repl_needs_prot(content);
   IF pos = 0 THEN
      RETURN '<col name="' || name || '">' || content || '</col>';
   END IF;
   IF pos = 1 THEN 
      RETURN repl_col2xml_cdata(name, content);
   END IF; 
   RETURN '<col name="' || name || '" encoding="base64">' ||  
          repl_base64_enc_clob(content) || '</col>';
END repl_col2xml;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- repl_check_structure is used to check wether a table has been created,       
-- dropped or altered.                                                          
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_check_structure
   RETURN CLOB AS
BEGIN
   RETURN 'OK';
END repl_check_structure;
-- EOC (end of command: needed as a separator for our script parser)            

-- ---------------------------------------------------------------------------- 
-- repl_check_tables is used to check wether a table has been created,          
-- dropped or altered.                                                          
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_check_tables(dbName VARCHAR, schName VARCHAR, 
                                             tblName VARCHAR, op VARCHAR)
   RETURN VARCHAR AS
   transId    VARCHAR(30);
   res        VARCHAR(10);
   tmp        INTEGER;
   replKey    INTEGER;
BEGIN
   SELECT count(*) INTO tmp FROM all_tables WHERE (table_name=tblName                 
                   OR table_name=UPPER(tblName) OR table_name=LOWER(tblName))
		   AND (owner=schName OR owner=UPPER(schName) OR 
		   owner=LOWER(schName));
   IF tmp = 0 THEN 
      res := 'FALSE';
   ELSE
      transId := DBMS_TRANSACTION.LOCAL_TRANSACTION_ID(FALSE);
      SELECT repl_seq.nextval INTO replKey FROM DUAL;
      INSERT INTO repl_items (repl_key, trans_key, dbId, tablename, guid,
                        db_action, db_catalog, db_schema, content, oldContent,
                        version) values (replKey, transId, dbName, tblName,
                        NULL, op, NULL, schName, NULL, NULL,  '0.0');
      res := 'TRUE';
   END IF;        
   RETURN res;
END repl_check_tables;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- repl_tables_trigger is invoked when a change occurs on repl_tables.          
-- TODO: need to get DB_NAME and SCHEMA_NAME from system properties             
-- ---------------------------------------------------------------------------- 

CREATE TRIGGER repl_tables_trigger AFTER UPDATE OR DELETE OR INSERT
ON repl_tables
FOR EACH ROW
   DECLARE
      op         VARCHAR(30);
      tableName  VARCHAR(30);
      ret        VARCHAR(10);
      -- these need to be replaced later on !!!!
      schemaName VARCHAR(30);
      dbName     VARCHAR(30);
BEGIN
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
   ret := repl_check_tables(dbName, schemaName, tableName, op);
END repl_tables_trigger;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- repl_tables_func is invoked by the trigger on repl_tables.                   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_increment
   RETURN INTEGER AS
   val INTEGER;
BEGIN
   SELECT repl_seq.nextval INTO val FROM DUAL;
   RETURN val;
END repl_increment;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- repl_add_table is invoked by the triggers on schemas (can also be invoked    
-- outside the triggers by the DbWatcher via JDBC) to determine if a table has  
-- to be replicated. If the table exists it also adds it to the repl_items      
-- table.                                                                       
-- dbName the name of the database                                              
-- tblName the name of the table to be replicated.                              
-- schemaName the name of the schema containing this table.                     
-- op the name of the operation. It can be CREATE, ALTER or DROP.               
-- returns TRUE if the table exists (has to be replicated) or FALSE otherwise.  
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_add_table(dbName VARCHAR, schName VARCHAR, 
                                           tblName VARCHAR, op VARCHAR)
   RETURN VARCHAR AS
   replKey INTEGER;
   transId VARCHAR2(30);
   tmp     NUMBER;
   res     VARCHAR(10);
BEGIN
   SELECT count(*) INTO tmp FROM repl_tables WHERE (tablename=tblName 
                   OR tablename=UPPER(tblName) OR tablename=LOWER(tblName))
		   AND (schemaname=schName OR schemaname=UPPER(schName) OR 
		   schemaname=LOWER(schName));
   IF tmp = 0 THEN
      res := 'FALSE';
   ELSE
      transId := DBMS_TRANSACTION.LOCAL_TRANSACTION_ID(FALSE);
      SELECT repl_seq.nextval INTO replKey FROM DUAL;
      INSERT INTO repl_items (repl_key, trans_key, dbId, tablename, guid,
                  db_action, db_catalog, db_schema, content, oldContent,
                  version) values (replKey, transId, dbName, tblName,
                  NULL, op, NULL, schName, NULL, NULL,  '0.0');
      res := 'TRUE';
   END IF;
   RETURN res;
END repl_add_table;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- repl_create_trigger is invoked when a new table is created.                  
-- note that this must be invoked for each Schema.                              
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE TRIGGER repl_create_trigger_xmlblaster
   AFTER CREATE ON XMLBLASTER.SCHEMA
DECLARE
   dbName     VARCHAR(30);
   tableName  VARCHAR(30);
   schemaName VARCHAR(30);
   dummy      VARCHAR(10);
BEGIN
   dbName     := DATABASE_NAME;
   tableName  := DICTIONARY_OBJ_NAME;
   schemaName := DICTIONARY_OBJ_OWNER;
   dummy := repl_add_table(dbName, schemaName, tableName, 'CREATE');
END repl_create_trigger_xmlblaster;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- repl_drop_trigger is invoked when a table is dropped.                        
-- note that this must be invoked for each Schema.                              
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE TRIGGER repl_drop_trigger_xmlblaster
   BEFORE DROP ON XMLBLASTER.SCHEMA
DECLARE
   dbName     VARCHAR(30);
   tableName  VARCHAR(30);
   schemaName VARCHAR(30);
   dummy      VARCHAR(10);
BEGIN
   dbName     := DATABASE_NAME;
   tableName  := DICTIONARY_OBJ_NAME;
   schemaName := DICTIONARY_OBJ_OWNER;
   dummy := repl_add_table(dbName, schemaName, tableName, 'DROP');
END repl_drop_trigger_xmlblaster;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- repl_alter_trigger is invoked when a table is altered (modified).            
-- note that this must be invoked for each Schema.                              
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE TRIGGER repl_alter_trigger_xmlblaster
   AFTER ALTER ON XMLBLASTER.SCHEMA
DECLARE
   dbName     VARCHAR(30);
   tableName  VARCHAR(30);
   schemaName VARCHAR(30);
   dummy      VARCHAR(10);
BEGIN
   dbName     := DATABASE_NAME;
   tableName  := DICTIONARY_OBJ_NAME;
   schemaName := DICTIONARY_OBJ_OWNER;
   dummy := repl_add_table(dbName, schemaName, tableName, 'ALTER');
END repl_alter_trigger_xmlblaster;
-- EOC (end of command: needed as a separator for our script parser)            


