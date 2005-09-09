-- ---------------------------------------------------------------------------- 
-- Written by Michele Laghi (michele.laghi@avitech-ag.com) 2005-08-09           
-- THIS FILE IS THE INPUT FILE TO CONFIGURE THE NDB (TEST) FOR POSTGRES         
-- to invoke use:                                                               
-- psql -f replicaPostgres.sql test                                             
--                                                                              
--                                                                              
-- Some Comments:                                                               
--  The effect of triggers has been checked. An open issue is how to determine  
--  wether an action has been caused by a direct operation of the user (primary 
--  action) or if it is a reaction to that as an operation performed by a       
--  trigger (reaction).                                                         
--                                                                              
--  The use of the internal oid is not possible since it will never be the same 
--  between different database instances. For this reason it is simpler if an   
--  oid is provided on each table. If this is not possible due to business      
--  restrictions (for example if an existing table can not be modified), then   
--  it is possible to use the OLD information together with the schema          
--  information and the dbWriter will be able to recreate the information about 
--  the primary key.                                                            
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

-- note that what written here is only needed once (this is why we have         
-- commented out it)                                                            
-- ---------------------------------------------------------------------------- 

-- this is not working since same value is read on simultaneous transactions.   
-- CREATE TABLE replCounter(uniqueId integer, PRIMARY KEY (uniqueId));          
-- INSERT INTO replCounter VALUES (0);                                          

-- ---------------------------------------------------------------------------- 
-- WE FIRST CREATE THE TABLE HOLDING A LIST OF ALL TABLES TO BE REPLICATED      
-- ---------------------------------------------------------------------------- 

CREATE TABLE replTables (tableName VARCHAR(30) PRIMARY KEY);


