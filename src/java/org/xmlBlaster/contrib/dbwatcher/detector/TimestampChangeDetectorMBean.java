/*------------------------------------------------------------------------------
Name:      TimestampChangeDetectorMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwatcher.detector;

/**
 * TimestampChangeDetectorMBean
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface TimestampChangeDetectorMBean {
   String getChangeCommand();
   void setChangeCommand(String changeCommand);
   String getChangeDetectStatement();
   void setChangeDetectStatement(String changeDetectStatement);
   String getGroupColName();
   void setGroupColName(String groupColName);
   boolean isIgnoreExistingDataOnStartup();
   String getNewTimestamp();
   String getOldTimestamp();
   void setOldTimestamp(String oldTimestamp);
   boolean isPoolOwner();
   void setPoolOwner(boolean poolOwner);
   String getQueryMeatStatement();
   void setQueryMeatStatement(String queryMeatStatement);
   boolean isTableExists();
   int getTimestampColNum();
   void setTimestampColNum(int timestampColNum);
   boolean isUseGroupCol();
   void setUseGroupCol(boolean useGroupCol);
}
