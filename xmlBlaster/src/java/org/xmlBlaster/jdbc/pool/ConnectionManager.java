/*--- formatted by Jindent 2.1, (www.c-lab.de/~jindent) ---*/

package org.xmlBlaster.jdbc.pool;

import java.util.*;
import java.sql.*;
import org.xmlBlaster.jdbc.ConnectionDescriptor;
import org.xmlBlaster.util.Log;

/**
 * Class declaration
 * 
 * 
 * @author
 * @version %I%, %G%
 */
public class ConnectionManager
 {

   private static final String      ME = "ConnectionManager";

   public static ConnectionManager  instance = null;

   private Thread                   reaper;
   private long                     reap_interval = 20000;

   private Hashtable                connections;

   /**
    * Constructor declaration
    * 
    * 
    * @see
    */
   private ConnectionManager()
    {
      connections = new Hashtable();
      reaper = new Thread(new Runnable()
       {

         /**
          * Method declaration
          * 
          * 
          * @see
          */
         public void run()
          {
            reap();
         } 

      });

      reaper.start();
   }

   /**
    * Method declaration
    * 
    * 
    * @return
    * 
    * @see
    */
   public static ConnectionManager getInstance()
    {
      if (instance == null)
       {
         instance = new ConnectionManager();
      } 

      return instance;
   } 

   /**
    * Method declaration
    * 
    * 
    * @param descriptor
    * 
    * @return
    * 
    * @throws SQLException
    * 
    * @see
    */
   public ConnectionWrapper getConnectionWrapper(ConnectionDescriptor descriptor) 
           throws SQLException
    {
      Connection        connection = null;
      String            key = descriptor.getConnectionkey();
      ConnectionWrapper wrapper = (ConnectionWrapper) connections.get(key);

      if (wrapper == null)
       {
         connection = createConnection(descriptor);
         wrapper = new ConnectionWrapper(connection, descriptor);
      } 
      else
       {
         connection = wrapper.getConnection();

         if (connection.isClosed())
          {
            connection = createConnection(descriptor);
         } 

         wrapper.setConnection(connection);
         wrapper.setConnectionDescriptor(descriptor);
      } 

      wrapper.setTimestamp();

      return wrapper;
   } 

   /**
    * Method declaration
    * 
    * 
    * @param descriptor
    * 
    * @return
    * 
    * @throws SQLException
    * 
    * @see
    */
   private Connection createConnection(ConnectionDescriptor descriptor) 
           throws SQLException
    {
      Connection  connection = null;
      String      url = descriptor.getUrl();
      String      username = descriptor.getUsername();
      String      password = descriptor.getPassword();

      connection = DriverManager.getConnection(url, username, password);

      if (descriptor.getConnectionlifespan() > -1)
       {
         connections.put(descriptor.getConnectionkey(), 
                         new ConnectionWrapper(connection, descriptor));
      } 

      return connection;
   } 

   /**
    * Method declaration
    * 
    * 
    * @see
    */
   private void reap()
    {
      while (true)
       {
         try
          {
            Thread.sleep(reap_interval);
         } 
         catch (Exception e) {}

         Vector      trash = new Vector();
         Enumeration cons = connections.keys();

         while (cons.hasMoreElements())
          {
            String               key = (String) cons.nextElement();
            ConnectionWrapper    wrapper = 
               (ConnectionWrapper) connections.get(key);
            ConnectionDescriptor descriptor = 
               wrapper.getConnectionDescriptor();
            Connection           connection = wrapper.getConnection();
            long                 now = System.currentTimeMillis();
            long                 then = wrapper.getTimestamp();
            long                 ttl = descriptor.getConnectionlifespan();

            if ((now - then) > ttl)
             {
               trash.addElement(key);

               try
                {
                  connection.close();
               } 
               catch (SQLException sqle) {}
            } 
         } 

         Enumeration garbage = trash.elements();

         while (garbage.hasMoreElements())
          {
            String   key = (String) garbage.nextElement();

            connections.remove(key);
            Log.info(ME, "Removed connection keyed by =>" + key);
         } 
      } 
   } 

   /**
    * Method declaration
    * 
    * 
    * @see
    */
   public void release()
    {
      Enumeration cons = connections.keys();

      while (cons.hasMoreElements())
       {
         String   key = (String) cons.nextElement();

         Log.info(ME, "Releasing connection =>" + key);

         ConnectionWrapper    wrapper = 
            (ConnectionWrapper) connections.get(key);
         ConnectionDescriptor descriptor = wrapper.getConnectionDescriptor();
         Connection           connection = wrapper.getConnection();

         try
          {
            connection.close();
         } 
         catch (Exception e) {}
      } 
   } 

}





/*--- formatting done in "My Own Convention" style on 02-21-2000 ---*/

