/*------------------------------------------------------------------------------
Name:      AuthenticateImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the xmlBlaster interface for xml-rpc.
Version:   $Id: AuthenticateImpl.java,v 1.2 2000/10/24 11:46:27 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.xmlBlaster.util.Log;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;


/**
 * The methods of this class are callable bei XML-RPC clients.
 * <p />
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public class AuthenticateImpl
{
   private final String ME = "XmlRpc.AuthenticateImpl";
   private Authenticate authenticateNative;


   /**
    * Constructor.
    */
   public AuthenticateImpl(Authenticate authenticateNative)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering constructor ...");
      this.authenticateNative = authenticateNative;
   }


   /**
    * Do login to xmlBlaster.
    * @see org.xmlBlaster.authentication.Authenticate.login()
    */
   public String login(String loginName, String passwd,
                       String xmlQoS_literal, String sessionId)
                          throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering login() ...");
      if (Log.DUMP) Log.dump(ME, xmlQoS_literal);

      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      String ret = authenticateNative.login(loginName, passwd, xmlQoS_literal, sessionId);
      if (Log.TIME) Log.time(ME, "Elapsed time in login()" + stop.nice());
      return ret;
   }


   /**
    * Logout of a client.
    * <p>
    * @exception XmlBlasterException If client is unknown
    */
   public void logout(String sessionId) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering logout() ...");
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      authenticateNative.logout(sessionId);
      if (Log.TIME) Log.time(ME, "Elapsed time in logout()" + stop.nice());
   }


   /**
    * Test the xml-rpc connection.
    * @return 1
    */
   public int ping() throws XmlBlasterException
   {
      return 1;
   }

   //   public String toXml() throws XmlBlasterException;
   /*
   public String toXml(String extraOffset) throws XmlBlasterException
   {
      return authenticateNative.toXml(extraOffset);
   }
   */
}

