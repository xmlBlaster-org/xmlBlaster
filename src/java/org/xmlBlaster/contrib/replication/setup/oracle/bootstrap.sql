-- Written by Michele Laghi (laghi@swissinfo.org) 2005-08-09                    
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
-- ---------------------------------------------------------------------------- 


DROP PROCEDURE ${replPrefix}debug
-- FLUSH (dropped ${replPrefix}debug)                                           


-- ---------------------------------------------------------------------------- 
-- WE FIRST CREATE THE TABLE HOLDING A LIST OF ALL TABLES TO BE REPLICATED      
-- ---------------------------------------------------------------------------- 


-- ---------------------------------------------------------------------------- 
-- This is only used to be filled for debugging purposes                        
-- ---------------------------------------------------------------------------- 
CREATE TABLE ${replPrefix}debug_table(replKey INTEGER, line VARCHAR(255))
-- EOC (end of command: needed as a separator for our script parser)            

CREATE OR REPLACE PROCEDURE ${replPrefix}debug(lineTxt VARCHAR2) AS
   replKey INTEGER;
BEGIN
   replKey := 1;							    
   -- SELECT ${replPrefix}seq.nextval INTO replKey FROM DUAL;
   -- INSERT INTO ${replPrefix}debug_table VALUES (replKey, lineTxt);
END;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- This is only for old LONG datas                                              
-- ---------------------------------------------------------------------------- 

CREATE TABLE ${replPrefix}longs_table(repl_key INTEGER, 
                              content CLOB, PRIMARY KEY (repl_key)) 
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- This table contains the list of tables to watch.                             
-- tablename is the name of the table to watch                                  
-- actions is the flag being a combination of I (indicating it acts on inserts),
-- D (for deletes) and U (for updates).                                         
-- it will only watch for initial replication.                                  
-- ---------------------------------------------------------------------------- 

CREATE TABLE ${replPrefix}tables(catalogname VARCHAR(${charWidth}), 
                         schemaname VARCHAR(${charWidth}),
                         tablename VARCHAR(${charWidth}), actions CHAR(3),
			 status VARCHAR(${charWidthSmall}), repl_key INTEGER, 
			 trigger_name VARCHAR(${charWidth}), debug INTEGER, 
			 replkeycol VARCHAR(${charWidth}),
			 PRIMARY KEY(catalogname, schemaname, tablename))
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- create the ${replPrefix}current_tables as a placeholder for the current      
-- tables (this is used to detect a CREATE TABLE and a DROP TABLE.              
-- ---------------------------------------------------------------------------- 

CREATE TABLE ${replPrefix}current_tables AS SELECT table_name AS relname 
       FROM all_tables WHERE table_name IN (SELECT tablename 
       FROM ${replPrefix}tables)
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- We create the table which will be used for the outgoing replica messages and 
-- a sequence needed for a monotone increasing sequence for the primary key.    
-- In postgres this will implicitly create an index "repltest_pkey" for this    
-- table. The necessary generic lowlevel functions are created.                 
-- ---------------------------------------------------------------------------- 

CREATE SEQUENCE ${replPrefix}seq MINVALUE 1 MAXVALUE 1000000000 CYCLE
-- EOC (end of command: needed as a separator for our script parser)            

CREATE TABLE ${replPrefix}items (repl_key INTEGER, 
             trans_key VARCHAR(${charWidth}), dbId VARCHAR(${charWidth}), 
	     tablename VARCHAR(${charWidth}), guid VARCHAR(${charWidth}), 
	     db_action VARCHAR(${charWidth}), db_catalog VARCHAR(${charWidth}),
	     db_schema VARCHAR(${charWidth}), content CLOB, oldContent CLOB, 
	     version VARCHAR(${charWidthSmall}), PRIMARY KEY (repl_key))
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- This trigger is needed if you want to detect change events synchronously as  
-- they occur without the need of polling. It is only used in conjunction with  
-- the OracleByEventScheduler.                                                  
-- ---------------------------------------------------------------------------- 
CREATE TRIGGER ${replPrefix}scheduler_trigger AFTER INSERT
ON ${replPrefix}ITEMS
FOR EACH ROW
BEGIN
   dbms_alert.signal('${replPrefix}ITEMS', 'INSERT');
END ${replPrefix}scheduler_trigger;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}fill_blob_char                                                  
-- must be invoked as:                                                          
-- repl_fill_blob_char(newCont, :new.SRWY_RWY_ID, 'SRWY_RWY_ID');               
-- ---------------------------------------------------------------------------- 


