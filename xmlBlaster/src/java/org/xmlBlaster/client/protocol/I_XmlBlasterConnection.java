/*------------------------------------------------------------------------------
Name:      I_XmlBlasterConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;


/**
 * This is the client interface to xmlBlaster.
 * <p />
 * All protocol drivers are accessed through these methods.
 * We need it to decouple the protocol specific stuff
 * (like RemoteException from RMI or CORBA exceptions) from
 * our java client code.
 * <p />
 * Note that you don't need this code, you can access xmlBlaster
 * with your own lowlevel RMI or CORBA coding as well.
 * <p />
 * If you are interested in a fail save client connection, consider
 * using XmlBlasterConnection.java which implements some nice features.
 *
 * @see org.xmlBlaster.client.protocol.XmlBlasterConnection
 *
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public interface I_XmlBlasterConnection
{
   /**
    * connect() is a login or authentication as well, the authentication schema
    * is transported in the qos.
    * It is more general then the login() method, since it allows
    * to transport any authentication info in the xml based qos.
    *
    * You can still use login() for simple name/password based authentication.
    *
    * @param qos The authentication and other informations
    * @param client A handle to your callback if desired or null
    * @return ConnectReturnQos
    */
   public ConnectReturnQos connect(ConnectQos qos) throws XmlBlasterException, ConnectionException;
   //public void disconnect(in string sessionId, in serverIdl::XmlType qos)

   // Could make sense to the SOCKET driver, returns new SocketCallbackImpl
   //public I_CallbackServer getCbServerInstance() throws XmlBlasterException;

   /**
    * @return The connection protocol name "IOR" or "RMI" etc.
    */
   public String getProtocol();

   public void login(String loginName, String passwd, ConnectQos qos) throws XmlBlasterException, ConnectionException;

   public ConnectReturnQos loginRaw() throws XmlBlasterException, ConnectionException;

   public boolean logout();

   public boolean shutdown();

   /** Reset the driver on problems */
   public void init();

   public String getLoginName();

   public boolean isLoggedIn();

   public String ping(String qos) throws XmlBlasterException, ConnectionException;

   public String subscribe(String xmlKey_literal, String subscribeQoS_literal) throws XmlBlasterException, ConnectionException;

   public void unSubscribe(String xmlKey_literal, String unSubscribeQoS_literal) throws XmlBlasterException, ConnectionException;

   public String publish(MessageUnit msgUnit) throws XmlBlasterException, ConnectionException;

   public String[] publishArr(MessageUnit[] msgUnitArr) throws XmlBlasterException, ConnectionException;

   public void publishOneway(MessageUnit[] msgUnitArr) throws XmlBlasterException, ConnectionException;

   public String[] erase(String xmlKey_literal, String eraseQoS_literal) throws XmlBlasterException, ConnectionException;

   public MessageUnit[] get(String xmlKey_literal, String getQoS_literal) throws XmlBlasterException, ConnectionException;

   //public static void usage();
}