-- ---------------------------------------------------------------------------- 
-- create the currentTables as a placeholder for the current tables (this is    
-- used to detect a CREATE TABLE and a DROP TABLE                               
-- ---------------------------------------------------------------------------- 

CREATE TABLE currentTables AS SELECT relname FROM pg_statio_user_tables WHERE 
       relname IN (SELECT tableName FROM replTables);


-- ---------------------------------------------------------------------------- 
-- create the test tables to be replicated (here two equal tables are created)  
-- ---------------------------------------------------------------------------- 

CREATE TABLE repltest (uniqueId integer, name text, surname text, email text, 
                       photo bytea, PRIMARY KEY (name, uniqueId));
CREATE TABLE repltest2 (uniqueId integer, name text, surname text, email text, 
                       photo bytea, PRIMARY KEY (name, uniqueId));

-- ---------------------------------------------------------------------------- 
-- and now populate the tables which have been created ...                      
-- ---------------------------------------------------------------------------- 

INSERT INTO repltest (oid, uniqueId, name, surname) VALUES (30000, 100, 
           'laghi', 'michele');
INSERT INTO repltest VALUES (1, 'laghi', 'michele', 'laghi@swissinfo.org', 
           'emty image here');
INSERT INTO repltest VALUES (2, 'laghi', 'michele', 'laghi@avitech-ag.com',
           'emty image here');
INSERT INTO repltest VALUES (2, 'heirich', 'goetzger', 
           'Heinrich.Goetzger@exploding-systems.de','emty image here');
INSERT INTO repltest VALUES (3, 'heirich', 'goetzger', 
           'Heinrich.Goetzger@avtech-ag.com','emty image here');
INSERT INTO repltest VALUES (0, 'else', 'somebody', 
           'somebody.else@somewhere.com','emty image here');
INSERT INTO repltest2 VALUES (1, 'laghi', 'michele', 'laghi@swissinfo.org', 
           'emty image here');
INSERT INTO repltest2 VALUES (2, 'laghi', 'michele', 'laghi@avitech-ag.com',
           'emty image here');
INSERT INTO repltest2 VALUES (2, 'heirich', 'goetzger', 
           'Heinrich.Goetzger@exploding-systems.de','emty image here');
INSERT INTO repltest2 VALUES (3, 'heirich', 'goetzger',
           'Heinrich.Goetzger@avtech-ag.com','emty image here');
INSERT INTO repltest2 VALUES (0, 'else', 'somebody', 
           'somebody.else@somewhere.com','emty image here');


-- ---------------------------------------------------------------------------- 
-- now we create a function and a trigger on the same table to test the         
-- effect of triggers (if it is possible to detect in the replitems table if    
-- an action was triggered directly by the user or if it was a result of a      
-- trigger.                                                                     
-- ---------------------------------------------------------------------------- 

-- DROP SEQUENCE testSeq;
-- CREATE SEQUENCE testSeq;
-- 
-- DROP FUNCTION triggerTest;
-- CREATE OR REPLACE FUNCTION triggerTest() RETURNS TRIGGER AS $triggerTest$
-- BEGIN
--    INSERT INTO repltest VALUES (nextval('testSeq'), 'trigger', 'one', 
--            'trigger@one.com','emty image here');
--    RETURN OLD;
-- END;
-- $triggerTest$ LANGUAGE 'plpgsql';
-- 
-- DROP TRIGGER triggerTest1 ON replTest CASCADE;
-- CREATE TRIGGER triggerTest1
-- AFTER DELETE
-- ON repltest
-- FOR EACH ROW
-- EXECUTE PROCEDURE triggerTest();
-- 
-- DROP TRIGGER triggerTest2 ON replTest2 CASCADE;
-- CREATE TRIGGER triggerTest2
-- AFTER DELETE
-- ON repltest2
-- FOR EACH ROW
-- EXECUTE PROCEDURE triggerTest();

DROP RULE tableChanges ON replitems CASCADE;


-- ---------------------------------------------------------------------------- 
-- A Difference between these two means that an ALTER operation has been        
-- invoked.                                                                     
-- ---------------------------------------------------------------------------- 

DROP VIEW replColsView;

CREATE VIEW replColsView AS SELECT attname,(SELECT relname FROM pg_class 
       WHERE oid=attrelid) AS owner,atttypid,attlen,attnotnull,attnum FROM 
       pg_attribute WHERE attnum > 0 AND (SELECT relname FROM pg_class WHERE 
       oid=attrelid) IN (SELECT tablename FROM repltables) 
       ORDER BY owner, attnum;

DROP TABLE replColsTable;

CREATE TABLE replColsTable AS SELECT attname,(SELECT relname FROM pg_class 
       WHERE oid=attrelid) AS owner,atttypid,attlen,attnotnull,attnum FROM 
       pg_attribute WHERE attnum > 0 AND (SELECT relname FROM pg_class WHERE 
       oid=attrelid) IN (SELECT tablename FROM repltables) 
       ORDER BY owner, attnum;

-- ---------------------------------------------------------------------------- 
-- NOW WE START WITH THE STANDARD PART (will be in one single transaction)      
-- ---------------------------------------------------------------------------- 

START TRANSACTION;


CREATE OR REPLACE FUNCTION isAltered(name text) RETURNS BOOLEAN AS $isAltered$
DECLARE
   colVar RECORD;
   md5A TEXT;
   md5B TEXT;
   tmp  TEXT;
BEGIN
   FOR colVar IN (SELECT * FROM replColsTable WHERE owner=$1) LOOP
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
   FOR colVar IN (SELECT * FROM replColsView WHERE owner=$1) LOOP
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
$isAltered$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- We create the table which will be used for the outgoing replica messages and 
-- a sequence needed for a monotone increasing sequence for the primary key.    
-- In postgres this will implicitly create an index "repltest_pkey" for this    
-- table. The necessary generic lowlevel functions are created.                 
-- ---------------------------------------------------------------------------- 

DROP SEQUENCE replSeq;
-- DROP RULE tableChanges ON replitems CASCADE;
CREATE SEQUENCE replSeq MINVALUE 1 MAXVALUE 1000000 CYCLE;

DROP TABLE replitems CASCADE;
CREATE TABLE replitems (replKey INTEGER DEFAULT nextval('replSeq'), 
             transaction TIMESTAMP, dbId VARCHAR(15), tableName VARCHAR(15), 
	     guid VARCHAR(15), action VARCHAR(10), schema VARCHAR(40), 
	     content TEXT, oldContent TEXT, version VARCHAR(6), 
	     PRIMARY KEY (replKey));

-- ---------------------------------------------------------------------------- 
-- colToXml converts a column into a simple xml notation. The output of these   
-- functions follows the notation of the dbWatcher. More about that on          
-- http://www.xmlblaster.org/xmlBlaster/doc/requirements/contrib.dbwatcher.html 
--    name: the name of the column                                              
--    content the content of the column. If it is a blob use colToXmlBASE64     
-- note that the name of the column is always stored as lowercase (otherwise    
-- it is not found on the replica (if the replicas schema is taken from the     
-- replica meta data).                                                          
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION colToXml(name text, content text) 
RETURNS text AS $colToXml$
BEGIN
   RETURN '<col name=\'' || lower(name) || '\'>' || content || '</col>';
END;
$colToXml$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- colToXmlBASE64 converts a column into a simple xml notation where the        
-- content will be decoded to base64.                                           
--    name: the name of the column                                              
--    content the content of the column (must be bytea or compatible)           
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION colToXmlBASE64(name text, content bytea) 
RETURNS text AS $colToXmlBASE64$
BEGIN
   RETURN '<col name=\'' || lower(name) || '\' encoding=\'base64\'>' ||  
           encode(content,'base64') || '</col>';
END;
$colToXmlBASE64$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- colToXmlCDATA converts a column into a simple xml notation and wraps the     
-- content into a CDATA object.                                                 
--    name: the name of the column                                              
--    content the content of the column. If it is a blob use colToXmlBASE64     
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION colToXmlCDATA(name text, content text) 
RETURNS text AS $colToXmlCDATA$
BEGIN
   RETURN '<col name=\'' || lower(name) || '\'><![CDATA[' || 
           content || ']]></col>';
END;
$colToXmlCDATA$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- checkCreateDropAlter is used to check wether a table has been created,       
-- dropped or altered.                                                          
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION checkCreateDropAlter() 
   RETURNS text AS $checkCreateDropAlter$
   DECLARE colVar  RECORD;
           counter INT4;
BEGIN
    counter = 0;
    FOR colVar IN (SELECT relname FROM currentTables WHERE relname NOT IN (
               SELECT relname FROM pg_statio_user_tables WHERE relname IN 
	       (SELECT tableName FROM replTables)))
    LOOP
      INSERT INTO replitems (transaction, dbId, tableName, guid, action, 
                             schema, content, oldContent, version) values 
                             (CURRENT_TIMESTAMP, current_database(),
                             colVar.relname, colVar.oid, 'DROP', 
			     current_schema(), NULL, NULL, '0.0');
      counter = 1;
    END LOOP;					 
    FOR colVar IN (SELECT relname FROM pg_statio_user_tables WHERE relname 
	       IN (SELECT tableName FROM replTables) AND (relname NOT 
	       IN (SELECT relname FROM currentTables)))
      LOOP
      INSERT INTO replitems (transaction, dbId, tableName, guid, action, 
                             schema, content, oldContent, version) values 
                             (CURRENT_TIMESTAMP,current_database(),
                             colVar.relname, colVar.oid, 'CREATE', 
			     current_schema(), NULL, NULL, '0.0');
      counter = 1;
    END LOOP;
    IF counter > 0 THEN
       TRUNCATE currentTables;
       FOR colVar IN (SELECT relname FROM pg_statio_user_tables WHERE relname 
                  IN (SELECT tableName FROM replTables))
       LOOP 
         INSERT INTO currentTables (relname) VALUES (colVar.relname);
       END LOOP;
    --    the following would not work (probably because table is cached)         
    --    CREATE TABLE currentTables AS SELECT relname FROM pg_statio_user_tables;
    END IF;
    -- now CREATE and DROP have been handled ...                                  
    -- now we must handle ALTER TABLE (or column)                                 
    counter = 0;
    FOR colVar IN (SELECT relname FROM currentTables) LOOP
       IF isAltered(colVar.relname) = 't' THEN
         INSERT INTO replitems (transaction, dbId, tableName, guid, action, 
                                schema, content, oldContent, version) values 
                                (CURRENT_TIMESTAMP,current_database(),
                                colVar.relname, colVar.oid, 'ALTER', 
				current_schema(), NULL, NULL, '0.0');
	 counter = 1;
       END IF;
    END LOOP;
    IF counter > 0 THEN
      TRUNCATE replColsTable;
      FOR colVar IN (SELECT * FROM replColsView) LOOP
         INSERT INTO replColsTable VALUES (colVar.attname, colVar.owner, 
	                                   colVar.atttypid, colVar.attlen, 
					   colVar.attnotnull, colVar.attnum);
      END LOOP;
    END IF;
    RETURN 'OK';
END;
$checkCreateDropAlter$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
--                                                                              
-- BELOW THE BUSINESS SPECIFIC OPERATIONS AND FUNCTIONS ARE DEFINED             
--                                                                              
-- ---------------------------------------------------------------------------- 

-- ---------------------------------------------------------------------------- 
-- replTestGroup is the function which will be registered to the triggers.      
-- It must not take any parameter.                                              
-- This is the only method which is business data specific. It is depending on  
-- the table to be replicated. This should be generated by a tool.              
--                                                                              
-- For each table you should just write out in a sequence the complete content  
-- of the row to replicate. You could make more fancy stuff here, for example   
-- you could just send the minimal stuff, i.e. only the stuff which has changed 
-- (for the new stuff) and for the old one you could always send an empty one.  
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION replTestGroup() RETURNS trigger AS $replTestGroup$
DECLARE blobCont BYTEA; 
        oldCont TEXT; 
	newCont TEXT;
	comment TEXT;
	oid     TEXT;
	tmp     TEXT;
BEGIN
    oldCont = NULL;
    newCont = NULL;
    tmp = checkCreateDropAlter();

    IF (TG_OP != 'INSERT') THEN
-- this is needed since conversion from text to bytea must be done ...          
       blobCont = old.email; 
       oldCont = colToXml('uniqueId',old.uniqueId) || 
                 colToXml('name', old.name) || 
		 colToXmlCDATA('surname',old.surname) || 
		 colToXmlBASE64('email', blobCont) || 
		 colToXmlBASE64('photo', old.photo);
       oid = old.oid;
    END IF;

    IF (TG_OP != 'DELETE') THEN
       blobCont = new.email;
       newCont = colToXml('uniqueId',new.uniqueId) || 
                 colToXml('name', new.name) || 
		 colToXmlCDATA('surname',new.surname) || 
		 colToXmlBASE64('email', blobCont) || 
		 colToXmlBASE64('photo', new.photo);
       oid = new.oid;

    END IF;
    INSERT INTO replitems (transaction, dbId, tableName, guid, action, schema, 
                           content, oldContent, version) values 
                           (CURRENT_TIMESTAMP,current_database(),
			   TG_RELNAME, oid, TG_OP, current_schema(), newCont, 
			   oldCont, '0.0');
    tmp = inet_client_addr();
    comment = 'current user \'' || current_user || '\' session_user \'' ||
              session_user || '\' current schema \'' || current_schema() || '\'';
    RAISE NOTICE 'NEW REPL. ITEM %', comment;

    IF (TG_OP = 'DELETE') THEN RETURN OLD;
    END IF;
    RETURN NEW;
END;
$replTestGroup$ LANGUAGE 'plpgsql';

-- ---------------------------------------------------------------------------- 
-- replSystem is the function needed to detect CREATE TABLE and DROP TABLE      
-- operations. It is database specific but is needs to be created only once.    
-- It must not take any parameter.                                              
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION replSystem() RETURNS trigger AS $replSystem$
BEGIN
    IF (current_schema() != 'public') THEN
       RETURN NULL;
    END IF;

    IF (TG_OP != 'INSERT') THEN
       INSERT INTO replitems (transaction, dbId, tableName, guid, action, schema, 
                              content, oldContent, version) values 
                              (CURRENT_TIMESTAMP,current_database(),
     			      new.tablename, new.oid, 'CREATE', NULL, NULL, 
			      NULL, '0.0');
       RAISE NOTICE 'NEW REPL. ITEM: TABLE CREATED  %', new.tablename;
       RETURN NEW;
    END IF;

    IF (TG_OP != 'DELETE') THEN
       INSERT INTO replitems (transaction, dbId, tableName, guid, action, schema, 
                              content, oldContent, version) values 
                              (CURRENT_TIMESTAMP,current_database(),
     			      old.tablename, old.oid, 'DROP', NULL, NULL, 
			      NULL, '0.0');
       RAISE NOTICE 'NEW REPL. ITEM: TABLE DROPPED  %', old.tablename;
    
       RETURN OLD;
    END IF;


    IF (TG_OP = 'DELETE') THEN RETURN OLD;
    END IF;
    RETURN NEW;
END;
$replSystem$ LANGUAGE 'plpgsql';


-- ---------------------------------------------------------------------------- 
-- We need to create triggers. We make sure old triggers will be deleted before 
-- continuing.                                                                  
-- Note that the function invoked by a trigger can not take arguments (more on  
-- it on http://www.postgresql.org/docs/8.0/interactive/plpgsql-trigger.html).  
-- ---------------------------------------------------------------------------- 


-- ---------------------------------------------------------------------------- 
-- THE TRIGGER FOR THE replSystem FUNCTION                                      
-- ---------------------------------------------------------------------------- 


-- DROP TRIGGER triggerReplSystem ON pg_statio_user_tables CASCADE;
--CREATE TRIGGER triggerReplSystem
--AFTER DELETE OR INSERT
--ON pg_tables
--FOR EACH ROW
--EXECUTE PROCEDURE replSystem();			       



-- IMPORTANT: Rules do not have effect on table creation (no effect on pg_tables
-- nor on pg_statio_user_tables                                                 
CREATE OR REPLACE RULE tableChanges AS ON INSERT TO pg_attribute DO ALSO
      INSERT INTO replitems (transaction, dbId, tableName, guid, action, schema, 
                             content, oldContent, version) values 
                             (CURRENT_TIMESTAMP,current_database(),
    			      new.attname, NULL, 'CREATE', NULL, NULL, 
     		      NULL, '0.0');


-- ---------------------------------------------------------------------------- 
-- THE TRIGGER FOR THE replTest TABLE                                           
-- ---------------------------------------------------------------------------- 


DROP TRIGGER triggerReplTest ON replTest CASCADE;
CREATE TRIGGER triggerReplTest
AFTER UPDATE OR DELETE OR INSERT
ON repltest
FOR EACH ROW
EXECUTE PROCEDURE replTestGroup();

-- ---------------------------------------------------------------------------- 
-- THE TRIGGER FOR THE replTest2 TABLE                                          
-- ---------------------------------------------------------------------------- 

DROP TRIGGER triggerReplTest2 ON repltest2 CASCADE;
CREATE TRIGGER triggerReplTest2
AFTER UPDATE OR DELETE OR INSERT
ON repltest2
FOR EACH ROW
EXECUTE PROCEDURE replTestGroup();

-- ---------------------------------------------------------------------------- 
--  R E A D Y    T O   C L O S E   T R A N S A C T I O N                        
-- ---------------------------------------------------------------------------- 

COMMIT;


-- update currentTables after it has been detected that something changed
-- DROP TABLE currentTables;
-- CREATE TABLE currentTables AS SELECT relname FROM pg_statio_user_tables;

-- list of all tables which have been dropped since last update:
-- select relname from currentTables where relname NOT IN (select relname from pg_statio_user_tables);

-- list of all tables which have been created since last update (note the lowercases):
-- select relname from pg_statio_user_tables where relname NOT IN (select relname from currentTables) AND relname != 'currenttables';




-- FOR TESTING INVOKE:                                                          
-- update repltest SET email='test@one.com' where name='laghi' AND uniqueId=1;  

-- ---------------------------------------------------------------------------- 
--                     E N D                                                    
-- ---------------------------------------------------------------------------- 


