/*------------------------------------------------------------------------------
Name:      NamedConnectionPool.java
Project:   xmlBlaster.org
Copyright: jutils.org, see jutils-LICENSE file
Comment:   Basic handling of a pool of limited resources
Version:   $Id: NamedConnectionPool.java,v 1.2 2000/07/07 11:31:14 ruff Exp $
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

import java.util.Hashtable;
import java.util.Enumeration;

import java.sql.Connection;
import java.sql.DriverManager;

/**
 * Load the drivers before using this pool, e.g.<br />
 * <pre>
 *    java -Djdbc.drivers=foo.bah.Driver:wombat.sql.Driver:bad.taste.ourDriver ...
 * </pre>
 * or in xmlBlaster.properties, e.g.<br />
 * <pre>
 *    JdbcDriver.drivers=oracle.jdbc.driver.OracleDriver,org.gjt.mm.mysql.Driver,postgresql.Driver,de.sag.jdbc.adabasd.ADriver,sun.jdbc.odbc.JdbcOdbcDriver,com.sybase.jdbc2.jdbc.SybDriver
 * </pre>
 */
public class NamedConnectionPool
{
   private static final String ME = "NamedConnectionPool";
   private Hashtable namedPools = new Hashtable();

   public NamedConnectionPool()
   {
   }

   /**
    * Use this method to get a JDBC connection.
    * You only need to pass the password the first time using the other resrve() method
    */
   Connection reserve(String dbUrl, String dbUser) throws XmlBlasterException
   {
      return reserve(dbUrl, dbUser, null);
   }

   /**
    * Use this method to get a JDBC connection.
    * <br />
    * The pooling properties are set to default values.
    * @param dbUrl For example "jdbc:oracle:thin:@localhost:1521:mydb
    */
   Connection reserve(String dbUrl, String dbUser, String dbPasswd) throws XmlBlasterException
   {
      long eraseUnusedPoolTimeout = XmlBlasterProperty.get("JdbcPool.eraseUnusedPoolTimeout", 60*60*1000L); // If a user disappers for one hour, delete his pool
      int maxInstances = XmlBlasterProperty.get("JdbcPool.maxInstances", 20);
      long busyToIdle = XmlBlasterProperty.get("JdbcPool.busyToIdleTimeout", 0); // How long may a query last
      long idleToErase = XmlBlasterProperty.get("JdbcPool.idleToEraseTimeout", 10*60*1000L); // How long does an unused connection survive (10 min)
      return reserve(dbUrl, dbUser, dbPasswd, eraseUnusedPoolTimeout, maxInstances, busyToIdle, idleToErase);
   }


