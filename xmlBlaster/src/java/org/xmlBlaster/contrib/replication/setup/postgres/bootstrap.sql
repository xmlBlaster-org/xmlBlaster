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
-- FLUSH (dropped repl_cols_view)                                               

-- note that what written here is only needed once (this is why we have         
-- commented out it)                                                            
-- ---------------------------------------------------------------------------- 

-- ---------------------------------------------------------------------------- 
-- WE FIRST CREATE THE TABLE HOLDING A LIST OF ALL TABLES TO BE REPLICATED      
-- ---------------------------------------------------------------------------- 

DROP VIEW repl_cols_view;
-- FLUSH (dropped repl_cols_view)                                               
DROP TABLE repl_tables CASCADE;
-- FLUSH (dropped repl_tables)                                                  
DROP TABLE repl_current_tables CASCADE;
-- FLUSH (dropped repl_current_tables)                                          
DROP TABLE repl_cols_table CASCADE;
-- FLUSH (dropped repl_cols_table)                                              
DROP SEQUENCE repl_seq;
-- FLUSH (dropped repl_seq)                                                     
DROP TABLE repl_items CASCADE;
-- FLUSH (dropped repl_items)                                                    

-- ---------------------------------------------------------------------------- 
-- This table contains the list of tables to watch.                             
-- tablename is the name of the table to watch                                  
--- replicate is the flag indicating 't' will replicate, 'f' will not replicate,
-- it will only watch for initial replication.                                  
-- ---------------------------------------------------------------------------- 

CREATE TABLE repl_tables(tablename VARCHAR(30), replicate CHAR(1), 
                         PRIMARY KEY(tablename));


