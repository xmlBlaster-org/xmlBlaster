/*------------------------------------------------------------------------------
Name:      PublishQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: PublishQoS.java,v 1.10 2000/01/21 08:19:04 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;

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


   /**
    * Vector for loginQoS, holding all destination addresses
    */
   protected Vector destinationVec = null;


   /**
    * Constructs the specialized quality of service object for a publish() call.
    * @param the XML based ASCII string
    */
   public PublishQoS(String xmlQoS_literal) throws XmlBlasterException
   {
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
   public boolean isPubSubStyle()
   {
      return destinationVec == null;
   }


   /**
    * Test if Point to Point addressing style is used.
    *
    * @return true if addressing of the destination is used
    *         false if Publish/Subscribe style is used
    */
   public boolean isPTP_Style()
   {
      return !isPubSubStyle();
   }


   /**
    * @return true/false
    */
   public boolean usesXPathQuery()
   {
      return usesXPathQuery;
   }


   /**
    * @return true/false
    */
   public boolean isDurable()
   {
      return isDurable;
   }


   /**
    * @return true/false
    */
   public boolean forceUpdate()
   {
      return forceUpdate;
   }


   /**
    * @return true/false
    */
   public boolean readonly()
   {
      return readonly;
   }


   /**
    * Internal use only, is this message sent from the persistence layer?
    * @return true/false
    */
   public boolean fromPersistenceStore()
   {
      return fromPersistenceStore;
   }


   /**
    * Get all the destinations of this message.
    * This should only be used with PTP style messaging<br />
    * Check <code>if (isPTP_Style()) ...</code> before calling this method
    *
    * @return a valid Vector containing 0 - n Strings with destination names (loginName of clients)<br />
    *         null if Publish/Subscribe style is used
    */
   Vector getDestinations()
   {
      return destinationVec;
   }


   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String name, AttributeList attrs)
   {
      if (super.startElementBase(name, attrs) == true)
         return;

      if (name.equalsIgnoreCase("destination")) {
         if (!inQos)
            return;
         inDestination = true;
         if (destinationVec == null) destinationVec = new Vector();
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getName(i).equalsIgnoreCase("queryType") ) {
                  String queryType = attrs.getValue(i).trim();
                  if (queryType.equalsIgnoreCase("XPATH")) {
                     Log.error(ME, "Sorry, XPath destinations are not yet supported");
                     usesXPathQuery = false; // !!!
                  }
               }
            }
         }
         return;
      }

      if (name.equalsIgnoreCase("IsDurable")) {
         if (!inQos)
            return;
         isDurable = true;
         return;
      }

      if (name.equalsIgnoreCase("ForceUpdate")) {
         if (!inQos)
            return;
         forceUpdate = true;
         return;
      }

      if (name.equalsIgnoreCase("Readonly")) {
         if (!inQos)
            return;
         Log.error(ME, "Sorry, Readonly is not yet supported");
         readonly = false; // !!!
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
         String destination = character.toString().trim();
         destinationVec.addElement(destination);
         character.setLength(0);
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
         sb.append(offset + "   <IsDurable />");
      if (forceUpdate())
         sb.append(offset + "   <ForceUpdate />");
      if (readonly())
         sb.append(offset + "   <Readonly />");
      if (destinationVec == null) {
         sb.append(offset + "   <Pub_Sub_style />");
      }
      else {
         for (int ii=0; ii<destinationVec.size(); ii++) {
            sb.append(offset + "   <destination>");
            sb.append(offset + "      " + (String)destinationVec.elementAt(ii));
            sb.append(offset + "   </destination>");
         }
      }

      sb.append(offset + "</" + ME + ">\n");

      return sb;
   }
}
