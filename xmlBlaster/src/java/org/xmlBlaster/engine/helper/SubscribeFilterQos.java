/*------------------------------------------------------------------------------
Name:      SubscribeFilterQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding filter address string and protocol string
Version:   $Id: SubscribeFilterQos.java,v 1.1 2002/03/15 07:57:19 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.xml2java;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xml.sax.Attributes;


/**
 * Helper class holding filter markup from a subscribe qos. 
 * <p />
 * <pre>
 * &lt;filter type='ContentLength' version='1.0'>
 *    800
 * &lt;/filter>
 * </pre>
 * This example addresses the plugin in xmlBlaster.properties file
 * <pre>
 *   SubscribeMimePlugin[ContentLenFilter][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter
 * </pre>
 */
public class SubscribeFilterQos
{
   private static final String ME = "SubscribeFilterQos";

   /** The filter rule */
   private String query;

   /** The plugin name e.g. "ContentLength" */
   private String type;
   
   /** The version of the plugin */
   public static final String DEFAULT_version = "1.0";
   private String version = XmlBlasterProperty.get("subscribeFilter.version", DEFAULT_version);

   /**
    */
   public SubscribeFilterQos()
   {
   }

   /**
    * @param type    The plugin name
    * @param version The plugin version
    */
   public SubscribeFilterQos(String type, String version)
   {
      setType(type);
      setVersion(version);
   }

   /**
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    */
   public final void setType(String type)
   {
      this.type = type;
   }

   /**
    * Returns the plugins name. 
    * @return e.g. "ContentLenFilter"
    */
   public final String getType()
   {
      return type;
   }

   /**
    * @param version The version of the plugin, defaults to "1.0", but can anything you like. 
    */
   public final void setVersion(String version)
   {
      this.version = version;
   }

   /**
    * Returns the plugins version. 
    * @return e.g. "1.0"
    */
   public final String getVersion()
   {
      return version;
   }

   /**
    * Set the filter query, it should fit to the protocol-type.
    *
    * @param query The filter query, e.g. "8000" for max length of a content with "ContentLenFilter" plugin
    */
   public final void setQuery(String query)
   {
      this.query = query;
   }

   /**
    * Returns the query, the syntax is depending on what your plugin supports.
    * @return e.g. "a>12 AND b<15"
    */
   public final String getQuery()
   {
      return query;
   }

   /**
    * Called for SAX filter start tag
    */
   public final void startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs)
   {
      String tmp = character.toString().trim(); // The query
      if (tmp.length() > 0) {
         setQuery(tmp);
      }
      character.setLength(0);

      if (name.equalsIgnoreCase("filter")) {
         if (attrs != null) {
            int len = attrs.getLength();
            for (int i = 0; i < len; i++) {
               if( attrs.getQName(i).equalsIgnoreCase("type") ) {
                  setType(attrs.getValue(i).trim());
               }
               else if( attrs.getQName(i).equalsIgnoreCase("version") ) {
                  setVersion(attrs.getValue(i).trim());
               }
               else {
                  Log.error(ME, "Ignoring unknown attribute " + attrs.getQName(i) + " in filter section.");
               }
            }
         }
         if (getType() == null) {
            Log.error(ME, "Missing 'filter' attribute 'type' in QoS, ignoring the filter request");
            setType(null);
         }
         return;
      }
   }


   /**
    * Handle SAX parsed end element
    */
   public final void endElement(String uri, String localName, String name, StringBuffer character)
   {
      if (name.equalsIgnoreCase("filter")) {
         String tmp = character.toString().trim(); // The query (if after inner tags)
         if (tmp.length() > 0)
            setQuery(tmp);
         else if (getQuery() == null)
            Log.error(ME, "filter QoS contains no query data");
      }
      character.setLength(0);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml()
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer(300);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<filter type='").append(getType()).append("'");
      if (!DEFAULT_version.equals(getVersion()))
          sb.append(" version='").append(getVersion()).append("'");
      sb.append(">");
      sb.append(offset).append("   ").append(getQuery());
      sb.append(offset).append("</filter>");

      return sb.toString();
   }
}


