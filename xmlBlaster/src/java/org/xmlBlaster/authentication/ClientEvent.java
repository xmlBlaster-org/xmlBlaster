/*------------------------------------------------------------------------------
Name:      ClientEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Transports SessionInfo or SubjectInfo
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.engine.qos.ConnectQosServer;


/**
 * An event which indicates that a client did a login or logout.
 * It carries the SessionInfo reference inside
 *
 * @author Marcel Ruff
 */
public class ClientEvent extends java.util.EventObject {
   
   ConnectQosServer connectQos;
   
   /**
    * Constructs a ClientEvent object.
    *
    * @param the client which does the login or logout
    */
   public ClientEvent(SessionInfo sessionInfo, ConnectQosServer connectQos          ) {
       super(sessionInfo);
       this.connectQos = connectQos;
   }

   public ClientEvent(SessionInfo sessionInfo) {
      this(sessionInfo, null);
   }

   /**
    * Returns the connectQos or null of the event.
    * @return the connectQos (could be null if not passed in the constructor)
    */
   public ConnectQosServer getConnectQos() {                
       return this.connectQos;
   }
   
   /**
    * Returns the originator of the event.
    *
    * @return the client which does the login or logout
    */
   public SessionInfo getSessionInfo() {
       return (SessionInfo)source;
   }

   /**
    * Constructs a ClientEvent object.
    *
    * @param the client which does the login or logout
    */
   public ClientEvent(SubjectInfo subjectInfo) {
       super(subjectInfo);
   }

   /**
    * Returns the originator of the event.
    *
    * @return the client which does the login or logout
    */
   public SubjectInfo getSubjectInfo() {
       return (SubjectInfo)source;
   }
}
