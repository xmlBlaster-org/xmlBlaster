/*------------------------------------------------------------------------------
Name:      AuthenticateImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementing the xmlBlaster interface for xml-rpc.
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.soap;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.util.DisconnectQos;
import org.jutils.text.StringHelper;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;

/**
 * The methods of this class are callable bei SOAP clients.
 * <p />
 * void return is not allowed so we return an empty string instead
 * <p />
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class AuthenticateImpl
{
   private final String ME = "SOAP.AuthenticateImpl";
   private final Global glob;
   private LogChannel log;
   private final I_Authenticate authenticate;


   public AuthenticateImpl() {
      this(Global.instance(), (I_Authenticate)null);
   }

   /**
    * Constructor.
    */
   public AuthenticateImpl(Global glob, I_Authenticate authenticate) {
      this.glob = glob;
      this.log = glob.getLog("soap");
      if (log.CALL) log.call(ME, "Entering constructor ...");
      this.authenticate = authenticate;
   }

   /**
    * Login to xmlBlaster.
    * @param qos_literal See ConnectQos.java
    * @return The xml string from ConnectReturnQos.java<br />
    * @see org.xmlBlaster.util.ConnectQos
    * @see org.xmlBlaster.util.ConnectReturnQos
    */
   public String connect(String qos_literal) throws XmlBlasterException {
      String returnValue = null, returnValueStripped = null;
      if (log.CALL) log.call(ME, "Entering connect(qos=" + qos_literal + ")");

      StopWatch stop=null; if (log.TIME) stop = new StopWatch();
      try {
         ConnectQos connectQos = new ConnectQos(glob, qos_literal);
         if (authenticate != null) {
            ConnectReturnQos returnQos = authenticate.connect(connectQos);
            returnValue = returnQos.toXml();
            returnValueStripped = StringHelper.replaceAll(returnValue, "<![CDATA[", "");
            returnValueStripped = StringHelper.replaceAll(returnValueStripped, "]]>", "");
            if (!returnValueStripped.equals(returnValue)) {
               log.trace(ME, "Stripped CDATA tags surrounding security credentials, SOAP does not like it (Helma does not escape ']]>'). " +
                              "This shouldn't be a problem as long as your credentials doesn't contain '<'");
            }
         }
         else {
            returnValueStripped = "<qos>Simulated connect</qos>";
            log.warn(ME, "Simulating connect only");
         }

         if (log.TIME) log.time(ME, "Elapsed time in connect()" + stop.nice());
      }
      catch (org.xmlBlaster.util.XmlBlasterException e) {
         throw new XmlBlasterException(e.id, e.getMessage()); // transform native exception to Corba exception
      }

      return returnValueStripped;
   }

   public void disconnect(final String sessionId, String qos_literal) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering disconnect()");
      if (authenticate != null) 
         authenticate.disconnect(sessionId, qos_literal);
      else
         log.warn(ME, "Simulating disconnect only");
      if (log.CALL) log.call(ME, "Exiting disconnect()");
   }

   /**
    * Test the xml-rpc connection.
    * @return ""
    */
   public String ping(String qos)
   {
      log.info(ME, "Entering ping");
      return "";
   }

   //   public String toXml() throws XmlBlasterException;
   /*
   public String toXml(String extraOffset) throws XmlBlasterException
   {
      return authenticate.toXml(extraOffset);
   }
   */
}

