/*------------------------------------------------------------------------------
Name:      ClientInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: ClientInfo.java,v 1.12 1999/12/01 17:26:00 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.authentication.AuthenticationInfo;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.clientIdl.BlasterCallback;
import java.util.List;


/**
 * ClientInfo stores all known data about a client.
 * <p>
 * It also contains a message queue, where messages are stored
 * until they are delivered at the next login of this client.
 *
 * @version $Revision: 1.12 $
 * @author $Author: ruff $
 */
public class ClientInfo
{
   private String ME = "ClientInfo";
   private AuthenticationInfo authInfo = null;

   /**
    * All MessageUnit which can't be delivered to the client (if he is not logged in)
    * are queued here and are delivered when the client comes on line.
    * <p>
    * Node objects = MessageUnit object
    */
   private List messageQueue = null;   // list = Collections.synchronizedList(new LinkedList());


   public ClientInfo(AuthenticationInfo authInfo)
   {
      this.authInfo = authInfo;
      if (Log.CALLS) Log.trace(ME, "Creating new ClientInfo " + authInfo.toString());
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
    * The unique login name.
    * <p />
    * @return the loginName
    */
   public final String toString()
   {
      return authInfo.toString();
   }


   /**
    * Accessing the CORBA Callback reference of the client in string notation.
    * <p />
    * @return BlasterCallback-IOR The CORBA callback reference in string notation
    */
   public String getCallbackIOR() throws XmlBlasterException
   {
      return authInfo.getCallbackIOR();
   }
}
