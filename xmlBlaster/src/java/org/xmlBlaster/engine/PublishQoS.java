/*------------------------------------------------------------------------------
Name:      PublishQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: PublishQoS.java,v 1.3 1999/12/09 13:28:36 ruff Exp $
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
    */
   public PublishQoS(String xmlQoS_literal) throws XmlBlasterException
   {
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
      super.startElement(name, attrs);

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
                  if (queryType.equalsIgnoreCase("IsDurable")) {
                     Log.error(ME, "Sorry, IsDurable is not yet supported");
                     isDurable = false; // !!!
                  }
                  if (queryType.equalsIgnoreCase("ForceUpdate")) {
                     Log.error(ME, "Sorry, ForceUpdate is not yet supported");
                     forceUpdate = false; // !!!
                  }
                  if (queryType.equalsIgnoreCase("Readonly")) {
                     Log.error(ME, "Sorry, Readonly is not yet supported");
                     readonly = false; // !!!
                  }
               }
            }
         }
         return;
      }
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String name) {
      super.endElement(name);

      if( name.equalsIgnoreCase("destination") ) {
         inDestination = false;
         String destination = character.toString().trim();
         destinationVec.addElement(destination);
         character = new StringBuffer();
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

      if (destinationVec == null) {
         sb.append(offset + "   <PUBLISH-SUBSCRIBE-STYLE />");
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
