/*------------------------------------------------------------------------------
Name:      GetQosWrapper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: GetQosWrapper.java,v 1.5 2002/05/06 07:33:53 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xmlBlaster.engine.mime.Query;

import java.util.Vector;


/**
 * This class encapsulates the qos of a get() message.
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>get()</b> qos could look like this:<br />
 * <pre>
 *     &lt;qos>
 *        &lt;meta>false<&lt;/meta>      &lt;!-- Don't send me the xmlKey meta data on updates -->
 *        &lt;content>false&lt;/content> &lt;!-- Don't send me the content data on updates (notify only) -->
 *     &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 */
public class GetQosWrapper extends QosWrapper
{
   private String ME = "GetQosWrapper";
   private boolean meta = true;
   private boolean content = true;
   /** Mime based filter rules */
   private Vector filterVec = null;


   /**
    * Default constructor for transient messages.
    */
   public GetQosWrapper()
   {
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
    * Mark a message to be updated even that the content didn't change.
    * <br />
    * Default is that xmlBlaster doesn't send messages to subscribed clients, if the message didn't change.
    */
   public void setNoContent()
   {
      this.content = false;
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
      sb.append("<qos>\n");
      if (!meta) sb.append("   <meta>false</meta>\n");
      if (!content) sb.append("   <content>false</content>\n");
      if (filterVec != null && filterVec.size() > 0) {
         for (int ii=0; ii<filterVec.size(); ii++) {
            AccessFilterQos filter = (AccessFilterQos)filterVec.elementAt(ii);
            sb.append(filter.toXml());
         }
      }
      sb.append("</qos>");
      return sb.toString();
   }

   /** For testing: java org.xmlBlaster.client.GetQosWrapper */
   public static void main(String[] args)
   {
      Global glob = new Global(args);
      try {
         GetQosWrapper qos = new GetQosWrapper();
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", new Query(glob, "800")));
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "3.2", new Query(glob, "a<10")));
         System.out.println(qos.toXml());
      }
      catch (Throwable e) {
         System.out.println("Test failed: " + e.toString());
      }
   }
}