CREATE OR REPLACE FUNCTION ${replPrefix}fill_blob_char(
                           val VARCHAR, nameOfParam VARCHAR) RETURN CLOB AS
   tmpCont CLOB;
BEGIN
   tmpCont := EMPTY_CLOB;
   dbms_lob.createtemporary(tmpCont, TRUE);
   dbms_lob.open(tmpCont, dbms_lob.lob_readwrite);
   dbms_lob.writeappend(tmpCont, LENGTH(val), val);
   dbms_lob.close(tmpCont);
   -- dbms_lob.append(completeCont, ${replPrefix}col2xml(nameOfParam, tmpCont));
   RETURN ${replPrefix}col2xml(nameOfParam, tmpCont);
END ${replPrefix}fill_blob_char;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}col2xml_cdata converts a column into a simple xml notation and  
-- wraps the content into a _cdata object.                                      
--   name: the name of the column                                               
--   content the content of the column. If it is a blob use                     
-- ${replPrefix}col2xml_base64                                                  
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}col2xml_cdata(name VARCHAR, 
                           content CLOB) RETURN CLOB AS
  tmp CLOB;
  ch  VARCHAR(${charWidth});
BEGIN
   tmp := EMPTY_CLOB;
   dbms_lob.createtemporary(tmp, TRUE);
   dbms_lob.open(tmp, dbms_lob.lob_readwrite);
   ch := '<col name="';
   dbms_lob.writeappend(tmp, length(ch), ch);
   dbms_lob.writeappend(tmp, length(name), name);
   ch := '"><![CDATA[';
   dbms_lob.writeappend(tmp, length(ch), ch);
   dbms_lob.append(tmp, content);
   ch := ']]></col>';
   dbms_lob.writeappend(tmp, length(ch), ch);
   dbms_lob.close(tmp);
   RETURN  tmp;
END ${replPrefix}col2xml_cdata;
-- EOC (end of command: needed as a separator for our script parser)            



-- ---------------------------------------------------------------------------- 
-- ${replPrefix}needs_prot (prot stands for protection) detects wether a        
-- protection to BASE64 is needed or not in a text string.                      
-- returns an integer. If 1 it means CDATA protection will suffice, if 2 it     
-- means it needs BASE64.                                                       
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}needs_prot(content CLOB) 
RETURN INTEGER AS
   pos       INTEGER;
   ret       INTEGER;
   len       INTEGER;
   offset    INTEGER;
   tmp       VARCHAR(32766);
   increment INTEGER;
BEGIN
   ret := 0;
   offset := 1;
   increment := 32766;
   len := dbms_lob.getlength(content);

   WHILE offset < len LOOP
      dbms_lob.read(content, increment, offset, tmp);
      offset := offset + increment; 
      if len > increment THEN 
         offset := offset - 3; -- overlap to be sure not to cut a token
      END IF;
      pos := INSTR(tmp, ']]>', 1, 1);
      IF POS > 0 THEN
         IF ret < 2 THEN
	    ret := 2;
	 END IF;
      END IF;
      pos := INSTR(tmp, '<', 1, 1);
      IF POS > 0 THEN 
	 IF ret < 1 THEN
	    ret := 1;
	 END IF;
      END IF;
      pos := INSTR(tmp, '&', 1, 1);
      IF POS > 0 THEN 
         IF ret < 1 THEN 
	    ret := 1;
         END IF;
      END IF;
   END LOOP;
   RETURN ret;
