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
-- Before that we need to manually register (in case this is not already done)  
-- the PL-SQL language hander:                                                  
-- Manual Installation of PL/pgSQL (you must have the rights to do so to make   
-- it simple be the user 'postgres'):                                           
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION plpgsql_call_handler()                            
       RETURNS language_handler AS '$libdir/plpgsql' LANGUAGE C;             
CREATE OR REPLACE FUNCTION plpgsql_validator(oid)                            
       RETURNS void AS '$libdir/plpgsql' LANGUAGE C;                         
CREATE TRUSTED PROCEDURAL LANGUAGE plpgsql                                   
       HANDLER plpgsql_call_handler VALIDATOR plpgsql_validator;             
-- FLUSH (dropped ${replPrefix}cols_view)                                       

-- note that what written here is only needed once (this is why we have         
-- commented out it)                                                            
-- ---------------------------------------------------------------------------- 

-- ---------------------------------------------------------------------------- 
-- WE FIRST CREATE THE TABLE HOLDING A LIST OF ALL TABLES TO BE REPLICATED      
-- ---------------------------------------------------------------------------- 

DROP VIEW ${replPrefix}cols_view;
-- FLUSH (dropped ${replPrefix}cols_view)                                       
DROP TABLE ${replPrefix}tables CASCADE;
-- FLUSH (dropped ${replPrefix}tables)                                          
DROP TABLE ${replPrefix}current_tables CASCADE;
-- FLUSH (dropped ${replPrefix}current_tables)                                  
DROP TABLE ${replPrefix}cols_table CASCADE;
-- FLUSH (dropped ${replPrefix}cols_table)                                      
DROP SEQUENCE ${replPrefix}seq;
-- FLUSH (dropped ${replPrefix}seq)                                             
DROP TABLE ${replPrefix}items CASCADE;
-- FLUSH (dropped ${replPrefix}items)                                           

-- ---------------------------------------------------------------------------- 
-- This table contains the list of tables to watch.                             
-- tablename is the name of the table to watch                                  
--- replicate is the flag indicating 't' will replicate, 'f' will not replicate,
-- it will only watch for initial replication.                                  
-- ---------------------------------------------------------------------------- 

CREATE TABLE ${replPrefix}tables(catalogname VARCHAR(30), schemaname 
                         VARCHAR(30), tablename VARCHAR(30), 
			 repl_flags CHAR(3), status VARCHAR(10), 
			 repl_key INTEGER, trigger_name VARCHAR(30), 
			 debug INTEGER, 
			 PRIMARY KEY(catalogname, schemaname, tablename));

-- ---------------------------------------------------------------------------- 
-- create the ${replPrefix}current_tables as a placeholder for the current      
-- tables (this is used to detect a CREATE TABLE and a DROP TABLE.              
-- ---------------------------------------------------------------------------- 

CREATE TABLE ${replPrefix}current_tables AS SELECT relname 
       FROM pg_statio_user_tables WHERE relname IN (SELECT tablename 
       FROM ${replPrefix}tables);

-- ---------------------------------------------------------------------------- 
-- A Difference between these two means that an ALTER operation has been        
-- invoked.                                                                     
-- ---------------------------------------------------------------------------- 

CREATE VIEW ${replPrefix}cols_view AS SELECT attname,(SELECT relname 
       FROM pg_class WHERE oid=attrelid) AS owner, atttypid, attlen, attnotnull, 
       attnum FROM pg_attribute WHERE attnum > 0 AND (SELECT relname FROM 
       pg_class WHERE oid=attrelid) IN (SELECT tablename 
       FROM ${replPrefix}tables) ORDER BY owner, attnum;

CREATE TABLE ${replPrefix}cols_table AS SELECT attname,(SELECT relname 
       FROM pg_class WHERE oid=attrelid) AS owner, atttypid, attlen, attnotnull, 
       attnum FROM pg_attribute WHERE attnum > 0 AND (SELECT relname 
       FROM pg_class WHERE oid=attrelid) IN (SELECT tablename 
       FROM ${replPrefix}tables) ORDER BY owner, attnum;

-- ---------------------------------------------------------------------------- 
-- Invoked to detect if a table has been altered.                               
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}is_altered(name text) 
       RETURNS BOOLEAN AS $${replPrefix}is_altered$
  DECLARE
     colVar RECORD;
     md5A TEXT;
     md5B TEXT;
     tmp  TEXT;
  BEGIN
   FOR colVar IN (SELECT * FROM ${replPrefix}cols_table WHERE owner=$1) LOOP
      md5A = colVar.attname;
      tmp = colVar.atttypid;
      md5A = md5A || tmp;
      tmp = colVar.attlen;
      md5A = md5A || tmp;
      tmp = colvar.attnotnull;
      md5A = md5A || tmp;
      tmp = colVar.attnum;
      md5A = md5A || tmp;
   END LOOP;
   FOR colVar IN (SELECT * FROM ${replPrefix}cols_view WHERE owner=$1) LOOP
      md5B = colVar.attname;
      tmp = colVar.atttypid;
      md5B = md5B || tmp;
      tmp = colVar.attlen;
      md5B = md5B || tmp;
      tmp = colvar.attnotnull;
      md5B = md5B || tmp;
      tmp = colVar.attnum;
      md5B = md5B || tmp;
   END LOOP;

   RETURN md5A != md5B;
