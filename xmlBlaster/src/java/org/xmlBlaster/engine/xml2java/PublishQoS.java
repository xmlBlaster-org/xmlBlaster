/*------------------------------------------------------------------------------
Name:      PublishQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: PublishQoS.java,v 1.5 2000/05/16 20:57:37 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Destination;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

import java.util.Vector;


/**
 * Handling of publish() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the publish() method<br />
 * They are needed to control the xmlBlaster
 */
public class PublishQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private String ME = "PublishQoS";

   // helper flags for SAX parsing
   private boolean inDestination = false; // parsing inside <destination> ?

   /** Internal use only, is this message sent from the persistence layer? */
   private boolean fromPersistenceStore = false;

   // flags for QoS state
   private boolean usesXPathQuery = false;
   private boolean isDurable = false;
   private boolean forceUpdate = false;
   private boolean readonly = false;
   private boolean forceQueuing = false;


   /**
    * Vector for loginQoS, holding all destination addresses (Destination objects)
    */
   protected Vector destinationVec = null;
   protected Destination destination = null;


   /**
    * Constructs the specialized quality of service object for a publish() call.
    * @param the XML based ASCII string
    */
   public PublishQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      // if (Log.TRACE) Log.trace(ME, "\n"+xmlQoS_literal);
      init(xmlQoS_literal);
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
      init(xmlQoS_literal);
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
    * Internal use only, is this message sent from the persistence layer?
    * @return true/false
    */
   public final boolean fromPersistenceStore()
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
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<" + ME + ">");

      if (isDurable())
         sb.append(offset + "   <isDurable />");
      if (forceUpdate())
         sb.append(offset + "   <forceUpdate />");
      if (readonly())
         sb.append(offset + "   <readonly />");
      if (destinationVec == null) {
         sb.append(offset + "   <Pub_Sub_style />");
      }
      else {
         for (int ii=0; ii<destinationVec.size(); ii++) {
            Destination destination = (Destination)destinationVec.elementAt(ii);
            sb.append(destination.printOn(extraOffset + "   ").toString());
         }
      }

      sb.append(offset + "</" + ME + ">\n");

      return sb;
   }
}
