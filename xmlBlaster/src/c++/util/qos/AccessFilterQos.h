/*------------------------------------------------------------------------------
Name:      AccessFilterQos.h
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding filter address std::string and protocol std::string
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

#ifndef _UTIL_QOS_ACCESSFILTERQOS_H
#define _UTIL_QOS_ACCESSFILTERQOS_H

#include <util/xmlBlasterDef.h>
#include <util/qos/Query.h>
#include <util/I_Log.h>
#include <util/Property.h>

#include <string>

namespace org { namespace xmlBlaster { namespace util { namespace qos {

extern Dll_Export const char* ACCESSFILTER_DEFAULT_version;
extern Dll_Export const char* ACCESSFILTER_DEFAULT_type;

class Dll_Export AccessFilterQos
{
   const std::string ME;
   org::xmlBlaster::util::Global&      global_;
   org::xmlBlaster::util::I_Log&         log_;

   /** The filter rule std::string and an object to hold the prepared query on demand  */
   Query query_;

   /** The plugin name e.g. "ContentLength" */
   std::string type_;
   
   /** The version of the plugin */
   std::string version_; // = DEFAULT_version;
   
   void copy(const AccessFilterQos& qos);

public:
   /**
    */
   AccessFilterQos(org::xmlBlaster::util::Global& global);

   /**
    * @param glob The global handle holding environment and logging objects
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    * @param version The plugin version, defaults to "1.0"
    * @param query   Your filter rule
    */
   AccessFilterQos(org::xmlBlaster::util::Global& global, const std::string& type, const std::string& version, const std::string& query);

   /**
    * @param glob The global handle holding environment and logging objects
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    * @param version The plugin version, defaults to "1.0"
    * @param query   Your filter rule
    */
   AccessFilterQos(org::xmlBlaster::util::Global& global, const std::string& type, const std::string& version, const Query& query);

   AccessFilterQos(const AccessFilterQos& qos);

   AccessFilterQos& operator =(const AccessFilterQos& qos);


   /**
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    */
   void setType(const std::string& type);

   /**
    * Returns the plugins name. 
    * @return e.g. "ContentLenFilter"
    */
   std::string getType() const;

   /**
    * @param version The version of the plugin, defaults to "1.0", but can anything you like. 
    */
   void setVersion(const std::string& version);

   /**
    * Returns the plugins version. 
    * @return e.g. "1.0"
    */
   std::string getVersion() const;

   /**
    * Set the filter query, it should fit to the protocol-type.
    *
    * @param query The filter query, e.g. "8000" for max length of a content with "ContentLenFilter" plugin
    */
   void setQuery(const Query& query);

   /**
    * Returns the query, the syntax is depending on what your plugin supports.
    * @return e.g. "a>12 AND b<15"
    */
   Query getQuery() const;

   /**
    * Dump state of this object into a XML ASCII std::string.
    * <br>
    * Only none default values are dumped for performance reasons
    * @param extraOffset indenting of tags for nice output
    * @return The xml representation
    */
   std::string toXml(const std::string& extraOffset="") const;
};

}}}}

#endif


