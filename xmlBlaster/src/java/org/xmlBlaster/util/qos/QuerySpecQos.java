/*------------------------------------------------------------------------------
Name:      QuerySpecQos.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding filter address string and protocol string
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.qos;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.engine.mime.Query;


/**
 * Helper class holding querySpec markup from a subscribe() or get() QoS. 
 * <p />
 * <pre>
 * &lt;querySpec type='QueueQuery' version='1.0'>
 *    800
 * &lt;/querySpec>
 * </pre>
 * This example addresses the plugin in xmlBlaster.properties file
 * <pre>
 *   QuerySpecPlugin[QueueQuery][1.0]=org.xmlBlaster.engine.query.demo.QuerySpec
 * </pre>
 *
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.queryspec.html">QuerySpec plugin framework</a>
 */
public class QuerySpecQos extends QueryRefinementQos {

   /**
    */
   public QuerySpecQos(Global glob) {
      super(glob, "querySpec", "query", "querySpec.version");
      this.ME = "QuerySpecQos";
   }

   /**
    * @param glob The global handle holding environment and logging objects
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    * @param version The plugin version, defaults to "1.0"
    * @param query   Your filter rule
    */
   public QuerySpecQos(Global glob, String type, String version, String query) {
      super(glob, type, version, query, "querySpec", "query", "querySpec.version");
      this.ME = "QuerySpecQos";
   }

   /**
    * @param glob The global handle holding environment and logging objects
    * @param type The plugin name, as used in xmlBlaster.properties e.g. "ContentLenFilter".
    * @param version The plugin version, defaults to "1.0"
    * @param query   Your filter rule
    */
   public QuerySpecQos(Global glob, String type, String version, Query query) {
      super(glob, type, version, query, "querySpec", "query", "querySpec.version");
   }
}


