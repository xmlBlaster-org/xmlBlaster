/*------------------------------------------------------------------------------
Name:      NamedConnectionPool.java
Project:   xmlBlaster.org
Copyright: jutils.org, see jutils-LICENSE file
Comment:   Basic handling of a pool of limited resources
Version:   $Id: NamedConnectionPool.java,v 1.1 2000/07/06 16:32:07 ruff Exp $
           $Source: /opt/cvsroot/xmlBlaster/src/java/org/xmlBlaster/protocol/jdbc/NamedConnectionPool.java,v $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.XmlBlasterException;

import org.jutils.JUtilsException;
import org.jutils.log.Log;
import org.jutils.init.Args;
import org.jutils.pool.PoolManager;
import org.jutils.pool.I_PoolManager;
import org.jutils.pool.ResourceWrapper;

import java.sql.Connection;
import java.sql.DriverManager;

public class NamedConnectionPool implements I_PoolManager
{
   private static final String ME = "NamedConnectionPool";
   PoolManager poolManager;

   /**
    * @param poolName       A nice name for this pool manager instance.
    * @param callback       The interface 'I_PoolManager' callback
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
   public NamedConnectionPool(int maxInstances, long busyToIdle, long idleToErase)
   {
      poolManager = new PoolManager(ME, this, maxInstances, busyToIdle, idleToErase);
   }

   /** This callback does nothing */
   public void idleToBusy(Object resource) {
      if (Log.TRACE) Log.trace(ME, "Entering idleToBusy() ...");
      UnnamedConnectionPool conPool = (UnnamedConnectionPool)resource;
   }

   /** This callback does nothing */
   public void busyToIdle(Object resource) {
      if (Log.TRACE) Log.trace(ME, "Entering busyToIdle() ...");
      UnnamedConnectionPool conPool = (UnnamedConnectionPool)resource;
   }

   /**
    * Create a new JDBC connection, the driver must be registered already.
    * @param instanceId <db_url>^<username>/<passwd>, e.g.  "jdbc:oracle:thin:@localhost:1521:mydb^jack/secret"
    */
   public void toCreate(PoolManager.CreateHelper helper) throws JUtilsException {
      if (Log.TRACE) Log.trace(ME, "Entering toCreate() ...");
      String instanceId = helper.instanceId;
      int caretIndex = instanceId.indexOf('^');
      int slashIndex = instanceId.indexOf('/', caretIndex);
      String dbUrl = instanceId.substring(0, caretIndex);
      String dbUser = instanceId.substring(caretIndex+1, slashIndex);
      String dbPasswd = instanceId.substring(slashIndex+1);
      try {
         if (Log.TRACE) Log.trace(ME, "JDBC connect to '" + dbUrl + "', '" + dbUser + "'");
         helper.resource = new UnnamedConnectionPool(dbUrl, dbUser, dbPasswd, 2000, 60*60*1000, 2000); // !!!! parameterize
      }
      catch(Exception e) {
         Log.error(ME, "System Exception in connect(" + dbUrl + ", " + dbUser + "): " + e.toString());
         throw new JUtilsException(ME, "Couldn't open database connection: " + e.toString());
      }
   }

   /**
    * Destroy the JDBC connection pool from a specific user. 
    * The driver remains.
    * @param The UnnamedConnectionPool object
    */
   public void toErased(Object resource) {
      if (Log.TRACE) Log.trace(ME, "Entering toErase() ...");
      UnnamedConnectionPool conPool = (UnnamedConnectionPool)resource;
      try {
         conPool.destroy();
         if (Log.TRACE) Log.trace(ME, "JDBC connection closed");
      }
      catch (Exception e) {
         Log.error(ME, "System Exception in close JDBC connection: " + e.toString());
      }
   }

   /**
    * Use this method to get a JDBC connection. 
    */
   Connection reserve(String dbUrl, String dbUser, String dbPasswd) throws XmlBlasterException {
      if (Log.TRACE) Log.trace(ME, "Entering reserve '" + dbUrl + "', '" + dbUser + "'");
      StringBuffer buf = new StringBuffer(80);
      String instanceId = buf.append(dbUrl).append("^").append(dbUser).append("/").append(dbPasswd).toString();
      try {
         ResourceWrapper rw = (ResourceWrapper)poolManager.reserve(instanceId);
         UnnamedConnectionPool conPool = (UnnamedConnectionPool)rw.getResource();
         return conPool.reserve();
      }
      catch (JUtilsException e) {
         Log.error(ME, "Caught exception in reserve(): " + e.toString());
         throw new XmlBlasterException(e);
      }
   }

   /**
    * Use this method to release a JDBC connection. 
    */
   void release(String dbUrl, String dbUser, String dbPasswd) throws XmlBlasterException {
      if (Log.TRACE) Log.trace(ME, "Entering release '" + dbUrl + "', '" + dbUser + "'");
      StringBuffer buf = new StringBuffer(80);
      String instanceId = buf.append(dbUrl).append("^").append(dbUser).append("/").append(dbPasswd).toString();
      try {
         poolManager.release(instanceId);
      }
      catch (JUtilsException e) {
         Log.error(ME, "Caught exception in release(): " + e.toString());
         throw new XmlBlasterException(e);
      }
   }

   /** Destroy the complete named pool */
   void destroy() {
      if (poolManager != null) {
         poolManager.destroy();
         poolManager = null;
      }
   }



   /**
    * Inner class, every user of the Named pool has its own connection pool. 
    */
   private class UnnamedConnectionPool implements I_PoolManager
   {
      private static final String ME = "UnnamedConnectionPool";
      PoolManager poolManager = null;
      private String dbUrl;
      private String dbUser;
      private String dbPasswd;

      /**
       * @param poolName       A nice name for this pool manager instance.
       * @param callback       The interface 'I_PoolManager' callback
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
      public UnnamedConnectionPool(String dbUrl, String dbUser, String dbPasswd,
                                   int maxInstances, long busyToIdle, long idleToErase)
      {
         this.dbUrl = dbUrl;
         this.dbUser = dbUser;
         this.dbPasswd = dbPasswd;
         poolManager = new PoolManager(ME, this, maxInstances, busyToIdle, idleToErase);
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
      public void toCreate(PoolManager.CreateHelper helper) throws JUtilsException {
         if (Log.TRACE) Log.trace(ME, "Entering toCreate() ...");
         try {
            Connection con = DriverManager.getConnection (dbUrl, dbUser, dbPasswd);
            helper.resource = con;
            helper.instanceId = ""+con.hashCode();
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
            ResourceWrapper rw = (ResourceWrapper)poolManager.reserve();
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
         if (Log.TRACE) Log.trace(ME, "Entering release '" + dbUrl + "', '" + dbUser + "'");
         try {
            poolManager.release(""+con.hashCode());
         }
         catch (JUtilsException e) {
            Log.error(ME, "Caught exception in release(): " + e.toString());
            throw new XmlBlasterException(e);
         }
      }

      /** Destroy the complete unnamed pool */
      void destroy() {
         if (poolManager != null) {
            poolManager.destroy();
            poolManager = null;
         }
      }
   }


   /**
    * For testing only.
    * <p />
    * Invoke:                 
java org.xmlBlaster.protocol.jdbc.NamedConnectionPool -trace true
    */
   public static void main(String[] args) {
      try {
         Log.setLogLevel(args); // initialize log level

         String ME = "TestConnection";
         String dbDriver = Args.getArg(args, "-dbDriver", "oracle.jdbc.driver.OracleDriver");
         final String dbUrl = Args.getArg(args, "-dbUrl", "jdbc:oracle:thin:@localhost:1521:MARCEL");
         final String dbUser = Args.getArg(args, "-dbUser", "mrf");
         final String dbPasswd = Args.getArg(args, "-dbPasswd", "mrf");
         final String dbStmt = Args.getArg(args, "-dbStmt", "select * from user_tables");

         Class cl = Class.forName(dbDriver);
         java.sql.Driver dr = (java.sql.Driver)cl.newInstance();
         java.sql.DriverManager.registerDriver(dr);
         Log.info(ME, "Jdbc driver '" + dbDriver + "' loaded.");
         
         NamedConnectionPool namedPool = new NamedConnectionPool(2000, 60*60*1000, 2000);

         class Test extends Thread {
            String ME = "TestThread";
            NamedConnectionPool namedPool = null;
            Test(String name, NamedConnectionPool namedPool) {
               this.namedPool = namedPool;
               this.ME = name;
            }
            public void run() {
               try {
                  for (int ii=0; ii<5; ii++) {
                     Log.plain("run=" + ii + "\n");
                     org.jutils.time.StopWatch watch = new org.jutils.time.StopWatch();
                     Connection con = namedPool.reserve(dbUrl, dbUser, dbPasswd);
                     Log.info(ME, "Reserved connection\n" + namedPool.poolManager.toXml());
                     java.sql.Statement stmt = null;
                     java.sql.ResultSet rs = null;
                     try {
                        stmt = con.createStatement();
                        rs = stmt.executeQuery(dbStmt);
                        Log.info(ME, "Query success");
                     } finally {
                        if (rs!=null) rs.close();
                        if (stmt!=null) stmt.close();
                        if (con!=null) namedPool.release(dbUrl, dbUser, dbPasswd);
                        Log.info(ME, "Closed and released" + watch.toString() + "\n" + namedPool.poolManager.toXml());
                     }
                     Log.info(ME, namedPool.poolManager.toXml());
                  }
                  try { Thread.currentThread().sleep(3000); } catch( InterruptedException i) {}
                  Log.info(ME, "Erased connection\n" + namedPool.poolManager.toXml());
                  if (namedPool.poolManager.getNumBusy() != 0 || namedPool.poolManager.getNumIdle() != 0)
                     Log.panic(ME, "TEST FAILED: Wrong number of busy/idle resources");
               }
               catch(Throwable e) {
                  Log.panic(ME, "TEST FAILED");
               }
            }
         }

         for (int ii=0; ii<5; ii++) {
            String name = "TestThread-"+ii;
            Test p = new Test(name, namedPool);
            p.start();
            Log.info(ME, "Started " + name + " ...");
         }
      } catch (Throwable e) {
         Log.panic(ME, "ERROR: Test failed " + e.toString());
      }
   }
}