END ${replPrefix}needs_prot;
-- EOC (end of command: needed as a separator for our script parser)            


		 
-- ---------------------------------------------------------------------------- 
-- ${replPrefix}base64_helper is only needed in ORACLE previous to version 9    
-- and is used by ${replPrefix}base64_enc_raw and ${replPrefix}base64_enc_char. 
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}base64_helper(zeros SMALLINT, 
                  val INTEGER) RETURN VARCHAR AS
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
END ${replPrefix}base64_helper;
-- EOC (end of command: needed as a separator for our script parser)            



-- ---------------------------------------------------------------------------- 
-- ${replPrefix}base64_enc_raw converts a RAW msg to a base64 encoded string.   
-- This is needed for ORACLE versions previous to Oracle9.                      
-- content will be encoded to base64.                                           
--   name: the name of content to be encoded.                                   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}base64_enc_raw(msg RAW)
   RETURN CLOB AS
     numBuilder INTEGER;
     char1      SMALLINT;
     char2      SMALLINT;
     char3      SMALLINT;
     char4      SMALLINT;
     res        CLOB;
     i          INTEGER;
     len        INTEGER;
-- divisible by 3 (to avoid '=' characters at end of chunks)                    
     source     RAW(32766); 
     zeros      SMALLINT;
     ch         CHAR(10);
     tmp        VARCHAR(${charWidthSmall});
BEGIN
   source := msg;
   res := EMPTY_CLOB;
   dbms_lob.createtemporary(res, TRUE);
   dbms_lob.open(res, dbms_lob.lob_readwrite);

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
      tmp := ${replPrefix}base64_helper(zeros, numBuilder);
      len := LENGTH(tmp);
      dbms_lob.writeappend(res, len, tmp);
   END LOOP;
   dbms_lob.close(res);
   RETURN res;
END ${replPrefix}base64_enc_raw;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}test_blob is only needed for testing since there are problems   
-- in passing LOB objects to the arguments of a method in ORACLE 8.1.6 in a     
-- portable fashion.                                                            
-- since it seems there is no portable way in JDBC to create LOB objects to be  
-- passed to functions. A Blob object is valid for the duration of the          
-- transaction in which is was created.                                         
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}test_blob(method VARCHAR2, 
                           msg RAW, other VARCHAR2, nmax INTEGER) 
   RETURN CLOB AS
   i   INTEGER;
   len INTEGER;
   tmp BLOB;
   res CLOB;
BEGIN
   ${replPrefix}debug('TEST BLOB INVOKED');

   tmp := EMPTY_BLOB;
   len := utl_raw.length(msg);
   dbms_lob.createtemporary(tmp, TRUE);
   dbms_lob.open(tmp, dbms_lob.lob_readwrite);
   FOR i IN  1 .. nmax LOOP
      dbms_lob.writeappend(tmp, len, msg);
   END LOOP;
   dbms_lob.close(tmp);
   IF method = 'BASE64_ENC_BLOB' THEN
      RETURN ${replPrefix}base64_enc_blob(tmp);
   END IF;
   IF method = 'COL2XML_BASE64' THEN
      RETURN ${replPrefix}col2xml_base64(other, tmp);
   END IF;
   
   -- on other just return 'TEST' as a blob
   res := EMPTY_CLOB;
   dbms_lob.createtemporary(res, TRUE);
   dbms_lob.open(res, dbms_lob.lob_readwrite);
   len := LENGTH('TEST');
   dbms_lob.writeappend(res, len, 'TEST');
   dbms_lob.close(res);
   RETURN res;
