/*------------------------------------------------------------------------------
Name:      AccessFilterQos.cpp
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding filter address string and protocol string
------------------------------------------------------------------------------*/

/**
 * Helper class holding filter markup from a subscribe() or get() QoS. 
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

#include <util/qos/AccessFilterQos.h>
#include <util/Global.h>

using namespace std;
using namespace org::xmlBlaster::util;
using namespace org::xmlBlaster::util::qos;

namespace org { namespace xmlBlaster { namespace util { namespace qos {


Dll_Export const char* ACCESSFILTER_DEFAULT_version = "1.0";
Dll_Export const char* ACCESSFILTER_DEFAULT_type    = "";
  
   void AccessFilterQos::copy(const AccessFilterQos& qos) 
   {
      type_    = qos.type_;
      version_ = qos.version_;
   }

   /**
    */
   AccessFilterQos::AccessFilterQos(Global& global) 
      : ME("AccessFilterQos"), global_(global), log_(global.getLog("org.xmlBlaster.util.qos")), query_(global)
   {
      type_ = "";
      setVersion(global_.getProperty().getStringProperty("accessFilter.version", ACCESSFILTER_DEFAULT_version));
      setType(global_.getProperty().getStringProperty("accessFilter.type", ACCESSFILTER_DEFAULT_type));
    }

   /**
    * @param glob The global handle holding environment and logging objects
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    * @param version The plugin version, defaults to "1.0"
    * @param query   Your filter rule
    */
   AccessFilterQos::AccessFilterQos(Global& global, const string& type, const string& version, const string& query)
      : ME("AccessFilterQos"), global_(global), log_(global.getLog("org.xmlBlaster.util.qos")), query_(global, query)
   {
      setType(type);
      setVersion(version);
   }

   /**
    * @param glob The global handle holding environment and logging objects
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    * @param version The plugin version, defaults to "1.0"
    * @param query   Your filter rule
    */
   AccessFilterQos::AccessFilterQos(Global& global, const string& type, const string& version, const Query& query)
      : ME("AccessFilterQos"), global_(global), log_(global.getLog("org.xmlBlaster.util.qos")), query_(query)
   {
      setType(type);
      setVersion(version);
   }

   AccessFilterQos::AccessFilterQos(const AccessFilterQos& qos)
      : ME(qos.ME), global_(qos.global_), log_(qos.log_), query_(qos.query_)
   {
      copy(qos);
   }

   AccessFilterQos& AccessFilterQos::operator =(const AccessFilterQos& qos)
   {
      copy(qos);
      return *this;
   }

   /**
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    */
   void AccessFilterQos::setType(const string& type)
   {
      type_ = type;
   }

   /**
    * Returns the plugins name. 
    * @return e.g. "ContentLenFilter"
    */
   string AccessFilterQos::getType() const
   {
      return type_;
   }

   /**
    * @param version The version of the plugin, defaults to "1.0", but can anything you like. 
    */
   void AccessFilterQos::setVersion(const string& version)
   {
      version_ = version;
   }

   /**
    * Returns the plugins version. 
    * @return e.g. "1.0"
    */
   string AccessFilterQos::getVersion() const
   {
      return version_;
   }

   /**
    * Set the filter query, it should fit to the protocol-type.
    *
    * @param query The filter query, e.g. "8000" for max length of a content with "ContentLenFilter" plugin
    */
   void AccessFilterQos::setQuery(const Query& query)
   {
      query_ = query;
   }

   /**
    * Returns the query, the syntax is depending on what your plugin supports.
    * @return e.g. "a>12 AND b<15"
    */
   Query AccessFilterQos::getQuery() const
   {
      return query_;
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   string AccessFilterQos::toXml(const string& extraOffset) const
   {
      string ret;   
      string offset = "\n " + extraOffset;

      ret += offset + "<filter type='" + getType() + "'";
      if (ACCESSFILTER_DEFAULT_version != getVersion())
          ret += " version='" + getVersion() + "'";
      ret += ">";
      string help = getQuery().toString();
      if (help.find("<![CDATA[") != help.npos)
         ret += offset + " " + help;
      else
         ret += offset + " <![CDATA[" + help + "]]>";
      ret += offset + "</filter>";

      return ret;
   }

}}}}

