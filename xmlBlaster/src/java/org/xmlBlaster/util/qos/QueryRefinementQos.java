/*------------------------------------------------------------------------------
Name:      QueryRefinementQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding filter address string and protocol string
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.engine.mime.Query;
import org.xml.sax.Attributes;


/**
 * Base class till helper classes such as AccessFilterQos and QuerySpecQos. 
 * <p />
 * <pre>
 * &lt;filter type='ContentLength' version='1.0'>
 *    800
 * &lt;/filter>
 * </pre>
 * This example addresses the plugin in xmlBlaster.properties file
 * <pre>
 *   MimeAccessPlugin[ContentLenFilter][1.0]=org.xmlBlaster.engine.mime.demo.ContentLenFilter
 * </pre>
 * The filter rules apply for cluster configuration as well.
 *
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.accessfilter.html">MIME based access filter plugin framework</a>
 */
public abstract class QueryRefinementQos
{
   protected String ME = "QueryRefinementQos";
   protected final Global glob;
   protected final LogChannel log;

   /** The filter/query rule string and an object to hold the prepared query on demand  */
   protected Query query;

   /** The plugin name e.g. "ContentLength" */
   protected String type;
   
   /** The version of the plugin */
   public static final String DEFAULT_version = "1.0";
   private String version = DEFAULT_version;

   /** the tag name specific to the extending class */ 
   protected String tagName = "filter";
   
   /** the name to assign to the log of the extending class */ 
   protected String logName = "mime";

   /** the name of the property retrieving the version of the plugin of the extending class */ 
   protected String versionProp = "accessFilter.version";

   /**
    */
   protected QueryRefinementQos(Global glob, String tagName, String logName, String versionProp)
   {
      this.tagName = tagName;
      this.logName = logName;
      this.versionProp = versionProp;
      this.glob = glob;
      this.log = this.glob.getLog(this.logName);
      setVersion(glob.getProperty().get(this.versionProp, DEFAULT_version));
   }

   /**
    * @param glob The global handle holding environment and logging objects
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    * @param version The plugin version, defaults to "1.0"
    * @param query   Your filter rule
    */
   protected QueryRefinementQos(Global glob, String type, String version, String query, String tagName, String logName, String versionProp)
   {
      this.tagName = tagName;
      this.logName = logName;
      this.versionProp = versionProp;
      this.glob = glob;
      this.log = this.glob.getLog(this.logName);
      setType(type);
      setVersion(version);
      setQuery(new Query(glob, query));
   }

   /**
    * @param glob The global handle holding environment and logging objects
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    * @param version The plugin version, defaults to "1.0"
    * @param query   Your filter rule
    */
   protected QueryRefinementQos(Global glob, String type, String version, Query query, String tagName, String logName, String versionProp)
   {
      this.tagName = tagName;
      this.logName = logName;
      this.versionProp = versionProp;
      this.glob = glob;
      this.log = this.glob.getLog(this.logName);
      setType(type);
      setVersion(version);
      setQuery(query);
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
   public final void setQuery(Query query)
   {
      this.query = query;
   }

   /**
    * Returns the query, the syntax is depending on what your plugin supports.
    * @return e.g. "a>12 AND b<15"
    */
   public final Query getQuery()
   {
      return query;
   }

   /**
    * Called for SAX filter start tag
    * @return true if ok, false on error
    */
   public final boolean startElement(String uri, String localName, String name, StringBuffer character, Attributes attrs)
   {
      String tmp = character.toString().trim(); // The query
      if (tmp.length() > 0) {
         setQuery(new Query(glob, tmp));
      }
      character.setLength(0);

      if (name.equalsIgnoreCase(this.tagName)) {
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
                  log.warn(ME, "Ignoring unknown attribute " + attrs.getQName(i) + " in " + this.tagName + " section.");
               }
            }
         }
         if (getType() == null) {
            log.warn(ME, "Missing '" + this.tagName + "' attribute 'type' in QoS, ignoring the " + this.tagName + " request");
            setType(null);
            return false;
         }
         return true;
      }

      return false;
   }


   /**
    * Handle SAX parsed end element
    */
   public final void endElement(String uri, String localName, String name, StringBuffer character)
   {
      if (name.equalsIgnoreCase(this.tagName)) {
         String tmp = character.toString().trim();
         if (tmp.length() > 0)
            setQuery(new Query(glob, tmp));
         else if (getQuery() == null)
            log.error(ME, this.tagName + " QoS contains no query data");
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
      String offset = "\n ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<" + this.tagName + " type='").append(getType()).append("'");
      if (!DEFAULT_version.equals(getVersion()))
          sb.append(" version='").append(getVersion()).append("'");
      sb.append(">");
      if (getQuery().toString().indexOf("<![CDATA[") >= 0)
         sb.append(offset).append(" ").append(getQuery());
      else
         sb.append(offset).append(" <![CDATA[").append(getQuery()).append("]]>");
      sb.append(offset).append("</" + this.tagName + ">");

      return sb.toString();
   }
}


