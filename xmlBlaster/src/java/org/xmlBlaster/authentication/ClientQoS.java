/*-----------------------------------------------------------------------------
Name:      ClientQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling login QoS (quality of service), knows how to parse with SAX
Version:   $Id: ClientQoS.java,v 1.8 2000/09/15 17:16:13 ruff Exp $
Author:    ruff@swand.lake.de
-----------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;
import org.xml.sax.AttributeList;
import java.util.Vector;


/**
 * Handling of login() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the login() method<br />
 * They inform the server about client preferences and wishes
 */
public class ClientQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private static String ME = "ClientQoS";

   // helper flags for SAX parsing

   /** PtP messages wanted? */
   private boolean noPtP = false;    // <noPtP />    <!-- Don't send me any PtP messages (prevents spamming) -->

   /** Contains CallbackAddress objects */
   private boolean inCallback = false;
   private CallbackAddress tmpAddr = null;
   private CallbackAddress[] addressArr = null;
   private Vector addressVec = new Vector();  // <callback type="IOR">IOR:000122200...</callback>


   /**
    * Constructs the specialized quality of service object for a publish() call.
    */
   public ClientQoS(String xmlQoS_literal) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Creating ClientQoS(" + xmlQoS_literal + ")");
      addressArr = null;
      init(xmlQoS_literal);
   }


   /**
    * Does client wants to receive PtP messages?
    *
    * @return true/false
    */
   public final boolean wantsPtP()
   {
      return !noPtP;
   }


   /**
    * Accessing the Callback addresses of the client
    * this may be a CORBA-IOR or email or URL ...
    * <p />
    * @return An array of CallbackAddress objects, containing the address and the protocol type
    *         If no callback available, return an array of 0 length
    */
   public CallbackAddress[] getCallbackAddresses()
   {
      if (addressArr == null) {
         addressArr = new CallbackAddress[addressVec.size()];
         for (int ii=0; ii<addressArr.length; ii++)
            addressArr[ii] = (CallbackAddress)addressVec.elementAt(ii);
      }
      return addressArr;
   }


   public void setCallbackAddress(CallbackAddress addr)
   {
      addressVec.addElement(addr);
      addressArr = null; // reset to be recalculated on demand
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

      if (Log.TRACE) Log.trace(ME, "Entering startElement for " + name);

      if (!inQos) return;

      if (name.equalsIgnoreCase("noPtP"))
         noPtP = true;

      if (name.equalsIgnoreCase("callback")) {
         inCallback = true;
         String type = null;
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
                if( attrs.getName(i).equalsIgnoreCase("type") ) {
                  type = attrs.getValue(i).trim();
                  break;
                }
            }
         }
         if (type == null) {
            Log.error(ME, "Missing 'callback' attribute 'type' in login-qos");
            type = "IOR";
         }
         tmpAddr = new CallbackAddress(type);
         character.setLength(0);
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
      super.endElement(name);

      if (Log.TRACE) Log.trace(ME, "Entering endElement for " + name);

      if(name.equalsIgnoreCase("callback")) {
         inCallback = false;
         if (tmpAddr != null) {
            tmpAddr.setAddress(character.toString().trim());
            addressVec.addElement(tmpAddr);
         }
         character.setLength(0);
         return;
      }
   }


}