END ${replPrefix}test_blob;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}test_clob is only needed for testing since there are problems   
-- in passing LOB objects to the arguments of a method in ORACLE 8.1.6 in a     
-- portable fashion.                                                            
-- since it seems there is no portable way in JDBC to create LOB objects to be  
-- passed to functions. A Blob object is valid for the duration of the          
-- transaction in which is was created.                                         
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}test_clob(method VARCHAR2, 
                           msg VARCHAR2, other VARCHAR2, nmax INTEGER) 
   RETURN CLOB AS
   i   INTEGER;
   len INTEGER;
   tmp CLOB;
   res CLOB;
   needsProt INTEGER;
   answer VARCHAR(${charWidth});
BEGIN
   ${replPrefix}debug('TEST CLOB INVOKED');
   tmp := EMPTY_CLOB;
   len := LENGTH(msg);
   answer := 'TEST';
   dbms_lob.createtemporary(tmp, TRUE);
   dbms_lob.open(tmp, dbms_lob.lob_readwrite);
   FOR i IN  1 .. nmax LOOP
      dbms_lob.writeappend(tmp, len, msg);
   END LOOP;
   dbms_lob.close(tmp);
   IF method = 'BASE64_ENC_CLOB' THEN
      RETURN ${replPrefix}base64_enc_clob(tmp);
   END IF;
   IF method = 'COL2XML_CDATA' THEN
      RETURN ${replPrefix}col2xml_cdata(other, tmp);
   END IF;
   IF method = 'COL2XML' THEN
      RETURN ${replPrefix}col2xml(other, tmp);
   END IF;
   IF method = 'NEEDS_PROT' THEN
      needsProt := ${replPrefix}needs_prot(tmp);
      answer := TO_CHAR(needsProt); -- overwrites 'TEST'
   END IF;
   -- on other just return 'TEST' as a blob
   res := EMPTY_CLOB;
   dbms_lob.createtemporary(res, TRUE);
   dbms_lob.open(res, dbms_lob.lob_readwrite);
   len := LENGTH(answer);
   dbms_lob.writeappend(res, len, answer);
   dbms_lob.close(res);
   RETURN res;
END ${replPrefix}test_clob;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}base64_enc_varchar2 converts a VARCHAR2 msg to a base64 encoded 
-- string. This is needed for ORACLE versions previous to Oracle9.              
-- content will be encoded to base64.                                           
--   name: the name of content to be encoded.                                   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}base64_enc_varchar2(msg VARCHAR2)
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
     tmp   VARCHAR(${charWidthSmall});
BEGIN
   source := msg;
   res := EMPTY_CLOB;
   dbms_lob.createtemporary(res, TRUE);
   dbms_lob.open(res, dbms_lob.lob_readwrite);
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
      tmp := ${replPrefix}base64_helper(zeros, numBuilder);
      dbms_lob.writeappend(res, length(tmp), tmp);
   END LOOP;
   dbms_lob.close(res);
   RETURN res;
END ${replPrefix}base64_enc_varchar2;
-- EOC (end of command: needed as a separator for our script parser)            



-- ---------------------------------------------------------------------------- 
-- ${replPrefix}base64_enc_blob converts a BLOB msg to a base64 encoded string. 
-- This is needed for ORACLE versions previous to Oracle9.                      
-- content will be encoded to base64.                                           
--   name: the name of content to be encoded.                                   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}base64_enc_blob(msg BLOB)
   RETURN CLOB AS
     len       INTEGER;
     offset    INTEGER;
     tmp       RAW(32766);
     res       CLOB;
     increment INTEGER;
BEGIN
   res := EMPTY_CLOB;
   dbms_lob.createtemporary(res, TRUE);
   dbms_lob.open(res, dbms_lob.lob_readwrite);
   offset := 1;
   increment := 32766;
   len := dbms_lob.getlength(msg);

   WHILE offset < len LOOP
      dbms_lob.read(msg, increment, offset, tmp);
      offset := offset + increment;
      -- the next line would be used for oracle from version 9 up.              
      -- res := res || utl_raw.cast_to_varchar2(utl_encode.base64_encode(tmp)); 
      dbms_lob.append(res, ${replPrefix}base64_enc_raw(tmp));
   END LOOP;
   dbms_lob.close(res);
   RETURN res;
