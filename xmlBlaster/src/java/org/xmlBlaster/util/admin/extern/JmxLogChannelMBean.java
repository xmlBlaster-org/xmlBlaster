/*------------------------------------------------------------------------------
Name:      JmxLogChannelMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.admin.extern;

import org.xmlBlaster.util.Global;

public interface JmxLogChannelMBean {
  public String getLogText();
  public void print();

  public void addErrorLevel();
  public void removeErrorLevel();

  public void addDumpLevel();
  public void removeDumpLevel();

  public void clearLocalLog();
  public void addGlobal(Global glob);

}