END;
$${replPrefix}is_altered$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- We create the table which will be used for the outgoing replica messages and 
-- a sequence needed for a monotone increasing sequence for the primary key.    
-- In postgres this will implicitly create an index "repltest_pkey" for this    
-- table. The necessary generic lowlevel functions are created.                 
-- ---------------------------------------------------------------------------- 

CREATE SEQUENCE ${replPrefix}seq MINVALUE 1 MAXVALUE 1000000000 CYCLE;

CREATE TABLE ${replPrefix}items (repl_key INTEGER DEFAULT 
             nextval('${replPrefix}seq'), trans_key VARCHAR(30), 
	     dbId VARCHAR(30), tablename VARCHAR(30), guid VARCHAR(30), 
	     db_action VARCHAR(15), db_catalog VARCHAR(30),
	     db_schema VARCHAR(30), content TEXT, oldContent TEXT, 
	     version VARCHAR(10), PRIMARY KEY (repl_key));

-- ---------------------------------------------------------------------------- 
-- ${replPrefix}needs_prot (prot stands for protection) detects wether a        
-- protection to  BASE64 is needed or not in a text string.                     
-- returns an integer. If 1 it means CDATA protection will suffice, if 2 it     
-- means it needs BASE64.                                                       
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}needs_prot(content text) 
RETURNS INT AS $${replPrefix}needs_prot$
DECLARE
   pos INT;
BEGIN
   pos = position(']]>' IN content);
   IF POS > 0 THEN
      RETURN 2;
   END IF;
   pos = position('<' IN content);
   IF POS > 0 THEN 
      RETURN 1;
   END IF;
   pos = position('&' IN content);
   IF POS > 0 THEN 
      RETURN 1;
   END IF;
   RETURN 0;
END;
$${replPrefix}needs_prot$ LANGUAGE 'plpgsql';

-- ---------------------------------------------------------------------------- 
-- ${replPrefix}col2xml converts a column into a simple xml notation. The       
-- output of these functions follows the notation of the dbWatcher. More about  
-- that on                                                                      
-- http://www.xmlblaster.org/xmlBlaster/doc/requirements/contrib.dbwatcher.html 
--   name: the name of the column                                               
--   content the content of the column. If it is a blob                         
-- use ${replPrefix}col2xml_base64.                                             
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}col2xml(name text, content text) 
RETURNS text AS $${replPrefix}col2xml$
DECLARE pos INT;
        blobCont BYTEA;
BEGIN
   pos = ${replPrefix}needs_prot(content);
   IF pos = 0 THEN
      RETURN '<col name="' || name || '">' || content || '</col>';
   END IF;
   IF pos = 1 THEN 
      RETURN ${replPrefix}col2xml_cdata(name, content);
   END IF; 
   blobCont = content; -- needed for the conversion
   RETURN ${replPrefix}col2xml_base64(name, blobCont);
END;
$${replPrefix}col2xml$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}col2xml_base64 converts a column into a simple xml notation     
-- where the                                                                    
-- content will be decoded to base64.                                           
--   name: the name of the column                                               
--   content the content of the column (must be bytea or compatible)            
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}col2xml_base64(name text, content bytea) 
RETURNS text AS $${replPrefix}col2xml_base64$
BEGIN 
   RETURN '<col name="' || name || '" encoding="base64">' ||  
           encode(content,'base64') || '</col>';
END;
$${replPrefix}col2xml_base64$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}col2xml_cdata converts a column into a simple xml notation and  
-- wraps the content into a _cdata object.                                      
--   name: the name of the column                                               
--   content the content of the column. If it is a blob use                     
--${replPrefix}col2xml_base64                                                   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}col2xml_cdata(name text, content text) 
RETURNS text AS $${replPrefix}col2xml_cdata$
BEGIN
   RETURN '<col name="' || name || '"><![CDATA[' || 
           content || ']]></col>';
