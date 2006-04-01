package org.xmlBlaster.authentication.plugins;

import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.qos.AddressServer;

/**
 * Container to transport information to the isAuthorized() method. 
 * @author xmlblast@marcelruff.info
 */
public class SessionHolder {
   private SessionInfo sessionInfo;
   private AddressServer addressServer;
   /**
    * @param sessionInfo
    * @param addressServer
    */
   public SessionHolder(SessionInfo sessionInfo, AddressServer addressServer) {
      super();
      this.sessionInfo = sessionInfo;
      this.addressServer = addressServer;
   }
   /**
    * @return Returns the addressServer.
    */
   public AddressServer getAddressServer() {
      return this.addressServer;
   }
   /**
    * @param addressServer The addressServer to set.
    */
   public void setAddressServer(AddressServer addressServer) {
      this.addressServer = addressServer;
   }
   /**
    * @return Returns the sessionInfo.
    */
   public SessionInfo getSessionInfo() {
      return this.sessionInfo;
   }
   /**
    * @param sessionInfo The sessionInfo to set.
    */
   public void setSessionInfo(SessionInfo sessionInfo) {
      this.sessionInfo = sessionInfo;
   } 
}
