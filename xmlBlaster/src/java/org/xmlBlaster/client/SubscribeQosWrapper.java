/*------------------------------------------------------------------------------
Name:      SubscribeQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: SubscribeQosWrapper.java,v 1.8 2002/03/15 13:10:16 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.SubscribeFilterQos;
import java.util.Vector;


/**
 * This class encapsulates the Message meta data and unique identifier (qos) of a subscribe() message.
 * <p />
 * A full specified <b>subscribe</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;meta>false<&lt;/meta>      &lt;!-- Don't send me the xmlKey meta data on updates -->
 *        &lt;content>false&lt;/content> &lt;!-- Don't send me the content data on updates (notify only) -->
 *        &lt;local>false&lt;/local>     &lt;!-- Inhibit the delivery of messages to myself if i have published it -->
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
   public void addSubscribeFilter(SubscribeFilterQos filter)
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
      if (filterVec != null && filterVec.size() > 0) {
         for (int ii=0; ii<filterVec.size(); ii++) {
            SubscribeFilterQos filter = (SubscribeFilterQos)filterVec.elementAt(ii);
            sb.append(filter.toXml());
         }
      }
      sb.append("\n</qos>");
      return sb.toString();
   }

   /** For testing: java org.xmlBlaster.client.SubscribeQosWrapper */
   public static void main(String[] args)
   {
      try {
         SubscribeQosWrapper qos = new SubscribeQosWrapper();
         qos.setContent(false);
         qos.addSubscribeFilter(new SubscribeFilterQos("ContentLenFilter", "1.0", "800"));
         qos.addSubscribeFilter(new SubscribeFilterQos("ContentLenFilter", "3.2", "a<10"));
         System.out.println(qos.toXml());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
   }
}
