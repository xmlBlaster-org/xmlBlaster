/*------------------------------------------------------------------------------
Name:      NamedConnectionPool.java
Project:   xmlBlaster.org
Copyright: jutils.org, see jutils-LICENSE file
Comment:   Basic handling of a pool of limited resources
Version:   $Id: NamedConnectionPool.java,v 1.8 2000/08/03 13:36:08 ruff Exp $
           $Source: /opt/cvsroot/xmlBlaster/src/java/org/xmlBlaster/protocol/jdbc/NamedConnectionPool.java,v $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;

import org.jutils.JUtilsException;
import org.jutils.log.Log;
import org.jutils.init.Args;
import org.jutils.time.I_Timeout;
import org.jutils.time.Timeout;
import org.jutils.pool.PoolManager;
import org.jutils.pool.I_PoolManager;
import org.jutils.pool.ResourceWrapper;
import org.jutils.runtime.Sleeper;

import java.util.Hashtable;
import java.util.Enumeration;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * This is a specialized JDBC connection pool for xmlBlaster.
 * <p />
 * It allows accessing any number of different databases with
 * arbitrary login users.<br />
 * Every database user is separately pooled for maximum performance.<br />
 * Every DB request needs to pass the DB-url, login name and password,
 * since the clients are not permanently connected.<br />
 * Unused connection pools are freed after some time.
 * <p />
 * The timeout parameters and pool size is adjustable.
 * <p />
 * The connections are established on demand (lazy allocation).
 * Pre-allocation is currently not implemented.
 * The first SQL request (for example with Oracle) consumes about
 * 1 second to establish the connection, the following requests
 * get this connection from the pool, which is below 1 millisecond.
 * If more than one SQL requests are done simultaneously, the pool
 * increases the number of parallel connections.
 * <p />
 * Load the drivers before using this pool, e.g.<br />
 * <pre>
 *    java -Djdbc.drivers=foo.bah.Driver:wombat.sql.Driver:bad.taste.ourDriver ...
 * </pre>
 * or in xmlBlaster.properties, e.g.<br />
 * <pre>
 *    JdbcDriver.drivers=oracle.jdbc.driver.OracleDriver,org.gjt.mm.mysql.Driver,postgresql.Driver,de.sag.jdbc.adabasd.ADriver,sun.jdbc.odbc.JdbcOdbcDriver,com.sybase.jdbc2.jdbc.SybDriver
 * </pre>
 * You use reserve() to get a connection and need to call release() after using it,
 * note that the connection parameters are optional:
 * <pre>
 *    String dbStmt = "select * from user_table";
 *    java.sql.Connection con = namedPool.reserve(dbUrl, dbUser, dbPasswd, 60*60*1000L, 100, 10*60*1000L);
 *    java.sql.Statement stmt = null;
 *    java.sql.ResultSet rs = null;
 *    try {
 *       stmt = con.createStatement();
 *       rs = stmt.executeQuery(dbStmt);
 *    } finally {
 *       if (rs!=null) rs.close();
 *       if (stmt!=null) stmt.close();
 *       if (con!=null) namedPool.release(dbUrl, user, pw, con);
 *    }
 * </pre>
 */
public class NamedConnectionPool
{
   private static final String ME = "NamedConnectionPool";
   private Hashtable namedPools = new Hashtable();

   private final Object meetingPoint = new Object();


   /**
    * Use this method to get a JDBC connection.
    * <br />
    * The pooling properties are set to default values.
    * @param dbUrl    For example "jdbc:oracle:thin:@localhost:1521:mydb
    * @param dbUser   The database user
    * @param dbPasswd The database password
    */
   Connection reserve(String dbUrl, String dbUser, String dbPasswd) throws XmlBlasterException
   {
      return reserve(dbUrl, dbUser, dbPasswd, -1, -1, -1);
   }


