package org.xmlBlaster.contrib.replication;

/**
 * @author marcel
 */
public interface I_InitialUpdateListener {
   public static final String INITIAL_UPDATE_LISTENER_KEY = "_initialUpdate";
   void startInitialUpdate() throws Exception;
   void stopInitialUpdate() throws Exception;
}