   /**
    * Use this method to get a JDBC connection.
    * <br />
    * Usually only the first time for a user, to specify all parameters.
    * @param dbUrl For example "jdbc:oracle:thin:@localhost:1521:mydb
    * @param eraseUnusedPoolTimeout Remove pool of a user if not in use, in ms
    *          0 switches it off
    */
   Connection reserve(String dbUrl, String dbUser, String dbPasswd, long eraseUnusedPoolTimeout,
                      int maxInstances, long busyToIdle, long idleToErase) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Entering reserve '" + dbUrl + "', '" + dbUser + "'");
      try {
         UnnamedConnectionPool pool = getPool(dbUrl, dbUser);
         if (pool == null) {
            synchronized (this) {
               if (pool == null) {
                  if (dbPasswd == null) throw new XmlBlasterException(ME+".MissingPasswd", "Please give a password for '" + dbUser + "' when creating a JDBC pool");
                  pool = new UnnamedConnectionPool(this, dbUrl, dbUser, dbPasswd, eraseUnusedPoolTimeout, maxInstances, busyToIdle, idleToErase);
                  namedPools.put(getKey(dbUrl, dbUser), pool);
               }
            }
         }
         return pool.reserve();
      }
      catch(Exception e) {
         Log.error(ME, "System Exception in connect(" + dbUrl + ", " + dbUser + "): " + e.toString());
         throw new XmlBlasterException(ME, "Couldn't open database connection: " + e.toString());
      }
   }

   /**
    * Use this method to release a JDBC connection.
    */
   void release(String dbUrl, String dbUser, Connection con) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Entering release '" + dbUrl + "', '" + dbUser + "'");
      UnnamedConnectionPool pool = getPool(dbUrl, dbUser);
      if (pool != null)
         pool.release(con);
   }

   /**
    * Destroy the JDBC connection pool from a specific user.
    * The driver remains.
    * @param The UnnamedConnectionPool object
    */
   void destroy(String dbUrl, String dbUser) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Entering destroy() ...");
      try {
         String key = getKey(dbUrl, dbUser);
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


   /** Destroy the complete named pool of all users.  */
   void destroy()
   {
      for (Enumeration e = namedPools.elements() ; e.hasMoreElements() ;) {
         UnnamedConnectionPool pool = (UnnamedConnectionPool)e.nextElement();
         pool.destroy();
      }
      namedPools.clear();
   }

   private String getKey(String dbUrl, String dbUser)
   {
      StringBuffer buf = new StringBuffer(80);
      return buf.append(dbUrl).append("^").append(dbUser).toString();
   }
   private UnnamedConnectionPool getPool(String dbUrl, String dbUser)
   {
      String key = getKey(dbUrl, dbUser);
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
      private Long timeoutHandle = new Long(0L); // initialize for synchronized

      /**
       * @param boss           My manager
       * @param eraseUnusedPoolTimeout This pool is erased after given millis without activity of the owning user<br />
       *                       0 switches it off
       * @param maxInstances   Max. number of resources in this pool.
       * @param busyToIdleTimeout Max. busy time of this resource in milli seconds<br />
       *                       On timeout it changes state from 'busy' to 'idle'.<br />
       *                       You can overwrite this value for each resource instance<br />
       *                       0 switches it off<br />
       *                       You get called back through I_PoolManager.busyToIdle() on timeout
       *                       allowing you to code some specific handling.
       * @param idleToEraseTimeout Max. idle time span of this resource in milli seconds<br />
       *                     On timeout it changes state from 'idle' to 'undef' (it is deleted).<br />
       *                     You can overwrite this value for each resource instance<br />
       *                     0 switches it off<br />
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
         poolManager = new PoolManager(ME, this, maxInstances, busyToIdle, idleToErase);
         if (eraseUnusedPoolTimeout > 10L)
            synchronized(timeoutHandle) {
               timeoutHandle = Timeout.getInstance().addTimeoutListener(this, eraseUnusedPoolTimeout, "dummy");
            }
      }

      /** This callback does nothing */
      public void idleToBusy(Object resource) {
         if (Log.TRACE) Log.trace(ME, "Entering idleToBusy() ...");
         Connection con = (Connection)resource;
      }

      /** This callback does nothing */
      public void busyToIdle(Object resource) {
         if (Log.TRACE) Log.trace(ME, "Entering busyToIdle() ...");
         Connection con = (Connection)resource;
      }

      /**
       * Create a new JDBC connection, the driver must be registered already.
       * @param instanceId <db_url>^<username>/<passwd>, e.g.  "jdbc:oracle:thin:@localhost:1521:mydb^jack/secret"
       */
      public Object toCreate(String instanceId) throws JUtilsException {
         if (Log.TRACE) Log.trace(ME, "Entering toCreate() ...");
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
         if (Log.TRACE) Log.trace(ME, "Entering toErase() ...");
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
         try {
               if (eraseUnusedPoolTimeout > 10L) {
                  synchronized(timeoutHandle) {
                     timeoutHandle = Timeout.getInstance().refreshTimeoutListener(timeoutHandle, eraseUnusedPoolTimeout);
                  }
               }
               ResourceWrapper rw = (ResourceWrapper)poolManager.reserve(PoolManager.USE_HASH_CODE);
               Connection con = (Connection)rw.getResource();
               return con;
         }
         catch (JUtilsException e) {
            Log.error(ME, "Caught exception in reserve(): " + e.toString());
            throw new XmlBlasterException(e);
         }
      }

      /**
       * Use this method to release a JDBC connection.
       */
      void release(Connection con) throws XmlBlasterException {
         if (poolManager == null) { throw new XmlBlasterException(ME+".Destroyed", "Pool is destroyed"); }
         if (Log.TRACE) Log.trace(ME, "Entering release '" + dbUrl + "', '" + dbUser + "' hashCode=" + con.hashCode());
         try {
            poolManager.release(""+con.hashCode());
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
         if (poolManager.getNumBusy() != 0) {
            Log.warning(ME, "Can't destroy pool from '" + dbUrl + "', '" + dbUser + "', he seems to be busy working on his database.");
            synchronized(timeoutHandle) {
               timeoutHandle = Timeout.getInstance().addTimeoutListener(this, eraseUnusedPoolTimeout, "dummy");
            }
            return;
         }
         try {
            boss.destroy(dbUrl, dbUser);
         } catch(XmlBlasterException e) {
            Log.error(ME, "timeout: " + e.toString());
         }
      }

      /** Destroy the complete unnamed pool */
      void destroy() {
         if (poolManager != null) {
            poolManager.destroy();
            poolManager = null;
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

         NamedConnectionPool namedPool = new NamedConnectionPool();

         final long timeToDeath = 10*1000L;

         class Test extends Thread {
            String ME = "TestThread";
            NamedConnectionPool namedPool = null;
            private String user;
            private String pw;
            Test(String name, NamedConnectionPool namedPool, String user, String pw) {
               this.namedPool = namedPool;
               this.ME = name;
               this.user = user;
               this.pw = pw;
            }
            public void run() {
               try {
                  for (int ii=0; ii<5; ii++) {
                     Log.plain("run=" + ii + "\n");
                     org.jutils.time.StopWatch watch = new org.jutils.time.StopWatch();
                     Connection con = null;
                     con = namedPool.reserve(dbUrl, user, pw, timeToDeath, 10, 0L, 40*1000L);
                     int hashBefore = con.hashCode();
                     Log.info(ME, "Reserved connection hash=" + hashBefore + watch.toString() + "\n" + namedPool.toXml());
                     java.sql.Statement stmt = null;
                     java.sql.ResultSet rs = null;
                     try {
                        stmt = con.createStatement();
                        rs = stmt.executeQuery(dbStmt);
                     } finally {
                        if (rs!=null) rs.close();
                        if (stmt!=null) stmt.close();
                        if (hashBefore != con.hashCode())
                           Log.panic(ME, "Hash mismatch");
                        watch = new org.jutils.time.StopWatch();
                        if (con!=null) namedPool.release(dbUrl, dbUser, con);
                        Log.info(ME, "Query successful done, connection released" + watch.toString() + "\n" + namedPool.toXml());
                     }
                  }
                  try { Thread.currentThread().sleep(timeToDeath+1000L); } catch( InterruptedException i) {}
                  Log.info(ME, "After sleeping " + (timeToDeath+1000L) + " sec, erased connection\n" + namedPool.toXml());
                  //if (namedPool.poolManager.getNumBusy() != 0 || namedPool.poolManager.getNumIdle() != 0)
                  //   Log.panic(ME, "TEST FAILED: Wrong number of busy/idle resources");
               }
               catch(Throwable e) {
                  Log.panic(ME, "TEST FAILED");
               }
            }
         }

         for (int ii=0; ii<6; ii++) {
            String name = "TestThread-"+ii;
            if ((ii % 2) == 0) {
               Test p = new Test(name, namedPool, dbUser, dbPasswd);
               p.start();
            }
            else {
               Test p = new Test(name, namedPool, dbUser2, dbPasswd2);
               p.start();
            }
            Log.info(ME, "Started " + name + " ...");
         }
      } catch (Throwable e) {
         Log.panic(ME, "ERROR: Test failed " + e.toString());
      }
   }
}

