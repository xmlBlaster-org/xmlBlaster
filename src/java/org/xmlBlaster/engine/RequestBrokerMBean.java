/*------------------------------------------------------------------------------
Name:      RequestBrokerMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;
import org.xmlBlaster.engine.admin.I_AdminNode;

/**
 * Export methods for JMX on cluster node level.
 *
 */

public interface RequestBrokerMBean extends I_AdminNode {
   String pingTimerInfo();
   String pingTimerDumpToFile(String fn);
}

