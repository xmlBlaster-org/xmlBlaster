/*------------------------------------------------------------------------------
Name:      GetQoS.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling QoS (quality of service), knows how to parse it with SAX
Version:   $Id: GetQoS.java,v 1.9 2002/05/16 23:31:58 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xml.sax.Attributes;
import java.util.Vector;


/**
 * Handling of get() quality of services.
 * <p />
 * QoS Informations sent from the client to the server via the get() method<br />
 * They are needed to control the xmlBlaster behavior
 */
public class GetQoS extends org.xmlBlaster.util.XmlQoSBase
{
   private static String ME = "GetQoS";
   private final Global glob;

   private transient AccessFilterQos tmpFilter = null;
   protected Vector filterVec = null;                         // To collect the filter when sax parsing
   protected transient AccessFilterQos[] filterArr = null; // To cache the filters in an array
   private transient boolean inFilter = false;

   /**
    * Constructs the specialized quality of service object for a publish() call.
    */
   public GetQoS(Global glob, String xmlQoS_literal) throws XmlBlasterException
   {
      this.glob = glob;
      if (Log.CALL) Log.call(ME, "Creating GetQoS(" + xmlQoS_literal + ")");
      init(xmlQoS_literal);
   }

   /**
    * Return the get filters or null if none is specified. 
    */
   public final AccessFilterQos[] getFilterQos()
   {
      if (filterArr != null || filterVec == null || filterVec.size() < 1)
         return filterArr;

      filterArr = new AccessFilterQos[filterVec.size()];
      filterVec.toArray(filterArr);
      return filterArr;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public void startElement(String uri, String localName, String name, Attributes attrs)
   {
      if (super.startElementBase(uri, localName, name, attrs) == true)
         return;

      if (Log.TRACE) Log.trace(ME, "Entering startElement for " + name);

      if (!inQos) return;

      if (name.equalsIgnoreCase("filter")) {
         inFilter = true;
         tmpFilter = new AccessFilterQos(glob);
         boolean ok = tmpFilter.startElement(uri, localName, name, character, attrs);
         if (ok) {
            if (filterVec == null) filterVec = new Vector();
            filterVec.addElement(tmpFilter);
         }
         else
            tmpFilter = null;
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

      if (Log.TRACE) Log.trace(ME, "Entering endElement for " + name);
      
      if (name.equalsIgnoreCase("filter")) {
         inFilter = false;
         if (tmpFilter != null)
            tmpFilter.endElement(uri, localName, name, character);
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
      StringBuffer sb = new StringBuffer(256);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<qos> <!-- GetQos -->");
      
      AccessFilterQos[] filterArr = getFilterQos();
      for (int ii=0; filterArr != null && ii<filterArr.length; ii++)
         sb.append(filterArr[ii].toXml(extraOffset+"   "));

      sb.append(offset).append("</qos>");

      return sb.toString();
   }

   /** For testing: java org.xmlBlaster.engine.xml2java.GetQoS */
   public static void main(String[] args)
   {
      try {
         GetQoS qos = null;
         String xml =
            "<qos>\n" +
            "   <meta>false</meta>\n" +
            "   <content>false</content>\n" +
            "   <local>false</local>\n" +
            "   <filter type='ContentLength' version='1.0'>\n" +
            "      8000\n" +
            "   </filter>\n" +
            "   <filter type='ContainsChecker' version='7.1' xy='true'>\n" +
            "      bug\n" +
            "   </filter>\n" +
            "   <filter>\n" +
            "      invalid filter without type\n" +
            "   </filter>\n" +
            "</qos>\n";
         System.out.println("=====Original XML========\n");
         System.out.println(xml);
         qos = new GetQoS(new Global(args), xml);
         System.out.println("=====Parsed and dumped===\n");
         System.out.println(qos.toXml());
      }
      catch(Throwable e) {
         e.printStackTrace();
         Log.error("TestFailed", e.toString());
      }
   }
}
