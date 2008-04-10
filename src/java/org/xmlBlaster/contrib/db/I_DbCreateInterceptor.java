/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.db;

import java.sql.Connection;

import org.xmlBlaster.contrib.I_Info;


/**
 * Interface who's implementation are invoked each time a connection is created inside the I_DbPool.
 * It is this way possible to make some configurations on the connection to a JDBC pool implementation.
 * Note that the same instance of the implementation of this class will be invoked on each connection
 * creation.
 * The implementing class should also provide a default constructor.
 * 
 * @author michele@laghi.eu 
 */
public interface I_DbCreateInterceptor  {

   void onCreateConnection(Connection conn) throws Exception;
   
   /**
    * Needs to be called after construction. 
    * @param info The configuration
    */
   void init(I_Info info) throws Exception;

   void shutdown();
}
