/*------------------------------------------------------------------------------
Name:      AuthenticateImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the xmlBlaster interface for xml-rpc.
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.xmlBlaster.util.Log;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.engine.xml2java.LoginReturnQoS;


/**
 * The methods of this class are callable bei XML-RPC clients.
 * <p />
 * void return is not allowed so we return an empty string instead
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
    * <p />
    * void return is not allowed so we return an empty string instead
    * <p>
    * @exception XmlBlasterException If client is unknown
    */
   public String logout(String sessionId) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering logout() ...");
      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      authenticateNative.logout(sessionId);
      if (Log.TIME) Log.time(ME, "Elapsed time in logout()" + stop.nice());
      return "";
   }


   /**
    * Login to xmlBlaster.
    * @parameter qos_literal See LoginQosWrapper.java
    * @return The xml string from LoginReturnQoS.java<br />
    * @see org.xmlBlaster.client.LoginQosWrapper
    * @see org.xmlBlaster.engine.xml2java.LoginReturnQoS
    */
   public String init(String qos_literal) throws XmlBlasterException
   {
      String returnValue = null;
      String sessionId = null;
      if (Log.CALL) Log.call(ME, "Entering init(qos=" + qos_literal + ")");

      StopWatch stop=null; if (Log.TIME) stop = new StopWatch();
      try {
         LoginReturnQoS qos = authenticateNative.init(qos_literal, sessionId);
         returnValue = qos.toXml();
         if (Log.TIME) Log.time(ME, "Elapsed time in login()" + stop.nice());
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.reason); // transform native exception to Corba exception
      }

      return returnValue;
   }

   public void disconnect(final String sessionId, String qos_literal) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering logout()");
      authenticateNative.disconnect(sessionId, qos_literal);
      if (Log.CALL) Log.call(ME, "Exiting logout()");
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