-- ---------------------------------------------------------------------------- 
-- create the repl_current_tables as a placeholder for the current tables (this 
-- is used to detect a CREATE TABLE and a DROP TABLE.                           
-- ---------------------------------------------------------------------------- 

CREATE TABLE repl_current_tables AS SELECT relname FROM pg_statio_user_tables 
       WHERE relname IN (SELECT tablename FROM repl_tables);


-- ---------------------------------------------------------------------------- 
-- A Difference between these two means that an ALTER operation has been        
-- invoked.                                                                     
-- ---------------------------------------------------------------------------- 

CREATE VIEW repl_cols_view AS SELECT attname,(SELECT relname FROM pg_class 
       WHERE oid=attrelid) AS owner, atttypid, attlen, attnotnull, attnum FROM 
       pg_attribute WHERE attnum > 0 AND (SELECT relname FROM pg_class WHERE 
       oid=attrelid) IN (SELECT tablename FROM repl_tables) 
       ORDER BY owner, attnum;

CREATE TABLE repl_cols_table AS SELECT attname,(SELECT relname FROM pg_class 
       WHERE oid=attrelid) AS owner, atttypid, attlen, attnotnull, attnum FROM 
       pg_attribute WHERE attnum > 0 AND (SELECT relname FROM pg_class WHERE 
       oid=attrelid) IN (SELECT tablename FROM repl_tables) 
       ORDER BY owner, attnum;


-- ---------------------------------------------------------------------------- 
-- Invoked to detect if a table has been altered.                               
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_is_altered(name text) 
       RETURNS BOOLEAN AS $repl_is_altered$
  DECLARE
     colVar RECORD;
     md5A TEXT;
     md5B TEXT;
     tmp  TEXT;
  BEGIN
   FOR colVar IN (SELECT * FROM repl_cols_table WHERE owner=$1) LOOP
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
   FOR colVar IN (SELECT * FROM repl_cols_view WHERE owner=$1) LOOP
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
$repl_is_altered$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- We create the table which will be used for the outgoing replica messages and 
-- a sequence needed for a monotone increasing sequence for the primary key.    
-- In postgres this will implicitly create an index "repltest_pkey" for this    
-- table. The necessary generic lowlevel functions are created.                 
-- ---------------------------------------------------------------------------- 

CREATE SEQUENCE repl_seq MINVALUE 1 MAXVALUE 1000000000 CYCLE;

CREATE TABLE repl_items (repl_key INTEGER DEFAULT nextval('repl_seq'), 
             trans_stamp TIMESTAMP, dbId VARCHAR(30), tablename VARCHAR(30), 
	     guid VARCHAR(30), db_action VARCHAR(15), db_catalog VARCHAR(30),
	     db_schema VARCHAR(30), content TEXT, oldContent TEXT, 
	     version VARCHAR(10), PRIMARY KEY (repl_key));

-- ---------------------------------------------------------------------------- 
-- repl_needs_prot (prot stands for protection) detects wether a protection to  
-- BASE64 is needed or not in a text string.                                    
-- returns an integer. If 1 it means CDATA protection will suffice, if 2 it     
-- means it needs BASE64.                                                       
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_needs_prot(content text) 
RETURNS INT AS $repl_needs_prot$
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
$repl_needs_prot$ LANGUAGE 'plpgsql';

-- ---------------------------------------------------------------------------- 
-- repl_col2xml converts a column into a simple xml notation. The output of     
-- these functions follows the notation of the dbWatcher. More about that on    
-- http://www.xmlblaster.org/xmlBlaster/doc/requirements/contrib.dbwatcher.html 
--   name: the name of the column                                               
--   content the content of the column. If it is a blob use repl_col2xml_base64.
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_col2xml(name text, content text) 
RETURNS text AS $repl_col2xml$
DECLARE pos INT;
        blobCont BYTEA;
BEGIN
   pos = repl_needs_prot(content);
   IF pos = 0 THEN
      RETURN '<col name=\'' || name || '\'>' || content || '</col>';
   END IF;
   IF pos = 1 THEN 
      RETURN repl_col2xml_cdata(name, content);
   END IF; 
   blobCont = content; -- needed for the conversion
   RETURN repl_col2xml_base64(name, blobCont);
END;
$repl_col2xml$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- repl_col2xml_base64 converts a column into a simple xml notation where the   
-- content will be decoded to base64.                                           
--   name: the name of the column                                               
--   content the content of the column (must be bytea or compatible)            
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_col2xml_base64(name text, content bytea) 
RETURNS text AS $repl_col2xml_base64$
BEGIN
   RETURN '<col name=\'' || name || '\' encoding=\'base64\'>' ||  
           encode(content,'base64') || '</col>';
END;
$repl_col2xml_base64$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- repl_col2xml_cdata converts a column into a simple xml notation and wraps    
-- the content into a _cdata object.                                            
--   name: the name of the column                                               
--   content the content of the column. If it is a blob use repl_col2xml_base64 
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_col2xml_cdata(name text, content text) 
RETURNS text AS $repl_col2xml_cdata$
BEGIN
   RETURN '<col name=\'' || name || '\'><![CDATA[' || 
           content || ']]></col>';
END;
$repl_col2xml_cdata$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- repl_check_structure is used to check wether a table has been created,       
-- dropped or altered.                                                          
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_check_structure()
   RETURNS TEXT AS $repl_check_structure$
   DECLARE colVar  RECORD;
           counter INT4;
BEGIN
    counter = 0;
    FOR colVar IN (SELECT relname FROM repl_current_tables WHERE relname NOT 
               IN (SELECT relname FROM pg_statio_user_tables WHERE relname IN 
	       (SELECT tablename FROM repl_tables)))
    LOOP
      INSERT INTO repl_items (trans_stamp, dbId, tablename, guid, db_action, 
                             db_catalog, db_schema, content, oldContent, 
			     version) values (CURRENT_TIMESTAMP, 
			     current_database(), colVar.relname, colVar.oid, 
			     'DROP', NULL, current_schema(), NULL, NULL, 
			     '0.0');
      counter = 1;
    END LOOP;					 
    FOR colVar IN (SELECT relname FROM pg_statio_user_tables WHERE relname 
	       IN (SELECT tablename FROM repl_tables) AND (relname NOT 
	       IN (SELECT relname FROM repl_current_tables)))
      LOOP
      INSERT INTO repl_items (trans_stamp, dbId, tablename, guid, db_action, 
                             db_catalog, db_schema, content, oldContent, 
			     version) values (CURRENT_TIMESTAMP, 
			     current_database(), colVar.relname, colVar.oid, 
			     'CREATE', NULL, current_schema(), NULL, NULL, 
			     '0.0');
      counter = 1;
    END LOOP;
    IF counter > 0 THEN
       TRUNCATE repl_current_tables;
       FOR colVar IN (SELECT relname FROM pg_statio_user_tables WHERE relname 
                  IN (SELECT tablename FROM repl_tables))
       LOOP 
         INSERT INTO repl_current_tables (relname) VALUES (colVar.relname);
       END LOOP;
    --    the following would not work (probably because table is cached)         
    --    CREATE TABLE repl_current_tables AS SELECT relname FROM                 
    --    pg_statio_user_tables;                                                  
    END IF;
    -- now CREATE and DROP have been handled ...                                  
    -- now we must handle ALTER TABLE (or column)                                 
    counter = 0;
    FOR colVar IN (SELECT relname FROM repl_current_tables) LOOP
       IF repl_is_altered(colVar.relname) = 't' THEN
         INSERT INTO repl_items (trans_stamp, dbId, tablename, guid, db_action, 
                                db_catalog, db_schema, content, oldContent, 
				version) VALUES (CURRENT_TIMESTAMP, 
				current_database(), colVar.relname, colVar.oid, 
				'ALTER', NULL, current_schema(), NULL, NULL, 
				'0.0');
	 counter = 1;
       END IF;
    END LOOP;
    IF counter > 0 THEN
      TRUNCATE repl_cols_table;
      FOR colVar IN (SELECT * FROM repl_cols_view) LOOP
         INSERT INTO repl_cols_table VALUES (colVar.attname, colVar.owner, 
	                                   colVar.atttypid, colVar.attlen, 
					   colVar.attnotnull, colVar.attnum);
      END LOOP;
    END IF;
    RETURN 'OK';
END;
$repl_check_structure$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- repl_tables_func is invoked by the trigger on repl_tables.                   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION repl_tables_func() 
   RETURNS trigger AS $repl_tables_func$
   DECLARE
      ret TEXT;
BEGIN
   ret = repl_check_structure();
   RETURN NULL;
END;
$repl_tables_func$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- repl_tables_trigger is invoked when a change occurs on repl_tables.          
-- ---------------------------------------------------------------------------- 

CREATE TRIGGER repl_tables_trigger AFTER UPDATE OR DELETE OR INSERT
ON repl_tables
FOR EACH ROW
EXECUTE PROCEDURE repl_tables_func();


