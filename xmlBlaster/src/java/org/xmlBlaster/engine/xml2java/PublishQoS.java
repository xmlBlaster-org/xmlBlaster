/*------------------------------------------------------------------------------
Name:      PublishQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: PublishQoS.java,v 1.22 2001/12/23 19:52:23 ruff Exp $
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
 * They are needed to control xmlBlaster
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

   /**
    * A message lease lasts a maximum of one and a half day (36 hours). <p />
    * This value can be modified in XmlBlaster.properties:<br />
    * <code>Message.lease.maxTimeToLive=3600000 # One our lease</code><br />
    * Every message can set the timeToLive value between 1 and maxTimeToLive
    */
   private static long maxTimeToLive = XmlBlasterProperty.get("Message.lease.maxTimeToLive", 36L*60*60*1000);

   // helper flags for SAX parsing
   private boolean inDestination = false; // parsing inside <destination> ?
   private boolean inSender = false; // parsing inside <sender> ?
   private boolean inExpiration = false; // parsing inside <expiration> ?
   private boolean inIsVolatile = false; // parsing inside <isVolatile> ?

   /** Internal use only, is this message sent from the persistence layer? */
   private boolean fromPersistenceStore = false;

   // flags for QoS state
   private boolean usesXPathQuery = false;
   private boolean isVolatile = false;
   private boolean isDurable = false;
   private boolean forceUpdate = false;
   private boolean readonly = false;
   private boolean forceQueuing = false;

   /** 
    * The receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   private long rcvTimestamp;

   /** 
    * A message expires after some time and will be discarded.
    * Clients will get a notify about expiration.
    * This value is the elapsed milliseconds since UTC 1970 ...
    */
   private long expirationTimestamp = Long.MAX_VALUE;

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
      touchRcvTimestamp();
      setTimeToLive(getMaxTimeToLive());
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
      if (!fromPersistenceStore) {
         touchRcvTimestamp();
         setTimeToLive(getMaxTimeToLive());
      }
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
    * Mark a message to be volatile or not.
    * <br />
    * A non-volatile messages stays in memory as long as the server runs<br />
    * A volatile messages exists only during publish and processing it (doing the updates).<br />
    * Defaults to false.
    * @return true/false
    */
   public final boolean usesXPathQuery()
   {
      return usesXPathQuery;
   }


   /**
    * @return true/false
    */
   public final boolean isVolatile()
   {
      return isVolatile;
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
    * @return Milliseconds until message expiration (from now) or Long.MAX_VALUE if forever
    */
   public long getTimeToLive()
   {
      if (expirationTimestamp < Long.MAX_VALUE)
         return expirationTimestamp - System.currentTimeMillis();
      else
         return Long.MAX_VALUE;
   }

   public void setTimeToLive(long timeToLive)
   {
      if (timeToLive <= 0L) timeToLive = Long.MAX_VALUE; // Check parameter

      if (timeToLive <= getMaxTimeToLive())
         this.expirationTimestamp = rcvTimestamp + timeToLive;
      else {
         Log.warn(ME, "Ignoring timeToLive=" + timeToLive + ", setting maximum message lifespan to " + getMaxTimeToLive() + " millis");
         if (getMaxTimeToLive() == Long.MAX_VALUE)
            this.expirationTimestamp = Long.MAX_VALUE;
         else
            this.expirationTimestamp = rcvTimestamp + maxTimeToLive;
      }
   }

   public static long getMaxTimeToLive()
   {
      if (maxTimeToLive <= 0L) maxTimeToLive=Long.MAX_VALUE; // Correct value if strange initialized
      return maxTimeToLive;
   }

   /** 
    * The approximate receive timestamp (UTC time),
    * when message arrived in requestBroker.publish() method.<br />
    * In milliseconds elapsed since midnight, January 1, 1970 UTC
    */
   public long getRcvTimestamp()
   {
      return rcvTimestamp;
   }

   public void touchRcvTimestamp()
   {
      rcvTimestamp = System.currentTimeMillis();
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
   public final void startElement(String uri, String localName, String name, Attributes attrs)
   {
      if (super.startElementBase(uri, localName, name, attrs) == true)
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
               if( attrs.getQName(i).equalsIgnoreCase("queryType") ) {
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
               Log.warn(ME, "Ignoring sent <sender> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (Log.TRACE) Log.trace(ME, "Found sender tag");
         }
         return;
      }

      if (name.equalsIgnoreCase("expiration")) {
         if (!inQos)
            return;
         inExpiration = true;
         if (attrs != null) {
            int len = attrs.getLength();
            if (fromPersistenceStore) {  // First we need the rcvTimestamp:
               String tmp = attrs.getValue("rcvTimestamp");
               if (tmp != null) {
                  try { rcvTimestamp = Long.parseLong(tmp.trim()); } catch(NumberFormatException e) { Log.error(ME, "Invalid rcvTimestamp - millis =" + tmp); };
               }
               else {
                  Log.warn(ME, "QoS from persistent store misses rcvTimestamp attribute, setting current date");
                  touchRcvTimestamp();
               }
            }
            String tmp = attrs.getValue("timeToLive");
            if (tmp != null) {
               try { setTimeToLive(Long.parseLong(tmp.trim())); } catch(NumberFormatException e) { Log.error(ME, "Invalid timeToLive - millis =" + tmp); };
            }
            else {
               Log.warn(ME, "QoS <expiration> misses timeToLive attribute, setting default of " + getMaxTimeToLive());
               setTimeToLive(getMaxTimeToLive());
            }
            // if (Log.TRACE) Log.trace(ME, "Found expiration tag");
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

      if (name.equalsIgnoreCase("isVolatile")) {
         if (!inQos)
            return;
         inIsVolatile = true;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               Log.warn(ME, "Ignoring sent <isVolatile> attribute " + attrs.getQName(i) + "=" + attrs.getValue(i).trim());
            }
            // if (Log.TRACE) Log.trace(ME, "Found isVolatile tag");
         }
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
   public void endElement(String uri, String localName, String name)
   {
      if (super.endElementBase(uri, localName, name) == true)
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

      if(name.equalsIgnoreCase("expiration")) {
         inExpiration = false;
         character.setLength(0);
         return;
      }

      if(name.equalsIgnoreCase("isVolatile")) {
         inIsVolatile = false;
         String tmp = character.toString().trim();
         try {
            isVolatile = new Boolean(tmp).booleanValue();
         } catch (NumberFormatException e) {
            Log.error(ME, "Wrong format of <isVolatile>" + tmp + "</isVolatile>, expected true or false.");
         }
         // if (Log.TRACE) Log.trace(ME, "Found isVolatile = " + isVolatile);
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
      sb.append(offset).append("   <expiration rcvTimestamp='").append(rcvTimestamp).append("' timeToLive='").append(getTimeToLive()).append("'/>");

      sb.append(offset).append("   <isVolatile>").append(isVolatile).append("</isVolatile>");
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
            "   <expiration rcvTimestamp='1007771081626' timeToLive='12000'/>\n" +
            "   <isVolatile>false</isVolatile>\n" +
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
