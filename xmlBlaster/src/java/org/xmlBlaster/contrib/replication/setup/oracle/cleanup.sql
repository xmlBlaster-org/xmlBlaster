-- ---------------------------------------------------------------------------- 
-- Written by Michele Laghi (laghi@swissinfo.org) 2005-08-09                    
-- Cleanup sql script to clean up the system resources for the master part of   
-- the replication with xmlBlaster for Postgres                                 
-- ---------------------------------------------------------------------------- 

PURGE RECYCLEBIN
-- FLUSH (purged recyclebin)                                                    
DROP FUNCTION repl_add_table
-- FLUSH (dropped repl_add_table)                                               
DROP FUNCTION repl_base64_enc_blob
-- FLUSH (dropped repl_base64_enc_blob)                                         
DROP FUNCTION repl_base64_enc_clob
-- FLUSH (dropped repl_base64_enc_clob)                                         
DROP FUNCTION repl_base64_enc_raw
-- FLUSH (dropped repl_base64_enc_raw)                                          
DROP FUNCTION repl_base64_enc_varchar2
-- FLUSH (dropped repl_base64_enc_varchar2)                                     
DROP FUNCTION repl_base64_helper
-- FLUSH (dropped repl_base64_helper)                                           
DROP FUNCTION repl_check_tables
-- FLUSH (dropped repl_check_tables)                                            
DROP TRIGGER repl_create_trigger_xmlblaster	
-- FLUSH (dropped repl_create_trigger_xmlblaster)                               
DROP TRIGGER repl_drop_trigger_xmlblaster
-- FLUSH (dropped repl_drop_trigger_xmlblaster)                                 
DROP TRIGGER repl_alter_trigger_xmlblaster
-- FLUSH (dropped repl_alter_trigger_xmlblaster)                                
DROP TRIGGER repl_tables_trigger
-- FLUSH (dropped repl_tables_trigger)                                          
DROP TABLE repl_tables
-- FLUSH (dropped repl_tables)                                                  
DROP SEQUENCE repl_seq
-- FLUSH (dropped repl_seq)                                                     
DROP TABLE repl_items
-- FLUSH (dropped repl_items)                                                   
DROP FUNCTION repl_schema_funct
-- FLUSH (dropped repl_schema_funct)                                            
DROP FUNCTION repl_col2xml
-- FLUSH (dropped repl_col2xml)                                                 
DROP FUNCTION repl_col2xml_base64
-- FLUSH (dropped repl_col2xml_base64)                                          
DROP FUNCTION repl_col2xml_cdata
-- FLUSH (dropped repl_col2xml_cdata)                                           
DROP FUNCTION repl_check_structure
-- FLUSH (dropped repl_check_structure)                                         
DROP FUNCTION repl_needs_prot
-- FLUSH (dropped repl_needs_prot)                                              
DROP FUNCTION repl_increment
-- FLUSH (dropped repl_increment)                                               
DROP FUNCTION repl_check_tables
-- FLUSH (dropped repl_check_tables)                                            


