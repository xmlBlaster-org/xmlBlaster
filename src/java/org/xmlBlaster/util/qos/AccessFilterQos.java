/*------------------------------------------------------------------------------
Name:      AccessFilterQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding filter address string and protocol string
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.engine.mime.Query;


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
public class AccessFilterQos extends QueryRefinementQos {

   /**
    */
   public AccessFilterQos(Global glob) {
      super(glob, "filter", "mime", "accessFilter.version");
      this.ME = "AccessFilterQos";
   }

   /**
    * @param glob The global handle holding environment and logging objects
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    * @param version The plugin version, defaults to "1.0"
    * @param query   Your filter rule
    */
   public AccessFilterQos(Global glob, String type, String version, String query) {
      super(glob, type, version, query, "filter", "mime", "accessFilter.version");
      this.ME = "AccessFilterQos";
   }

   /**
    * @param glob The global handle holding environment and logging objects
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    * @param version The plugin version, defaults to "1.0"
    * @param query   Your filter rule
    */
   public AccessFilterQos(Global glob, String type, String version, Query query) {
      super(glob, type, version, query, "filter", "mime", "accessFilter.version");
   }
   
   public String toString() {
	   return super.toString();
   }
}


