/*------------------------------------------------------------------------------
Name:      I_XmlBlasterConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_XmlBlasterConnection.java,v 1.1 2000/10/18 20:45:42 ruff Exp $
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
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public interface I_XmlBlasterConnection
{
   public void login(String loginName, String passwd, LoginQosWrapper qos, I_CallbackExtended client) throws XmlBlasterException, ConnectionException;

   public void loginRaw() throws XmlBlasterException, ConnectionException;

   public boolean logout();

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

