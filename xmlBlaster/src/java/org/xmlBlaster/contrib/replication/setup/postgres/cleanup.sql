-- ---------------------------------------------------------------------------- 
-- Written by Michele Laghi (laghi@swissinfo.org) 2005-08-09                    
-- Cleanup sql script to clean up the system resources for the master part of   
-- the replication with xmlBlaster for Postgres                                 
-- ---------------------------------------------------------------------------- 

DROP TRIGGER repl_tables_trigger ON repl_tables CASCADE;
-- FLUSH DROP TRIGGER repl_tables_trigger ON repl_tables CASCADE                
DROP FUNCTION repl_tables_func() CASCADE;
-- FLUSH FUNCTION repl_tables_func() CASCADE                                    
DROP VIEW repl_cols_view CASCADE;
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
DROP FUNCTION repl_is_altered(name text) CASCADE;
-- FLUSH DROP FUNCTION repl_is_altered(name text) CASCADE                       
DROP FUNCTION repl_col2xml(name text, content text) CASCADE;
-- FLUSH DROP FUNCTION repl_col2xml(name text, content text) CASCADE            
DROP FUNCTION repl_col2xml_base64(name text, content bytea) CASCADE;
-- FLUSH DROP FUNCTION repl_col2xml_base64(name text, content bytea) CASCADE    
DROP FUNCTION repl_col2xml_cdata(name text, content text) CASCADE;
-- FLUSH DROP FUNCTION repl_col2xml_cdata(name text, content text) CASCADE      
DROP FUNCTION repl_check_structure(text) CASCADE;
-- FLUSH DROP FUNCTION repl_check_structure() CASCADE                           
DROP FUNCTION repl_needs_prot(text) CASCADE;
-- FLUSH DROP FUNCTION repl_needs_prot() CASCADE                                
DROP FUNCTION repl_increment() CASCADE;
-- FLUSH DROP FUNCTION repl_increment() CASCADE                                 


