package org.xmlBlaster.contrib.replication;

public interface I_ReplSource {
   String getTopic();
   void initialUpdate(String replTopic, String replManagerAddress, String slaveName, String requestedVersion, String initialFilesLocation, boolean onlyRegister) throws Exception;
   void cancelUpdate(String slaveName);
   void recreateTriggers() throws Exception;
   byte[] executeStatement(String sql, long maxResponseEntries, boolean isHighPrio, boolean isMaster, String sqlTopic, String statementId) throws Exception;
   void startInitialUpdateBatch() throws Exception;
   void collectInitialUpdate() throws Exception;
   
}
