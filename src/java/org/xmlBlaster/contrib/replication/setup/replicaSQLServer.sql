-- ---------------------------------------------------------------------------- 
-- Written by Michele Laghi (michele.laghi@avitech-ag.com) 2005-08-09           
-- THIS FILE IS THE INPUT FILE TO CONFIGURE THE NDB (TEST) FOR POSTGRES         
-- to invoke use:                                                               
-- osql -E -i replicaSQLServer.sql

-- ---------------------------------------------------------------------------- 
-- create the test tables to be replicated (here two equal tables are created)  
-- ---------------------------------------------------------------------------- 

use xmlBlaster
go


CREATE TABLE repltestrepl (uniqueId integer, name VARCHAR(50), surname text, email text, 
                       photo image, PRIMARY KEY (name, uniqueId));
CREATE TABLE repltest2repl (uniqueId integer, name VARCHAR(50), surname text, email text, 
                       photo image, PRIMARY KEY (name, uniqueId));

