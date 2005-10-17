-- ---------------------------------------------------------------------------- 
-- Written by Michele Laghi (laghi@swissinfo.org) 2005-08-09                    
-- Cleanup sql script to clean up the system resources for the master part of   
-- the replication with xmlBlaster for Postgres                                 
-- ---------------------------------------------------------------------------- 

DROP TRIGGER ${replPrefix}tables_trigger ON ${replPrefix}tables CASCADE;
-- FLUSH DROP TRIGGER ${replPrefix}tables_trigger ON ${replPrefix}tables        
DROP FUNCTION ${replPrefix}tables_func() CASCADE;
-- FLUSH FUNCTION ${replPrefix}tables_func()                                    
DROP VIEW ${replPrefix}cols_view CASCADE;
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
DROP FUNCTION ${replPrefix}is_altered(name text) CASCADE;
-- FLUSH DROP FUNCTION ${replPrefix}is_altered(name text)                       
DROP FUNCTION ${replPrefix}col2xml(name text, content text) CASCADE;
-- FLUSH DROP FUNCTION ${replPrefix}col2xml(name text, content text)            
DROP FUNCTION ${replPrefix}col2xml_base64(name text, content bytea) CASCADE;
-- FLUSH DROP FUNCTION ${replPrefix}col2xml_base64(name text, content bytea)    
DROP FUNCTION ${replPrefix}col2xml_cdata(name text, content text) CASCADE;
-- FLUSH DROP FUNCTION ${replPrefix}col2xml_cdata(name text, content text)      
DROP FUNCTION ${replPrefix}check_structure(text) CASCADE;
-- FLUSH DROP FUNCTION ${replPrefix}check_structure() CASCADE                   
DROP FUNCTION ${replPrefix}needs_prot(text) CASCADE;
-- FLUSH DROP FUNCTION ${replPrefix}needs_prot() CASCADE                        
DROP FUNCTION ${replPrefix}increment() CASCADE;
-- FLUSH DROP FUNCTION ${replPrefix}increment() CASCADE                         


