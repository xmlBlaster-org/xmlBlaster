/**
 *
 */
package org.xmlBlaster.util.checkpoint;

import org.xmlBlaster.util.admin.I_AdminService;

/**
* @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
*/
public interface CheckpointMBean extends I_AdminService {

   /**
    * A comma separated list of checkpoints used
    * @return "publish.ack,update.ack,update.queue.add"
    */
   String getCheckpointList();

   /**
    * @return The plugin specific filter string
    */
   String getFilter();

   /**
    * The filter is used by the plugin to determine which message shall be logged
    * @param Set a filter string, the meaning is specific to the plugin used
    */
   void setFilter(String filter);

   /**
    * @return If set to true the getFilter() is ignored
    */
   boolean isShowAllMessages();

   /**
    * @param showAllMessages If set to true the getFilter() is ignored
    */
   void setShowAllMessages(boolean showAllMessages);

   /**
    * @return if true: <wfguid>4e5082125</wfguid>
    * if false: [wfguid=4e5082125]
    */
   boolean isXmlStyle();

   /**
    * @param xmlStyle the xmlStyle to set
    */
   void setXmlStyle(boolean xmlStyle);
}
