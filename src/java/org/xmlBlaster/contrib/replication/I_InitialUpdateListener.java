package org.xmlBlaster.contrib.replication;

public interface I_InitialUpdateListener {
   
   final static String INITIAL_UPDATE_LISTENER_KEY = "_replication.initialUpdateListener";
   
   void startInitialUpdate() throws Exception;
   void stopInitialUpdate() throws Exception;
   
}