   /**
    * Use this method to get a JDBC connection.
    * <br />
    * Usually only the first time for a user, to specify all parameters.
    * @param dbUrl For example "jdbc:oracle:thin:@localhost:1521:mydb
    * @param eraseUnusedPoolTimeout Remove pool of a user if not in use, in ms
    *          0 switches it off, -1 uses default setting 1 hour (from xmlBlaster.properties)
    * @param maxInstances Default is max. 20 connections (from xmlBlaster.properties)
    * @param idleToErase  in msec
    *          0 switches it off, -1 uses default setting 10 min. (from xmlBlaster.properties)
    */
   Connection reserve(String dbUrl, String dbUser, String dbPasswd, long eraseUnusedPoolTimeout,
                      int maxInstances, long idleToErase) throws XmlBlasterException
   {
      long busyToIdle = 0L; // On timeout it changes state from 'busy' to 'idle': switched off!
      UnnamedConnectionPool pool = getPool(dbUrl, dbUser, dbPasswd);
      if (pool == null) { // check before as well to increase performance
         synchronized(meetingPoint) {
            pool = getPool(dbUrl, dbUser, dbPasswd);
            if (pool == null) {
               if (dbPasswd == null) throw new XmlBlasterException(ME+".MissingPasswd", "Please give a password for '" + dbUser + "' when creating a JDBC pool");
               pool = new UnnamedConnectionPool(this, dbUrl, dbUser, dbPasswd, eraseUnusedPoolTimeout, maxInstances, busyToIdle, idleToErase);
               namedPools.put(getKey(dbUrl, dbUser, dbPasswd), pool);
            }
         }
      }
      try {
         Connection con = pool.reserve();
         if (Log.TRACE) Log.trace(ME, "reserve(" + dbUrl + ", " + dbUser + ") con=" + con);
         return con;
      }
      catch(Exception e) {
         Log.error(ME, "System Exception in connect(" + dbUrl + ", " + dbUser + "): " + e.toString());
         throw new XmlBlasterException(ME+".NoOpen", "Couldn't open database connection: " + e.toString());
      }
   }

   /**
    * Use this method to release a JDBC connection.
    */
   void release(String dbUrl, String dbUser, String dbPasswd, Connection con) throws XmlBlasterException
   {
      UnnamedConnectionPool pool = getPool(dbUrl, dbUser, dbPasswd);
      if (pool != null) {
         pool.release(con);
         if (Log.TRACE) Log.trace(ME, "release(" + dbUrl + ", " + dbUser + ") con=" + con);
      }
   }

