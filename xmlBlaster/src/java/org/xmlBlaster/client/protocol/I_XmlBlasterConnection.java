/*------------------------------------------------------------------------------
Name:      I_XmlBlasterConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_XmlBlasterConnection.java,v 1.4 2001/08/19 23:07:54 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.LoginQosWrapper;


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
    * init() is a login or authentication as well, the authentication schema
    * is transported in the qos.
    * It is more general then the login() method, since it allows
    * to transport any authentication info in the xml based qos.
    *
    * @param qos The authentication and other informations
    * @param client A handle to your callback if desired or null
    */
   public void init(LoginQosWrapper qos, I_CallbackExtended client) throws XmlBlasterException, ConnectionException;
   //public void disconnect(in string sessionId, in serverIdl::XmlType qos)

   public void login(String loginName, String passwd, LoginQosWrapper qos, I_CallbackExtended client) throws XmlBlasterException, ConnectionException;

   public void loginRaw() throws XmlBlasterException, ConnectionException;

   public boolean logout();

   public boolean shutdown();

   public void init();

   public String getLoginName();

   public boolean isLoggedIn();

   public void ping() throws ConnectionException;

   public String subscribe(String xmlKey_literal, String subscribeQoS_literal) throws XmlBlasterException, ConnectionException;

   public void unSubscribe(String xmlKey_literal, String unSubscribeQoS_literal) throws XmlBlasterException, ConnectionException;

   public String publish(MessageUnit msgUnit) throws XmlBlasterException, ConnectionException;

   public String[] publishArr(MessageUnit[] msgUnitArr) throws XmlBlasterException, ConnectionException;

   public String[] erase(String xmlKey_literal, String eraseQoS_literal) throws XmlBlasterException, ConnectionException;

   public MessageUnit[] get(String xmlKey_literal, String getQoS_literal) throws XmlBlasterException, ConnectionException;

   //public static void usage();
}

