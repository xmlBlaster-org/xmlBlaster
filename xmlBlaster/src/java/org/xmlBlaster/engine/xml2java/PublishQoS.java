/*------------------------------------------------------------------------------
Name:      PublishQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: PublishQoS.java,v 1.17 2001/01/30 14:24:45 freidlin Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.Vector;
import java.io.*;


/**
 * Handling of publish() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the publish() method<br />
 * They are needed to control the xmlBlaster
 * <p />
 * Example:
 * <pre>
 *   &lt;qos> &lt;!-- PublishQoS -->
 *     &lt;isDurable />
 *     &lt;sender>
 *        Tim
 *     &lt;/sender>
 *  &lt;/qos>
 * </pre>
 */
public class PublishQoS extends org.xmlBlaster.util.XmlQoSBase implements Serializable
{
   private String ME = "PublishQoS";

   // helper flags for SAX parsing
   private boolean inDestination = false; // parsing inside <destination> ?
   private boolean inSender = false; // parsing inside <sender> ?
   private boolean inExpires = false; // parsing inside <expires> ?
   private boolean inErase = false; // parsing inside <erase> ?

   /** Internal use only, is this message sent from the persistence layer? */
   private boolean fromPersistenceStore = false;

   // flags for QoS state
   private boolean usesXPathQuery = false;
   private boolean isDurable = false;
   private boolean forceUpdate = false;
   private boolean readonly = false;
   private boolean forceQueuing = false;

   /** Expires after given milliseconds, clients will get a notify about expiration. Default is no expiration (similar to pass 0 milliseconds) */
   private long expires = 0L;
   /** Message is erased after given milliseconds, clients will get a notify about expiration. Default is no erasing (similar to pass 0 milliseconds) */
   private long erase = 0L;

   /** the sender (publisher) of this message (unique loginName) */
   private String sender = null;

   /**
    * Vector for loginQoS, holding all destination addresses (Destination objects)
    */
   protected Vector destinationVec = null;
   protected Destination destination = null;

   public long size = 0L;


