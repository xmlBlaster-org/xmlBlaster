/*------------------------------------------------------------------------------
Name:      SubscribeQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: SubscribeQosWrapper.java,v 1.14 2002/09/13 23:17:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import java.util.Vector;


/**
 * This class encapsulates the Message meta data and unique identifier (qos) of a subscribe() message.
 * <p />
 * A full specified <b>subscribe</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;meta>false&lt;/meta>      &lt;!-- Don't send me the xmlKey meta data on updates -->
 *        &lt;content>false&lt;/content> &lt;!-- Don't send me the content data on updates (notify only) -->
 *        &lt;local>false&lt;/local>     &lt;!-- Inhibit the delivery of messages to myself if i have published it -->
 *        &lt;initialUpdate>false&lt;/initialUpdate>;
 *        &lt;filter type='myPlugin' version='1.0'>a!=100&lt;/filter>  &lt;!-- Filters messages i have subscribed as implemented in your plugin -->
 *     &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class SubscribeQosWrapper extends QosWrapper
{
   private String ME = "SubscribeQosWrapper";

   /** not yet supported */
   private boolean meta = true;

   /** not yet supported */
   private boolean content = true;

   private boolean local = true;

   /** send on subscribe an initial update with the current message */
   private boolean initialUpdate = true;

   /** Mime based filter rules */
   private Vector filterVec = null;


   /**
    * Constructor for default qos (quality of service).
    */
   public SubscribeQosWrapper()
   {
   }


   /**
    * Constructor to receive notifies only (no data content will be delivered).
    * <p />
    * This may be useful if you have huge contents, and you only want to be informed about a change
    * @param content false - no data content is delivered
    */
   public SubscribeQosWrapper(boolean content)
   {
      this.content = content;
   }


   /**
    * Do we want to have an initial update on subscribe if the message
    * exists already?
    *
    * @return true if initial update wanted
    *         false if only updates on new publishes are sent
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.initialUpdate.html">The engine.qos.subscribe.initialUpdate requirement</a>
    */
   public void setInitialUpdate(boolean initialUpdate)
   {
      this.initialUpdate = initialUpdate;
   }

   /**
    * false Inhibit the delivery of messages to myself if i have published it.
    */
   public void setLocal(boolean local)
   {
      this.local = local;
   }

   /**
    * If false, the update contains not the content (it is a notify of change only)
    * TODO: Implement in server!!!
    */
   public void setContent(boolean content)
   {
      this.content = content;
   }

   /**
    * Adds your subplied subscribe filter
    */
   public void addAccessFilter(AccessFilterQos filter)
   {
      if (filterVec == null) filterVec = new Vector();
      this.filterVec.addElement(filter);
   }

   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toString()
   {
      return toXml();
   }


   /**
    * Converts the data into a valid XML ASCII string.
    * @return An XML ASCII string
    */
   public String toXml()
   {
      StringBuffer sb = new StringBuffer();
      sb.append("<qos>");
      if (!meta) sb.append("\n   <meta>false</meta>");
      if (!content) sb.append("\n   <content>false</content>");
      if (!local) sb.append("\n   <local>false</local>");
      if (!initialUpdate) sb.append("\n   <initialUpdate>false</initialUpdate>");
      if (filterVec != null && filterVec.size() > 0) {
         for (int ii=0; ii<filterVec.size(); ii++) {
            AccessFilterQos filter = (AccessFilterQos)filterVec.elementAt(ii);
            sb.append(filter.toXml());
         }
      }
      sb.append("\n</qos>");
      return sb.toString();
   }

   /** For testing: java org.xmlBlaster.client.SubscribeQosWrapper */
   public static void main(String[] args)
   {
      Global glob = new Global(args);
      try {
         SubscribeQosWrapper qos = new SubscribeQosWrapper();
         qos.setContent(false);
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", new Query(glob, "800")));
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "3.2", new Query(glob, "a<10")));
         System.out.println(qos.toXml());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
   }
}