END ${replPrefix}base64_enc_blob;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}base64_enc_clob converts a CLOB msg to a base64 encoded string. 
-- This is needed for ORACLE versions previous to Oracle9.                      
-- content will be encoded to base64.                                           
--   name: the name of content to be encoded.                                   
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}base64_enc_clob(msg CLOB)
   RETURN CLOB AS
     len    INTEGER;
     offset INTEGER;
     tmp    VARCHAR2(32766);
     res    CLOB;
     increment INTEGER;
BEGIN
   res := EMPTY_CLOB;
   dbms_lob.createtemporary(res, TRUE);
   dbms_lob.open(res, dbms_lob.lob_readwrite);
   offset := 1;
   increment := 32766;
   len := dbms_lob.getlength(msg);
   WHILE offset < len LOOP
      dbms_lob.read(msg, increment, offset, tmp);
      offset := offset + increment;
      -- the next line would be used for oracle from version 9 up.              
      -- res := res || utl_raw.cast_to_varchar2(utl_encode.base64_encode(tmp)); 
      dbms_lob.append(res, ${replPrefix}base64_enc_varchar2(tmp));
   END LOOP;
   dbms_lob.close(res);
   RETURN res;
END ${replPrefix}base64_enc_clob;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}col2xml_base64 converts a column into a simple xml notation     
-- where the content will be decoded to base64.                                 
--   name: the name of the column                                               
--   content the content of the column (must be bytea or compatible)            
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}col2xml_base64(name VARCHAR, 
                           content BLOB) RETURN CLOB AS
   tmp CLOB;
   ch  VARCHAR(40);
BEGIN
   tmp := EMPTY_CLOB;
   dbms_lob.createtemporary(tmp, TRUE);
   dbms_lob.open(tmp, dbms_lob.lob_readwrite);
   ch := '<col name="';
   dbms_lob.writeappend(tmp, length(ch), ch);
   dbms_lob.writeappend(tmp, length(name), name);
   ch := '" encoding="base64">';
   dbms_lob.writeappend(tmp, length(ch), ch);
   dbms_lob.append(tmp, ${replPrefix}base64_enc_blob(content));
   ch := '</col>';
   dbms_lob.writeappend(tmp, length(ch), ch);
   dbms_lob.close(tmp);
   RETURN  tmp;
END ${replPrefix}col2xml_base64;
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

CREATE OR REPLACE FUNCTION ${replPrefix}col2xml(name VARCHAR, content CLOB)
RETURN CLOB AS
   pos INTEGER;
   tmp CLOB;
   ch  VARCHAR(40);
BEGIN
   tmp := EMPTY_CLOB;
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

CREATE OR REPLACE FUNCTION ${replPrefix}check_structure
   RETURN VARCHAR AS
BEGIN
   RETURN 'OK';
END ${replPrefix}check_structure;

-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}debug_trigger is used to check wether a trigger has to be       
-- debugged or not.                                                             
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}debug_trigger(schmName VARCHAR, 
   tblName VARCHAR) RETURN INTEGER AS 
   ret    INTEGER;
BEGIN
   SELECT debug INTO ret FROM ${replPrefix}tables WHERE tablename=tblName AND
          schemaname=schmName;
   RETURN ret;
END ${replPrefix}debug_trigger;

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
   contClob   CLOB;
BEGIN
   ${replPrefix}debug('CHECK_TABLES ' || schName || '.' || tblName);
