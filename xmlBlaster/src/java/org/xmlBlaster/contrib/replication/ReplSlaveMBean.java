/*------------------------------------------------------------------------------
Name:      ReplSlaveMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

public interface ReplSlaveMBean {
   String getTopic();
   long getMinReplKey();
   long getMaxReplKey();
   String getStatus();
   String getSqlResponse();
   void doContinue() throws Exception;
   void doPause() throws Exception;
   boolean toggleActive() throws Exception;
   void cancelInitialUpdate() throws Exception;
}