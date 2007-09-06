-- ---------------------------------------------------------------------------- 
-- This is needed to add the triggers to the schemas to detect CREATE, DROP and 
-- ALTER events on a particular schema to be watched. It is invoked by the      
-- method I_DbSpecific.addSchemaToWatch(...)                                    
-- note that this must be invoked for each Schema.                              
-- Note that for some reason on ORACLE the DDL Schemas such as these do not have
-- any effect if the owner of the trigger is different from the owner of the    
-- schema. To solve that we set the schema on the database i.e:                 
-- AFTER CREATE ON DATABASE instead of AFTER CREATE ON ${schemaName}.SCHEMA     
-- ---------------------------------------------------------------------------- 


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}create_trigger is invoked when a new table is created.          
-- note that this must be invoked for each Schema.                              
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE TRIGGER ${replPrefix}crtg_${schemaName}
   AFTER CREATE ON DATABASE
DECLARE
   dbName     VARCHAR(${charWidth});
   tableName  VARCHAR(${charWidth});
   schemaName VARCHAR(${charWidth});
   dummy      VARCHAR(${charWidthSmall});
BEGIN
   dbName     := DATABASE_NAME;
   tableName  := DICTIONARY_OBJ_NAME;
   schemaName := DICTIONARY_OBJ_OWNER;
   dbms_output.put_line('CREATE TRIGGER INVOKED FOR ' || schemaName || 
                       '.' || tableName);
   dummy := ${replPrefix}add_table(dbName, schemaName, tableName, 'CREATE');
END ${replPrefix}crtg_${schemaName};

-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}drop_trigger is invoked when a table is dropped.                
-- note that this must be invoked for each Schema.                              
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE TRIGGER ${replPrefix}drtg_${schemaName}
   BEFORE DROP ON DATABASE
DECLARE
   dbName     VARCHAR(${charWidth});
   tableName  VARCHAR(${charWidth});
   schemaName VARCHAR(${charWidth});
   dummy      VARCHAR(${charWidthSmall});
BEGIN
   dbName     := DATABASE_NAME;
   tableName  := DICTIONARY_OBJ_NAME;
   schemaName := DICTIONARY_OBJ_OWNER;
   dbms_output.put_line('DROP TRIGGER INVOKED FOR ' || schemaName || 
                       '.' || tableName);
   dummy := ${replPrefix}add_table(dbName, schemaName, tableName, 'DROP');
END ${replPrefix}drtg_${schemaName};
-- EOC (end of command: needed as a separator for our script parser)            


-- ---------------------------------------------------------------------------- 
-- ${replPrefix}alter_trigger is invoked when a table is altered (modified).    
-- note that this must be invoked for each Schema.                              
-- ---------------------------------------------------------------------------- 

CREATE OR REPLACE TRIGGER ${replPrefix}altg_${schemaName}
   AFTER ALTER ON DATABASE
DECLARE
   dbName     VARCHAR(${charWidth});
   tableName  VARCHAR(${charWidth});
   schemaName VARCHAR(${charWidth});
   dummy      VARCHAR(${charWidthSmall});
BEGIN
   dbName     := DATABASE_NAME;
   tableName  := DICTIONARY_OBJ_NAME;
   schemaName := DICTIONARY_OBJ_OWNER;
   dbms_output.put_line('ALTER TRIGGER INVOKED FOR ' || schemaName || 
                       '.' || tableName);
   dummy := ${replPrefix}add_table(dbName, schemaName, tableName, 'ALTER');
END ${replPrefix}altg_${schemaName};
-- EOC (end of command: needed as a separator for our script parser)            



