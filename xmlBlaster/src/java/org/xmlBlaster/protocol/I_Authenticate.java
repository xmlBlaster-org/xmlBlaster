/*------------------------------------------------------------------------------
Name:      I_Authenticate.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Native Interface to xmlBlaster
Version:   $Id: I_Authenticate.java,v 1.13 2003/10/05 17:33:31 ruff Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.engine.admin.I_AdminSubject;
import org.xmlBlaster.authentication.SessionInfo;

/**
 * This is the native interface to xmlBlaster-authentication.
 * <p />
 * All login/logout or connect/disconnect calls access xmlBlaster's
 * authentication plugins through these methods.
 * This interface is implemented by authentication/Authenticate.java
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
 * @see org.xmlBlaster.authentication.Authenticate
 * @author xmlBlaster@marcelruff.info
 */
public interface I_Authenticate
{
   public boolean sessionExists(String sessionId);

   public Global getGlobal();

   public I_XmlBlaster getXmlBlaster();

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">The interface.connect requirement</a>
    */
   public ConnectReturnQosServer connect(ConnectQosServer qos) throws XmlBlasterException;

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">The interface.connect requirement</a>
    */
   public String connect(String qos) throws XmlBlasterException;

   /*
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">The interface.connect requirement</a>
    */
   public ConnectReturnQosServer connect(ConnectQosServer qos, String sessionId) throws XmlBlasterException;

   /*
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html">The interface.connect requirement</a>
    */
   public String connect(String qos, String sessionId) throws XmlBlasterException;

   /**
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">The interface.disconnect requirement</a>
    */
   public void disconnect(String sessionId, String qos_literal) throws XmlBlasterException;

   /**
    * Administrative access. 
    * For security reasons the I_AdminSubject implementation is SubjectInfoProtector
    */
   public I_AdminSubject getSubjectInfoByName(SessionName sessionName)  throws XmlBlasterException;

   public String toXml() throws XmlBlasterException;
}