   /**
    * Constructs the specialized quality of service object for a publish() call.
    * @param the XML based ASCII string
    */
   public PublishQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      // if (Log.TRACE) Log.trace(ME, "\n"+xmlQoS_literal);
         parseQos(xmlQoS_literal);
         size = xmlQoS_literal.length();
   }


   /**
    * Constructs the specialized quality of service object for a publish() call.
    * For internal use only, this message is sent from the persistence layer
    * @param the XML based ASCII string
    * @param true
    */
   public PublishQoS(String xmlQoS_literal, boolean fromPersistenceStore) throws XmlBlasterException
   {
      this.fromPersistenceStore = fromPersistenceStore;
      parseQos(xmlQoS_literal);
      size = xmlQoS_literal.length();
   }


   /**
    */
   private void parseQos(String xmlQoS_literal) throws XmlBlasterException
   {
      if (!isEmpty(xmlQoS_literal)) // if possible avoid expensive SAX parsing
         init(xmlQoS_literal);  // use SAX parser to parse it (is slow)
      else xmlLiteral = "<qos></qos>";
      size = xmlQoS_literal.length();
   }


   /**
    * Test if Publish/Subscribe style is used.
    *
    * @return true if Publish/Subscribe style is used
    *         false if addressing of the destination is used
    */
   public final boolean isPubSubStyle()
   {
      return destinationVec == null;
   }


   /**
    * Test if Point to Point addressing style is used.
    *
    * @return true if addressing of the destination is used
    *         false if Publish/Subscribe style is used
    */
   public final boolean isPTP_Style()
   {
      return !isPubSubStyle();
   }


   /**
    * @return true/false
    */
   public final boolean usesXPathQuery()
   {
      return usesXPathQuery;
   }


   /**
    * @return true/false
    */
   public final boolean isDurable()
   {
      return isDurable;
   }


   /**
    * @return true/false
    */
   public final boolean forceUpdate()
   {
      return forceUpdate;
   }


   /**
    * @return true/false
    */
   public final boolean readonly()
   {
      return readonly;
   }


   /**
    * Access sender name.
    * @return loginName of sender
    */
   public String getSender()
   {
      return sender;
   }


   /**
    * Access sender name.
    * @return loginName of sender
    */
   public void setSender(String sender)
   {
      this.sender = sender;
   }


   /**
    * Internal use only, is this message sent from the persistence layer?
    * @return true/false
    */
   public final boolean isFromPersistenceStore()
   {
      return fromPersistenceStore;
   }


   /**
    * Internal use only, set if this message sent from the persistence layer
    * @param true/false
    */
   public final void setFromPersistenceStore(boolean fromPersistenceStore)
   {
      this.fromPersistenceStore = fromPersistenceStore;
   }

   /**
    * @return Milliseconds until message will be automatically erased
    */
   public long getEraseTimeout()
   {
      return erase;
   }

   /**
    * @return Milliseconds until message expires
    */
   public long getExpires()
   {
      return expires;
   }

   /**
    * Get all the destinations of this message.
    * This should only be used with PTP style messaging<br />
    * Check <code>if (isPTP_Style()) ...</code> before calling this method
    *
    * @return a valid Vector containing 0 - n Strings with destination names (loginName of clients)<br />
    *         null if Publish/Subscribe style is used
    */
   public final Vector getDestinations()
   {
      return destinationVec;
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String name, AttributeList attrs)
   {
      if (super.startElementBase(name, attrs) == true)
         return;

      if (name.equalsIgnoreCase("destination")) {
         if (!inQos)
            return;
         inDestination = true;
         if (destinationVec == null) destinationVec = new Vector();
         destination = new Destination();
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getName(i).equalsIgnoreCase("queryType") ) {
                  String queryType = attrs.getValue(i).trim();
                  if (queryType.equalsIgnoreCase("EXACT")) {
                     destination.setQueryType(queryType);
                  }
                  else if (queryType.equalsIgnoreCase("XPATH")) {
                     destination.setQueryType(queryType);
                  }
                  else
                     Log.error(ME, "Sorry, destination queryType='" + queryType + "' is not supported");
               }
            }
         }
         String tmp = character.toString().trim(); // The address or XPath query string
         if (tmp.length() > 0) {
            destination.setDestination(tmp); // set address or XPath query string if it is before the ForceQueuing tag
            character.setLength(0);
         }
         return;
      }

      if (name.equalsIgnoreCase("sender")) {
         if (!inQos)
            return;
         inSender = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warn(ME, "Ignoring sent <sender> attribute " + attrs.getName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (Log.TRACE) Log.trace(ME, "Found sender tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("expires")) {
         if (!inQos)
            return;
         inExpires = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warn(ME, "Ignoring sent <expires> attribute " + attrs.getName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (Log.TRACE) Log.trace(ME, "Found expires tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("erase")) {
         if (!inQos)
            return;
         inErase = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warn(ME, "Ignoring sent <erase> attribute " + attrs.getName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (Log.TRACE) Log.trace(ME, "Found erase tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("ForceQueuing")) {
         if (!inDestination)
            return;
         forceQueuing = true;
         destination.forceQueuing(true);
         return;
      }

      if (name.equalsIgnoreCase("isDurable")) {
         if (!inQos)
            return;
         isDurable = true;
         return;
      }

      if (name.equalsIgnoreCase("forceUpdate")) {
         if (!inQos)
            return;
         forceUpdate = true;
         return;
      }

      if (name.equalsIgnoreCase("readonly")) {
         if (!inQos)
            return;
         readonly = true;
         return;
      }
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String name)
   {
      if (super.endElementBase(name) == true)
         return;

      if( name.equalsIgnoreCase("destination") ) {
         inDestination = false;
         String tmp = character.toString().trim(); // The address or XPath query string
         if (tmp.length() > 0) {
            destination.setDestination(tmp); // set address or XPath query string if it is before the ForceQueuing tag
            character.setLength(0);
         }
         destinationVec.addElement(destination);
         return;
      }

      if(name.equalsIgnoreCase("sender")) {
         inSender = false;
         sender = character.toString().trim();
         // if (Log.TRACE) Log.trace(ME, "Found message sender login name = " + sender);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("expires")) {
         inExpires = false;
         String tmp = character.toString().trim();
         try {
            expires = new Long(tmp).longValue();
         } catch (NumberFormatException e) {
            Log.error(ME, "Wrong format of <expires>" + tmp + "</expires>, expected a long in milliseconds.");
         }
         // if (Log.TRACE) Log.trace(ME, "Found message expires login name = " + expires);
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("erase")) {
         inErase = false;
         String tmp = character.toString().trim();
         try {
            erase = new Long(tmp).longValue();
         } catch (NumberFormatException e) {
            Log.error(ME, "Wrong format of <erase>" + tmp + "</erase>, expected a long in milliseconds.");
         }
         // if (Log.TRACE) Log.trace(ME, "Found message erase login name = " + erase);
         character.setLength(0);
         return;
      }

   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml()
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      // WARNING: This dump must be valid, as it is used by the
      //          persistent store
      sb.append(offset + "<qos> <!-- " + ME + " -->");

      if (destinationVec == null) {
         sb.append(offset + "   <Pub_Sub_style />");
      }
      else {
         for (int ii=0; ii<destinationVec.size(); ii++) {
            Destination destination = (Destination)destinationVec.elementAt(ii);
            sb.append(destination.toXml(extraOffset + "   "));
         }
      }
      if (sender != null) {
         sb.append(offset).append("   <sender>");
         sb.append(offset).append("      ").append(sender);
         sb.append(offset).append("   </sender>");
      }
      sb.append(offset).append("   <expires>").append(getExpires()).append("</expires>");
      sb.append(offset).append("   <erase>").append(getEraseTimeout()).append("</erase>");

      if (isDurable())
         sb.append(offset).append("   <isDurable />");
      if (forceUpdate())
         sb.append(offset).append("   <forceUpdate />");
      if (readonly())
         sb.append(offset).append("   <readonly />");

      sb.append(offset).append("</qos>\n");

      return sb.toString();
   }


   /** For testing: java org.xmlBlaster.engine.xml2java.PublishQoS */
   public static void main(String[] args)
   {
      try {
         XmlBlasterProperty.init(args);
         String xml =
            "<qos>\n" +
            "   <destination queryType='EXACT'>\n" +
            "      Tim\n" +
            "      <ForceQueuing />\n" +
            "   </destination>\n" +
            "   <destination queryType='EXACT'>\n" +
            "      <ForceQueuing timeout='12000' />\n" +
            "      Ben\n" +
            "   </destination>\n" +
            "   <destination queryType='XPATH'>\n" +
            "      //[GROUP='Manager']\n" +
            "   </destination>\n" +
            "   <destination queryType='XPATH'>\n" +
            "      //ROLE/[@id='Developer']\n" +
            "   </destination>\n" +
            "   <sender>\n" +
            "      Gesa\n" +
            "   </sender>\n" +
            "   <expires>\n" +
            "      12000\n" +
            "   </expires>\n" +
            "   <erase>\n" +
            "      24000\n" +
            "   </erase>\n" +
            "   <isDurable />\n" +
            "   <forceUpdate />\n" +
            "   <readonly />\n" +
            /*
            "   <defaultContent>\n" +
            "      Empty\n" +
            "   </defaultContent>\n" +
            */
            "</qos>\n";

         PublishQoS qos = new PublishQoS(xml);
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         Log.error("TestFailed", e.toString());
      }
   }
}