   /**
    * Destroy the JDBC connection pool from a specific user.
    * The driver remains.
    * @param The UnnamedConnectionPool object
    */
   void destroy(String dbUrl, String dbUser, String dbPasswd) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Entering destroy() ...");
      try {
         String key = getKey(dbUrl, dbUser, dbPasswd);
         UnnamedConnectionPool pool = (UnnamedConnectionPool)namedPools.remove(key);
         if (pool != null)
            pool.destroy();
         if (Log.TRACE) Log.trace(ME, "All JDBC connections for '" + dbUrl + "' destroyed");
      }
      catch (Exception e) {
         Log.error(ME, "System Exception in destroy JDBC connection for '" + dbUrl + "': " + e.toString());
         throw new XmlBlasterException(ME+".DestroyError", "System Exception in destroy JDBC connection for '" + dbUrl + "': " + e.toString());
      }
   }


   /**
    * Destroy the complete named pool of all users.
    * This object is still valid for further use.
    */
   void destroy()
   {
      for (Enumeration e = namedPools.elements() ; e.hasMoreElements() ;) {
         UnnamedConnectionPool pool = (UnnamedConnectionPool)e.nextElement();
         pool.destroy();
      }
      namedPools.clear();
   }

   /**
    * @return instanceId <db_url>^<username>/<passwd>, e.g.  "jdbc:oracle:thin:@localhost:1521:mydb^jack/secret"
    */
   private String getKey(String dbUrl, String dbUser, String dbPasswd)
   {
      StringBuffer buf = new StringBuffer(80);
      return buf.append(dbUrl).append("^").append(dbUser).append("/").append(dbPasswd).toString();
   }
   private UnnamedConnectionPool getPool(String dbUrl, String dbUser, String dbPasswd)
   {
      String key = getKey(dbUrl, dbUser, dbPasswd);
      return (UnnamedConnectionPool)namedPools.get(key);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml()
   {
      StringBuffer buf = new StringBuffer(256);
      String offset = "\n";
      buf.append(offset).append("<").append(ME).append(">");
      buf.append(offset).append("   <namedPools num='").append(namedPools.size()).append("'>");
      for (Enumeration e = namedPools.elements() ; e.hasMoreElements() ;) {
         UnnamedConnectionPool pool = (UnnamedConnectionPool)e.nextElement();
         buf.append(pool.toXml());
      }
      buf.append(offset).append("   </namedPools>");
      buf.append(offset).append("</" + ME + ">");
      return buf.toString();
   }


   /**
    * Inner class, every user of the Named pool has its own connection pool. 
    * <p />
    * If the resource pool is exhausted, the request will poll for a connection
    * 5 times, with 1 sec sleeping in between.<br />
    * This feature is adjustable in xmlBlaster.properties with:<br />
    * <pre>
    *   JdbcPool.maxResourceExhaustRetries=5
    *   JdbcPool.resourceExhaustSleepGap=1000
    * </pre>
    */
   private class UnnamedConnectionPool implements I_PoolManager, I_Timeout
   {
      private static final String ME = "UnnamedConnectionPool";
      private NamedConnectionPool boss = null;
      PoolManager poolManager = null;
      private String dbUrl;
      private String dbUser;
      private String dbPasswd;
      private long eraseUnusedPoolTimeout;
      /** If the pool is exhausted, we poll the given times */
      private int maxResourceExhaustRetries;
      /** If the pool is exhausted, we poll every given millis<br />
          Please note that the current request thread will block for maxResourceExhaustRetries*resourceExhaustSleepGap millis. */
      private long resourceExhaustSleepGap;
      private Long timeoutHandle = new Long(0L); // initialize for synchronized

      private final Object meetingPoint = new Object();

      /**
       * @param boss           My manager
       * @param eraseUnusedPoolTimeout This pool is erased after given millis without activity of the owning user<br />
       *                       0 switches it off
       * @param maxInstances   Max. number of resources in this pool.
       *                       -1 uses default of 20 (xmlBlaster.properties)<br />
       * @param busyToIdleTimeout Max. busy time of this resource in milli seconds<br />
       *                       On timeout it changes state from 'busy' to 'idle'.<br />
       *                       You can overwrite this value for each resource instance<br />
       *                       0 switches it off<br />
       *                       -1 uses default (switched off)<br />
       *                       You get called back through I_PoolManager.busyToIdle() on timeout
       *                       allowing you to code some specific handling.
       * @param idleToEraseTimeout Max. idle time span of this resource in milli seconds<br />
       *                     On timeout it changes state from 'idle' to 'undef' (it is deleted).<br />
       *                     You can overwrite this value for each resource instance<br />
       *                     0 switches it off<br />
       *                     -1 uses default (10 min) (xmlBlaster.properties)<br />
       *                     You get called back through I_PoolManager.toErased() on timeout
       *                     allowing you to code some specific handling.
       */
      public UnnamedConnectionPool(NamedConnectionPool boss, String dbUrl, String dbUser, String dbPasswd,
                                   long eraseUnusedPoolTimeout, int maxInstances, long busyToIdle, long idleToErase)
      {
         this.boss = boss;
         this.dbUrl = dbUrl;
         this.dbUser = dbUser;
         this.dbPasswd = dbPasswd;
         this.eraseUnusedPoolTimeout = eraseUnusedPoolTimeout;

         if (eraseUnusedPoolTimeout == -1)
            eraseUnusedPoolTimeout = XmlBlasterProperty.get("JdbcPool.eraseUnusedPoolTimeout", 60*60*1000L); // If a user disapears for one hour, delete his pool
         if (maxInstances == -1)
            maxInstances = XmlBlasterProperty.get("JdbcPool.maxInstances", 20); // Max. number of connections
         if (busyToIdle == -1)
            busyToIdle = XmlBlasterProperty.get("JdbcPool.busyToIdleTimeout", 0); // How long may a query last
         if (idleToErase == -1)
            idleToErase = XmlBlasterProperty.get("JdbcPool.idleToEraseTimeout", 10*60*1000L); // How long does an unused connection survive (10 min)

         maxResourceExhaustRetries = XmlBlasterProperty.get("JdbcPool.maxResourceExhaustRetries", 5);
         resourceExhaustSleepGap = XmlBlasterProperty.get("JdbcPool.resourceExhaustSleepGap", 1000);   // milli

         poolManager = new PoolManager(ME, this, maxInstances, busyToIdle, idleToErase);
         if (eraseUnusedPoolTimeout > 10L)
            synchronized(timeoutHandle) {
               timeoutHandle = Timeout.getInstance().addTimeoutListener(this, eraseUnusedPoolTimeout, "dummy");
            }
      }

      /** This callback does nothing (enforced by interface I_PoolManager) */
      public void idleToBusy(Object resource) {
         // Connection con = (Connection)resource;
      }

      /** This callback does nothing (enforced by interface I_PoolManager */
      public void busyToIdle(Object resource) {
         // Connection con = (Connection)resource;
      }

      /**
       * Create a new JDBC connection, the driver must be registered already.
       */
      public Object toCreate(String instanceId) throws JUtilsException {
         try {
            return DriverManager.getConnection (dbUrl, dbUser, dbPasswd);
         }
         catch(Exception e) {
            Log.error(ME, "System Exception in connect(" + dbUrl + ", " + dbUser + "): " + e.toString());
            throw new JUtilsException(ME, "Couldn't open database connection: " + e.toString());
         }
      }

      /**
       * Destroy the JDBC connection.
       * The driver remains.
       * @param The Connection object
       */
      public void toErased(Object resource) {
         Connection con = (Connection)resource;
         try {
            con.close();
            if (Log.TRACE) Log.trace(ME, "JDBC connection closed for '" + dbUrl + "', '" + dbUser + "'");
         }
         catch (Exception e) {
            Log.error(ME, "System Exception in close JDBC connection: " + e.toString());
         }
      }

      /**
       * Use this method to get a JDBC connection.
       */
      Connection reserve() throws XmlBlasterException {
         if (poolManager == null) { throw new XmlBlasterException(ME+".Destroyed", "Pool is destroyed"); }
         if (Log.TRACE) Log.trace(ME, "Entering reserve '" + dbUrl + "', '" + dbUser + "'");
         int ii=0;
         while (true) {
            try {
               synchronized(meetingPoint) {
                  if (eraseUnusedPoolTimeout > 10L) {
                     synchronized(timeoutHandle) {
                        timeoutHandle = Timeout.getInstance().refreshTimeoutListener(timeoutHandle, eraseUnusedPoolTimeout);
                     }
                  }
                  ResourceWrapper rw = (ResourceWrapper)poolManager.reserve(PoolManager.USE_OBJECT_REF);
                  Connection con = (Connection)rw.getResource();
                  return con;
               }
            }
            catch (JUtilsException e) {
               if (e.id.equals("ResourceExhaust") && ii < maxResourceExhaustRetries) {
                  if (ii == 0) Log.warning(ME, "Caught exception in reserve(), going to poll " + maxResourceExhaustRetries + " times every " + resourceExhaustSleepGap + " millis");
                  Sleeper.sleep(resourceExhaustSleepGap);
                  ii++;
               }
               else {
                  Log.error(ME, "Caught exception in reserve(): " + e.toString() + "\n" + toXml());
                  throw new XmlBlasterException(e);
               }
            }
         }
      }

      /**
       * Use this method to release a JDBC connection.
       */
      void release(Connection con) throws XmlBlasterException {
         if (poolManager == null) { throw new XmlBlasterException(ME+".Destroyed", "Pool is destroyed"); }
         if (Log.TRACE) Log.trace(ME, "Entering release '" + dbUrl + "', '" + dbUser + "' conId=" + con);
         try {
            synchronized(meetingPoint) {
               poolManager.release(""+con);
            }
         }
         catch (JUtilsException e) {
            Log.error(ME, "Caught exception in release(): " + e.toString());
            throw new XmlBlasterException(e);
         }
      }

      /**
       * Timeout callback enforced by I_Timeout.
       */
      public void timeout(java.lang.Object o)
      {
         if (Log.TRACE) Log.trace(ME, "Entering pool destroy timeout for '" + dbUrl + "', '" + dbUser + "' ...");
         synchronized(meetingPoint) {
            if (poolManager.getNumBusy() != 0) {
               Log.warning(ME, "Can't destroy pool from '" + dbUrl + "', '" + dbUser + "', he seems to be busy working on his database.");
               synchronized(timeoutHandle) {
                  timeoutHandle = Timeout.getInstance().addTimeoutListener(this, eraseUnusedPoolTimeout, "dummy");
               }
               return;
            }
            try {
               boss.destroy(dbUrl, dbUser, dbPasswd);
            } catch(XmlBlasterException e) {
               Log.error(ME, "timeout: " + e.toString());
            }
         }
      }

      /** Destroy the complete unnamed pool */
      void destroy() {
         synchronized(meetingPoint) {
            if (poolManager != null) {
               poolManager.destroy();
               poolManager = null;
            }
         }
         NamedConnectionPool boss = null;
         dbUrl = null;
         dbUser = null;
      }

      /**
       * Dump state of this object into a XML ASCII string.
       */
      public final String toXml()
      {
         StringBuffer buf = new StringBuffer(256);
         String offset = "\n   ";
         buf.append(offset).append("<").append(ME).append(" url='").append(dbUrl).append("' user='").append(dbUser).append("' eraseUnusedPoolTimeout='").append(eraseUnusedPoolTimeout).append("'>");
         if (poolManager != null)
            buf.append(poolManager.toXml("      "));
         else
            buf.append(poolManager.toXml("      DESTROYED"));
         buf.append(offset).append("</" + ME + ">");
         return buf.toString();
      }
   }



   /**
    * For testing only.
    * <p />
    * Invoke: java org.xmlBlaster.protocol.jdbc.NamedConnectionPool -trace true
    */
   public static void main(String[] args) {
      NamedConnectionPool namedPool = null;
      try {
         Log.setLogLevel(args); // initialize log level

         String ME = "TestConnection";
         String dbDriver = Args.getArg(args, "-dbDriver", "oracle.jdbc.driver.OracleDriver");
         final String dbUrl = Args.getArg(args, "-dbUrl", "jdbc:oracle:thin:@localhost:1521:MARCEL");
         final String dbUser = Args.getArg(args, "-dbUser", "mrf");
         final String dbPasswd = Args.getArg(args, "-dbPasswd", "mrf");
         final String dbUser2 = Args.getArg(args, "-dbUser2", "system");
         final String dbPasswd2 = Args.getArg(args, "-dbPasswd2", "manager");
         final String dbStmt = Args.getArg(args, "-dbStmt", "select * from user_tables");

         Class cl = Class.forName(dbDriver);
         java.sql.Driver dr = (java.sql.Driver)cl.newInstance();
         java.sql.DriverManager.registerDriver(dr);
         Log.info(ME, "Jdbc driver '" + dbDriver + "' loaded.");

         namedPool = new NamedConnectionPool();

         final long timeToDeath = 10*1000L;

         class Test extends Thread {
            private String ME = "TestThread";
            private NamedConnectionPool np;
            private String user;
            private String pw;
            Test(String name, NamedConnectionPool namedP, String user, String pw) {
               super(name);
               this.np = namedP;
               this.ME = name;
               this.user = user;
               this.pw = pw;
            }
            public void run() {
               try {
                  for (int ii=0; ii<50; ii++) {
                     Log.info(ME, " query run=" + ii + "\n");
                     org.jutils.time.StopWatch watch = new org.jutils.time.StopWatch();
                     Connection con = np.reserve(dbUrl, user, pw, timeToDeath, 100, 40*1000L);
                     Log.info(ME, "Reserved connection id=" + con + watch.toString());
                     //Log.info(ME, np.toXml());
                     java.sql.Statement stmt = null;
                     java.sql.ResultSet rs = null;
                     try {
                        stmt = con.createStatement();
                        rs = stmt.executeQuery(dbStmt);
                     } finally {
                        if (rs!=null) rs.close();
                        if (stmt!=null) stmt.close();
                        watch = new org.jutils.time.StopWatch();
                        if (con!=null) np.release(dbUrl, user, pw, con);
                        Log.info(ME, "Query successful done, connection released" + watch.toString());
                        //Log.info(ME, np.toXml());
                     }
                  }
                  Log.info(ME, "Going to sleep " + (timeToDeath+1000L) + " msec");
                  try { Thread.currentThread().sleep(timeToDeath+1000L); } catch( InterruptedException i) {}
                  Log.info(ME, "After sleeping " + (timeToDeath+1000L) + " msec, erased connection\n" + np.toXml());
               }
               catch(Throwable e) {
                  e.printStackTrace();
                  if (np!=null) { np.destroy(); np = null; } // this error handling is not thread save
                  Log.panic(ME, "TEST FAILED" + e.toString());
               }
            }
         }

         java.util.Vector vec = new java.util.Vector();
         for (int ii=0; ii<8; ii++) {
            String name = "TestThread-"+ii;
            if (true) { //(ii % 2) == 0) {
               Test p = new Test(name, namedPool, dbUser, dbPasswd);
               p.setDaemon(true);
               p.start();
               vec.addElement(p);
            }
            else {
               Test p = new Test(name, namedPool, dbUser2, dbPasswd2);
               p.setDaemon(true);
               p.start();
               vec.addElement(p);
            }
            Log.info(ME, "Started " + name + " ...");
         }

         for (int ii=0; ii<vec.size(); ii++) {
            Test p = (Test)vec.elementAt(ii);
            p.join();
         }

         Log.info(ME, "All done, destroying ...");

         namedPool.destroy();
      } catch (Throwable e) {
         namedPool.destroy();
         Log.panic(ME, "ERROR: Test failed " + e.toString());
      }
   }
}

