/*------------------------------------------------------------------------------
Name:      ClientInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: ClientInfo.java,v 1.10 1999/11/30 09:29:31 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.authentication.AuthenticationInfo;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.clientIdl.BlasterCallback;
import java.util.List;


/**
 * ClientInfo stores all known data about a client
 *
 * @version $Revision: 1.10 $
 * @author $Name:  $
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

   public final BlasterCallback getCB() throws XmlBlasterException
   {
      return authInfo.getCB();
   }


   /**
    * This is the unique identifier of the client
    * it is currently the byte[] oid from the POA active object map.
    * @return oid
    */
   public final String getUniqueKey() throws XmlBlasterException
   {
      return authInfo.getUniqueKey();
   }


   /**
    * @return the uniqueKey in hex notation for dumping it (readable form)
    */
   public final String getUniqueKeyHex() throws XmlBlasterException
   {
      return jacorb.poa.util.POAUtil.convert(getUniqueKey().getBytes(), true);
   }


   /**
    * @return the loginName
    */
   public final String toString()
   {
      return authInfo.toString();
   }


   public String getCallbackIOR() throws XmlBlasterException
   {
      return authInfo.getCallbackIOR();
   }
}
