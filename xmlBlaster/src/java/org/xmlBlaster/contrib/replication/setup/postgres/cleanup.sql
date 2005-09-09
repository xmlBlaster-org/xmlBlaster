-- ---------------------------------------------------------------------------- 
-- Written by Michele Laghi (michele.laghi@avitech-ag.com) 2005-08-09           
--                                                                              
-- ---------------------------------------------------------------------------- 

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

