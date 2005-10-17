-- ---------------------------------------------------------------------------- 
-- Written by Michele Laghi (laghi@swissinfo.org) 2005-08-09                    
-- Cleanup sql script to clean up the system resources for the master part of   
-- the replication with xmlBlaster for Postgres                                 
-- ---------------------------------------------------------------------------- 

PURGE RECYCLEBIN
-- FLUSH (purged recyclebin)                                                    
DROP FUNCTION ${replPrefix}add_table
-- FLUSH (dropped ${replPrefix}add_table)                                       
DROP FUNCTION ${replPrefix}base64_enc_blob
-- FLUSH (dropped ${replPrefix}base64_enc_blob)                                 
DROP FUNCTION ${replPrefix}base64_enc_clob
-- FLUSH (dropped ${replPrefix}base64_enc_clob)                                 
DROP FUNCTION ${replPrefix}base64_enc_raw
-- FLUSH (dropped ${replPrefix}base64_enc_raw)                                  
DROP FUNCTION ${replPrefix}base64_enc_varchar2
-- FLUSH (dropped ${replPrefix}base64_enc_varchar2)                             
DROP FUNCTION ${replPrefix}base64_helper
-- FLUSH (dropped ${replPrefix}base64_helper)                                   
DROP FUNCTION ${replPrefix}check_tables
-- FLUSH (dropped ${replPrefix}check_tables)                                    
DROP TRIGGER ${replPrefix}create_trigger_xmlblaster	
-- FLUSH (dropped ${replPrefix}create_trigger_xmlblaster)                       
DROP TRIGGER ${replPrefix}drop_trigger_xmlblaster
-- FLUSH (dropped ${replPrefix}drop_trigger_xmlblaster)                         
DROP TRIGGER ${replPrefix}alter_trigger_xmlblaster
-- FLUSH (dropped ${replPrefix}alter_trigger_xmlblaster)                        
DROP TRIGGER ${replPrefix}tables_trigger
-- FLUSH (dropped ${replPrefix}tables_trigger)                                  
DROP TABLE ${replPrefix}tables
-- FLUSH (dropped ${replPrefix}tables)                                          
DROP SEQUENCE ${replPrefix}seq
-- FLUSH (dropped ${replPrefix}seq)                                             
DROP TABLE ${replPrefix}items
-- FLUSH (dropped ${replPrefix}items)                                           
DROP FUNCTION ${replPrefix}schema_funct
-- FLUSH (dropped ${replPrefix}schema_funct)                                    
DROP FUNCTION ${replPrefix}col2xml
-- FLUSH (dropped ${replPrefix}col2xml)                                         
DROP FUNCTION ${replPrefix}col2xml_base64
-- FLUSH (dropped ${replPrefix}col2xml_base64)                                  
DROP FUNCTION ${replPrefix}col2xml_cdata
-- FLUSH (dropped ${replPrefix}col2xml_cdata)                                   
DROP FUNCTION ${replPrefix}check_structure
-- FLUSH (dropped ${replPrefix}check_structure)                                 
DROP FUNCTION ${replPrefix}needs_prot
-- FLUSH (dropped ${replPrefix}needs_prot)                                      
DROP FUNCTION ${replPrefix}increment
-- FLUSH (dropped ${replPrefix}increment)                                       
DROP FUNCTION ${replPrefix}check_tables
-- FLUSH (dropped ${replPrefix}check_tables)                                    


