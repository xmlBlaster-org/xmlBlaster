/*
 * -----------------------------------------------------------------------------
 * Name:      XmlDBAdapterWorker.java
 * Project:   xmlBlaster.org
 * Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 * Comment:   The thread that does the actual connection and interaction
 * Version:   $Id: XmlDBAdapterWorker.java,v 1.17 2002/05/01 21:40:13 ruff Exp $
 * ------------------------------------------------------------------------------
 */
package org.xmlBlaster.protocol.jdbc;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;

import java.sql.ResultSet;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * For every database access, an instance of this class does the work in a dedicated thread.
 */
public class XmlDBAdapterWorker extends Thread {

   private static final String   ME = "WorkerThread";
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
   public XmlDBAdapterWorker(String cust, byte[] content,
                             I_Publish callback, NamedConnectionPool namedPool) {
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
      XmlDBAdapter adap = new XmlDBAdapter(content, namedPool);
      MessageUnit[] msgArr = adap.query();
      try {
         if (msgArr.length > 0) {
            PublishKeyWrapper key = new PublishKeyWrapper("__sys_jdbc."+ME, "text/xml", "SQLQuery");
            PublishQosWrapper qos = new PublishQosWrapper(new Destination(cust));
            msgArr[0].xmlKey = key.toXml();
            msgArr[0].qos = qos.toXml();
            String  oid = callback.publish(msgArr[0]);
            if (Log.DUMP) Log.plain("Delivered Results...\n" + new String(content));
         }
         else
            if (Log.TRACE) Log.trace(ME, "No result message returned to client");
      }
      catch (XmlBlasterException e) {
         Log.error(ME, "Exception in notify: " + e.reason);
      }
      catch (Exception e) {
         e.printStackTrace();
         Log.error(ME, "Exception in notify: " + e);
      }
   }
}
