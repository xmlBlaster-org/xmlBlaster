/*--- formatted by Jindent 2.1, (www.c-lab.de/~jindent) ---*/
/*------------------------------------------------------------------------------
Name:      ConnectionWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper class to encapsulate a Connection and  ConnectionDescriptor
Version:   $Id: ConnectionWrapper.java,v 1.1 2000/02/22 04:25:07 jsrbirch Exp $
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.pool.jdbc;

import org.xmlBlaster.protocol.jdbc.ConnectionDescriptor;
import java.sql.*;

/**
 * Class declaration
 * 
 * 
 * @author
 * @version %I%, %G%
 */
public class ConnectionWrapper {

   private Connection            connection = null;
   private ConnectionDescriptor  descriptor = null;
   private long                  timestamp = -1;

   /**
    * Constructor declaration
    * 
    * 
    * @param connection
    * @param descriptor
    * 
    * @see
    */
   public ConnectionWrapper(Connection connection, 
                            ConnectionDescriptor descriptor) {
      this.connection = connection;
      this.descriptor = descriptor;
      timestamp = System.currentTimeMillis();
   }

   /**
    * Method declaration
    * 
    * 
    * @return
    * 
    * @see
    */
   public Connection getConnection() {
      return connection;
   } 

   /**
    * Method declaration
    * 
    * 
    * @return
    * 
    * @see
    */
   public ConnectionDescriptor getConnectionDescriptor() {
      return descriptor;
   } 

   /**
    * Method declaration
    * 
    * 
    * @param connection
    * 
    * @see
    */
   public void setConnection(Connection connection) {
      timestamp = System.currentTimeMillis();
      this.connection = connection;
   } 

   /**
    * Method declaration
    * 
    * 
    * @param descriptor
    * 
    * @see
    */
   public void setConnectionDescriptor(ConnectionDescriptor descriptor) {
      timestamp = System.currentTimeMillis();
      this.descriptor = descriptor;
   } 

   /**
    * Method declaration
    * 
    * 
    * @return
    * 
    * @see
    */
   public long getTimestamp() {
      return timestamp;
   } 

   /**
    * Method declaration
    * 
    * 
    * @see
    */
   public void setTimestamp() {
      timestamp = System.currentTimeMillis();
   } 

}







/*--- formatting done in "xmlBlaster Convention" style on 02-21-2000 ---*/

