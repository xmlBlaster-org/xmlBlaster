/*------------------------------------------------------------------------------
Name:      DisconnectQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling one xmlQoS
Version:   $Id: DisconnectQos.java,v 1.8 2002/12/20 16:32:46 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xml.sax.Attributes;
import java.io.Serializable;


/**
 * This class encapsulates the qos of a logout() or disconnect()
 * <p />
 * So you don't need to type the 'ugly' XML ASCII string by yourself.
 * After construction access the ASCII-XML string with the toXml() method.
 * <br />
 * A typical <b>logout</b> qos could look like this:<br />
 * <pre>
 *  &lt;qos>
 *    &lt;deleteSubjectQueue>true&lt;/deleteSubjectQueue>
 *    &lt;clearSessions>false&lt;/clearSessions>
 *  &lt;/qos>
 * </pre>
 * <p />
 * see xmlBlaster/src/dtd/XmlQoS.xml
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">The interface.disconnect requirement</a>
 * @see org.xmlBlaster.test.classtest.DisconnectQosTest
 */
public class DisconnectQos extends org.xmlBlaster.util.XmlQoSBase implements Serializable
{
   private String ME = "DisconnectQos";
   private boolean deleteSubjectQueue = true;
   private boolean clearSessions = false;

   /**
    * Default constructor
    */
   public DisconnectQos()
   {
      super(Global.instance());
   }

   /**
    * Parses the given ASCII logout QoS. 
    */
   public DisconnectQos(String xmlQoS_literal) throws XmlBlasterException
   {
      super(Global.instance());
      init(xmlQoS_literal);
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
    * Return true if subject queue shall be deleted with last user session
    * @return true;
    */
   public boolean deleteSubjectQueue()
   {
      return deleteSubjectQueue;
   }

   /**
    * @param true if subject queue shall be deleted with last user session logout
    */
   public void deleteSubjectQueue(boolean del)
   {
      this.deleteSubjectQueue = del;
   }

   /**
    * Return true if we shall kill all other sessions of this user on logout (defaults to false). 
    * @return false
    */
   public boolean clearSessions()
   {
      return clearSessions;
   }

   /**
    * @param true if we shall kill all other sessions of this user on logout (defaults to false). 
    */
   public void clearSessions(boolean del)
   {
      this.clearSessions = del;
   }

   public void startElement(String uri, String localName, String name, Attributes attrs)
   {
      if (super.startElementBase(uri, localName, name, attrs) == true)
         return;

      if (name.equalsIgnoreCase("deleteSubjectQueue")) {
         deleteSubjectQueue = true;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("clearSessions")) {
         clearSessions = true;
         character.setLength(0);
         return;
      }
   }


   public void endElement(String uri, String localName, String name)
   {
      if (super.endElementBase(uri, localName, name) == true)
         return;

      if (name.equalsIgnoreCase("deleteSubjectQueue")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            deleteSubjectQueue(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase("clearSessions")) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            clearSessions(new Boolean(tmp).booleanValue());
         character.setLength(0);
         return;
      }
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * The default is to include the security string
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml()
   {
      return toXml("");
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<qos>");
      sb.append(offset).append("  <deleteSubjectQueue>").append(deleteSubjectQueue).append("</deleteSubjectQueue>");
      sb.append(offset).append("  <clearSessions>").append(clearSessions).append("</clearSessions>");
      sb.append(offset).append("</qos>");

      return sb.toString();
   }

   /** For testing: java org.xmlBlaster.util.DisconnectQos */
   public static void main(String[] args)
   {
      try {
         new org.xmlBlaster.util.Global(args);
         DisconnectQos qos = new DisconnectQos();
         qos.clearSessions(true);
         qos.deleteSubjectQueue(false);
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         System.err.println("TestFailed : " + e.toString());
      }
   }
}
