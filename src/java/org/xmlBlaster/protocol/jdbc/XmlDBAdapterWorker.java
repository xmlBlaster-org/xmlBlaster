/*
 * -----------------------------------------------------------------------------
 * Name:      XmlDBAdapterWorker.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   The thread that does the actual connection and interaction
 * Version:   $Id$
 * ------------------------------------------------------------------------------
 */
package org.xmlBlaster.protocol.jdbc;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;

import com.sun.corba.se.impl.orbutil.closure.Constant;

/**
 * For every database access, an instance of this class does the work in a dedicated thread.
 */
public class XmlDBAdapterWorker extends Thread {

   private static final String   ME = "WorkerThread";
   private final Global          glob;
   private static Logger log = Logger.getLogger(XmlDBAdapterWorker.class.getName());
   private String                cust;
   private byte[]                content;
   private I_Publish             callback = null;
   private NamedConnectionPool   namedPool = null;

   /**
    * Create the worker instance to handle a single RDBMS request.
    * @param cust       The sender of the SQL message
    * @param content    The SQL statement
    * @param callback   Interface to publish the XML based result set
    * @param namedPool  A pool of JDBC connections for the RDBMS users
    */
   public XmlDBAdapterWorker(Global glob, String cust, byte[] content,
                             I_Publish callback, NamedConnectionPool namedPool) {
      this.glob = glob;

      this.cust = cust;
      this.content = content;
      this.callback = callback;
      this.namedPool = namedPool;
   }

   /**
    * Query the database.
    */
   public void run()
   {
      XmlDBAdapter adap = new XmlDBAdapter(glob, content, namedPool);
      MsgUnit[] msgArr = adap.query();
      try {
         if (msgArr == null || msgArr.length == 0) {
            if (log.isLoggable(Level.FINE)) log.fine("No result message returned to client");
            return;
         }
         PublishKey key = new PublishKey(glob, "__sys_jdbc."+ME, "text/xml", "SQLQuery");
         PublishQos qos = new PublishQos(glob, new Destination(new SessionName(glob, cust)));
         byte[] keyBytes = Constants.toUtf8Bytes(key.toXml());
         byte[] qosBytes = Constants.toUtf8Bytes(qos.toXml());
         
         for (int ii=0; ii<msgArr.length; ii++) {
            MsgUnitRaw msgUnitRaw = new MsgUnitRaw(msgArr[ii], keyBytes, msgArr[ii].getContent(), qosBytes);
            callback.publish(msgUnitRaw);
            if (log.isLoggable(Level.FINEST)) log.finest("Delivered Results...\n" + new String(content));
         }
      }
      catch (XmlBlasterException e) {
         log.severe("Exception in notify: " + e.getMessage());
      }
      catch (Exception e) {
         e.printStackTrace();
         log.severe("Exception in notify: " + e);
      }
   }
}
