/*------------------------------------------------------------------------------
Name:      PluginConfigMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Access one plugin of the RunlevelManager
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;

import org.xmlBlaster.util.admin.I_AdminUsage;

/**
 * @author Marcel Ruff
 */
public interface PluginConfigMBean extends I_AdminUsage {
   public boolean isCreate();
   public void setCreate(boolean create);
}
