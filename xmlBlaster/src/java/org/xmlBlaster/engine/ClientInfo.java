/*------------------------------------------------------------------------------
Name:      ClientInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: ClientInfo.java,v 1.14 1999/12/02 13:59:43 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.authentication.AuthenticationInfo;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.clientIdl.BlasterCallback;


/**
 * ClientInfo stores all known data about a client.
 * <p />
 * The driver supporting the desired Callback protocol (CORBA/EMAIL/HTTP)
 * is instantiated here.<br />
 * Note that only CORBA is supported in this version.<br />
 * To add a new driver protocol, you only need to implement the empty
 * CallbackEmailDriver.java or CallbackHttpDriver.java
 * <p />
 * It also contains a message queue, where messages are stored
 * until they are delivered at the next login of this client.
 *
 * @version $Revision: 1.14 $
 * @author $Author: ruff $
 */
public class ClientInfo
{
   private String ME = "ClientInfo";
   private String loginName = null;            // the unique client identifier
   private AuthenticationInfo authInfo = null; // all client informations
   private I_CallbackDriver myCallbackDriver = null;

   /**
    * All MessageUnit which can't be delivered to the client (if he is not logged in)
    * are queued here and are delivered when the client comes on line.
    * <p>
    * Node objects = MessageUnit object
    */
   private ClientUpdateQueue messageQueue = null;   // list = Collections.synchronizedList(new LinkedList());


   /**
    * Create this instance when a client did a login.
    * <p />
    * @param authInfo the AuthenticationInfo with the login informations for this client
    */
   public ClientInfo(AuthenticationInfo authInfo)
   {
      if (Log.CALLS) Log.trace(ME, "Creating new ClientInfo " + authInfo.toString());
      notifyAboutLogin(authInfo);
   }


   /**
    * Create this instance when a message is sent to this client, but he is not logged in
    * <p />
    * @param loginName The unique login name
    */
   public ClientInfo(String loginName)
   {
      if (Log.CALLS) Log.trace(ME, "Creating new empty ClientInfo for " + loginName);
   }


   /**
    * Accessing the CallbackDriver for this client, supporting the
    * desired protocol (CORBA, EMAIL, HTTP).
    *
    * @return the CallbackDriver for this client
    */
   public final I_CallbackDriver getCallbackDriver()
   {
      return myCallbackDriver;
   }


   /**
    * Is the client currently logged in? 
    * @return true yes
    *         false client is not on line
    */
   public boolean isLoggedIn()
   {
      return authInfo != null;
   }


   /**
    * Get notification that the client did a login.
    * <p />
    * This instance may exist before a login was done, for example
    * when some messages where directly addressed to this client.<br />
    * This notifies about a client login.
    *
    * @param authInfo the AuthenticationInfo with the login informations for this client
    */
   public final void notifyAboutLogin(AuthenticationInfo authInfo)
   {
      this.authInfo = authInfo;
      this.loginName = authInfo.getLoginName();

      if (authInfo.useCorbaCB())
         myCallbackDriver = CallbackCorbaDriver.getInstance();
      else if (authInfo.useEmailCB())
         myCallbackDriver = CallbackEmailDriver.getInstance();
      else if (authInfo.useHttpCB())
         myCallbackDriver = CallbackHttpDriver.getInstance();
   }


   /**
    * Get notification that the client did a login.
    * <p />
    * This instance may exist before a login was done, for example
    * when some messages where directly addressed to this client.<br />
    * This notifies about a client login.
    *
    * @param authInfo the AuthenticationInfo with the login informations for this client
    */
   public final void notifyAboutLogout()
   {
      this.authInfo = null;
      this.myCallbackDriver = null;
   }


   /**
    * Accessing the CORBA Callback reference of the client.
    * <p />
    * @return BlasterCallback reference <br />
    *         null if the client has no callback
    */
   public final BlasterCallback getCB() throws XmlBlasterException
   {
      return authInfo.getCB();
   }


   /**
    * This is the unique identifier of the client,
    * it is currently the byte[] oid from the POA active object map.
    * <p />
    * @return oid
    */
   public final String getUniqueKey() throws XmlBlasterException
   {
      return authInfo.getUniqueKey();
   }


   /**
    * The uniqueKey in hex notation.
    * <p />
    * @return the uniqueKey in hex notation for dumping it (readable form)
    */
   public final String getUniqueKeyHex() throws XmlBlasterException
   {
      return jacorb.poa.util.POAUtil.convert(getUniqueKey().getBytes(), true);
   }


   /**
    * Access the unique login name of a client.
    * @return loginName
    */
   public final String getLoginName()
   {
      return loginName;
   }


   /**
    * Accessing the AuthenticationInfo object
    * <p />
    * @return AuthenticationInfo
    */
   public final AuthenticationInfo getAuthenticationInfo() throws XmlBlasterException
   {
      return authInfo;
   }


   /**
    * The unique login name.
    * <p />
    * @return the loginName
    */
   public final String toString()
   {
      return loginName;
   }


   /**
    * Accessing the CORBA Callback reference of the client in string notation.
    * <p />
    * @return BlasterCallback-IOR The CORBA callback reference in string notation
    */
   public final String getCallbackIOR() throws XmlBlasterException
   {
      return authInfo.getCallbackIOR();
   }
}