END;
$${replPrefix}col2xml_cdata$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}check_structure is used to check wether a table has been        
-- created, dropped or altered.                                                 
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}check_structure()
   RETURNS TEXT AS $${replPrefix}check_structure$
   DECLARE colVar  RECORD;
           counter INT4;
BEGIN
    counter = 0;
    FOR colVar IN (SELECT relname FROM ${replPrefix}current_tables WHERE 
               relname NOT IN (SELECT relname FROM pg_statio_user_tables WHERE 
	       relname IN (SELECT tablename FROM ${replPrefix}tables)))
    LOOP
      INSERT INTO ${replPrefix}items (trans_key, dbId, tablename, guid, 
                       db_action, db_catalog, db_schema, content, oldContent, 
		       version) values (CURRENT_TIMESTAMP, current_database(), 
		       colVar.relname, colVar.oid, 'DROP', NULL, 
		       current_schema(), NULL, NULL, '0.0');
      counter = 1;
    END LOOP;					 
    FOR colVar IN (SELECT relname FROM pg_statio_user_tables WHERE relname 
	       IN (SELECT tablename FROM ${replPrefix}tables) AND (relname NOT 
	       IN (SELECT relname FROM ${replPrefix}current_tables)))
      LOOP
      INSERT INTO ${replPrefix}items (trans_key, dbId, tablename, guid, 
                       db_action, db_catalog, db_schema, content, oldContent, 
		       version) values (CURRENT_TIMESTAMP, 
		       current_database(), colVar.relname, colVar.oid, 
		       'CREATE', NULL, current_schema(), NULL, NULL, '0.0');
      counter = 1;
    END LOOP;
    IF counter > 0 THEN
       TRUNCATE ${replPrefix}current_tables;
       FOR colVar IN (SELECT relname FROM pg_statio_user_tables WHERE relname 
                  IN (SELECT tablename FROM ${replPrefix}tables))
       LOOP 
         INSERT INTO ${replPrefix}current_tables (relname) 
	        VALUES (colVar.relname);
       END LOOP;
    --    the following would not work (probably because table is cached)         
    --    CREATE TABLE ${replPrefix}current_tables AS SELECT relname FROM         
    --    pg_statio_user_tables;                                                  
    END IF;
    -- now CREATE and DROP have been handled ...                                  
    -- now we must handle ALTER TABLE (or column)                                 
    counter = 0;
    FOR colVar IN (SELECT relname FROM ${replPrefix}current_tables) LOOP
       IF ${replPrefix}is_altered(colVar.relname) = 't' THEN
         INSERT INTO ${replPrefix}items (trans_key, dbId, tablename, guid, 
	               db_action, db_catalog, db_schema, content, oldContent, 
		       version) VALUES (CURRENT_TIMESTAMP, 
		       current_database(), colVar.relname, colVar.oid, 
		       'ALTER', NULL, current_schema(), NULL, NULL, 
		       '0.0');
	 counter = 1;
       END IF;
    END LOOP;
    IF counter > 0 THEN
      TRUNCATE ${replPrefix}cols_table;
      FOR colVar IN (SELECT * FROM ${replPrefix}cols_view) LOOP
         INSERT INTO ${replPrefix}cols_table VALUES (colVar.attname, colVar.owner, 
	                                   colVar.atttypid, colVar.attlen, 
					   colVar.attnotnull, colVar.attnum);
      END LOOP;
    END IF;
    RETURN 'OK';
END;
$${replPrefix}check_structure$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}tables_func is invoked by the trigger on ${replPrefix}tables.   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}tables_func() 
   RETURNS trigger AS $${replPrefix}tables_func$
   DECLARE
      ret TEXT;
BEGIN
   ret = ${replPrefix}check_structure();
   RETURN NULL;
END;
$${replPrefix}tables_func$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}tables_trigger is invoked when a change occurs                  
-- on ${replPrefix}tables.                                                      
-- ---------------------------------------------------------------------------- 

CREATE TRIGGER ${replPrefix}tables_trigger AFTER UPDATE OR DELETE OR INSERT
ON ${replPrefix}tables
FOR EACH ROW
EXECUTE PROCEDURE ${replPrefix}tables_func();


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}tables_func is invoked by the trigger on ${replPrefix}tables.   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}increment() 
   RETURNS INTEGER AS $${replPrefix}increment$
BEGIN
   RETURN nextval('${replPrefix}seq');
END;
$${replPrefix}increment$ LANGUAGE 'plpgsql';

