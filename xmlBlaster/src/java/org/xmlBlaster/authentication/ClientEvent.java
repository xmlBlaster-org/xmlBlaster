/*------------------------------------------------------------------------------
Name:      ClientEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Transports SessionInfo or SubjectInfo
Version:   $Id: ClientEvent.java,v 1.5 2002/03/13 16:41:07 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.authentication;

import org.xmlBlaster.authentication.SessionInfo;


/**
 * An event which indicates that a client did a login or logout.
 * It carries the SessionInfo reference inside
 *
 * @author Marcel Ruff
 */
public class ClientEvent extends java.util.EventObject
{
   /**
    * Constructs a ClientEvent object.
    *
    * @param the client which does the login or logout
    */
   public ClientEvent(SessionInfo sessionInfo)
   {
       super(sessionInfo);
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
   public ClientEvent(SubjectInfo subjectInfo)
   {
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