--   SELECT count(*) INTO tmp FROM all_tables WHERE (table_name=tblName         
--                   OR table_name=UPPER(tblName) OR table_name=LOWER(tblName)) 
--		   AND (owner=schName OR owner=UPPER(schName) OR                
--		   owner=LOWER(schName));                                       

   SELECT count(*) INTO tmp FROM sys.all_tables WHERE table_name=tblName AND owner=schName;

   ${replPrefix}debug('CHECK_TABLES count=' || TO_CHAR(tmp));
   IF tmp = 0 THEN 
      res := 'FALSE';
   ELSE
      if cont = NULL THEN
         contClob := EMPTY_CLOB;
         dbms_lob.writeappend(contClob, length(cont), cont);
         dbms_lob.close(contClob);
      ELSE
	contClob := NULL;
      END IF;

      SELECT ${replPrefix}seq.nextval INTO replKey FROM DUAL;
      INSERT INTO ${replPrefix}items (repl_key, trans_key, dbId, tablename, 
                  guid, db_action, db_catalog, db_schema, content, oldContent,
                  version) values (replKey, 'UNKNOWN', dbName, tblName,
                  NULL, op, NULL, schName, cont, NULL, '${replVersion}');

      transId := DBMS_TRANSACTION.LOCAL_TRANSACTION_ID(FALSE);
      -- we compare in the db watcher if replKey = transKey and warn for it
      if transId = NULL THEN
         transId := CHR(replKey);
      END IF;
      UPDATE ${replPrefix}items SET trans_key=transId WHERE repl_key=replKey;
      res := 'TRUE';
   END IF;        
   RETURN res;
END ${replPrefix}check_tables;
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}tables_trigger is invoked when a change occurs on               
-- ${replPrefix}tables.                                                         
-- TODO: need to get DB_NAME and SCHEMA_NAME from system properties             
-- ---------------------------------------------------------------------------- 

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

      SELECT ${replPrefix}seq.nextval INTO replKey FROM DUAL;
      INSERT INTO ${replPrefix}items (repl_key, trans_key, dbId, tablename, 
                  guid, db_action, db_catalog, db_schema, content, oldContent,
                  version) values (replKey, 'UNKNOWN', dbName, tblName,
                  NULL, op, NULL, schName, NULL, NULL, '${replVersion}');

      transId := DBMS_TRANSACTION.LOCAL_TRANSACTION_ID(FALSE);
      -- we compare in the db watcher if replKey = transKey and warn for it
      if transId = NULL THEN
         transId := CHR(replKey);
      END IF;
      UPDATE ${replPrefix}items SET trans_key=transId WHERE repl_key=replKey;
      res := 'TRUE';
   END IF;
   RETURN res;
END ${replPrefix}add_table;
-- EOC (end of command: needed as a separator for our script parser)            

-- ---------------------------------------------------------------------------- 
-- ${replPrefix}prepare_broadcast is used to make the first entry in the items  
-- table before publishing a statement.                                         
-- returns TRUE if the table exists (has to be replicated) or FALSE otherwise.  
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE FUNCTION ${replPrefix}prepare_broadcast(txt VARCHAR)
   RETURN VARCHAR AS
   replKey  INTEGER;
   transId  VARCHAR(${charWidth});
   tmp      NUMBER;
   res      VARCHAR(${charWidthSmall});
   contClob CLOB;
BEGIN
   ${replPrefix}debug('PREPARE_BROADCAST ' || txt);
   contClob := EMPTY_CLOB;
   dbms_lob.createtemporary(contClob, TRUE);
   dbms_lob.open(contClob, dbms_lob.lob_readwrite);
   dbms_lob.writeappend(contClob, length(txt), txt);
   dbms_lob.close(contClob);

   SELECT ${replPrefix}seq.nextval INTO replKey FROM DUAL;
   INSERT INTO ${replPrefix}items (repl_key, trans_key, db_action, content, 
               version) values (replKey, 'UNKNOWN', 'statement', 
	       contClob, '${replVersion}');
   transId := DBMS_TRANSACTION.LOCAL_TRANSACTION_ID(FALSE);
   -- we compare in the db watcher if replKey = transKey and warn for it
   if transId = NULL THEN
      transId := CHR(replKey);
   END IF;
   UPDATE ${replPrefix}items SET trans_key=transId WHERE repl_key=replKey;

   res := 'TRUE';
   RETURN res;
END ${replPrefix}prepare_broadcast;
-- EOC (end of command: needed as a separator for our script parser)            


