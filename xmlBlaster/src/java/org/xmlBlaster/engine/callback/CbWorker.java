/*------------------------------------------------------------------------------
Name:      CbWorker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding messages waiting on client callback.
Version:   $Id: CbWorker.java,v 1.1 2000/12/29 14:46:22 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.callback;

import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQoS;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;
import java.util.*;


/**
 * Queueing messages to send back to a client.
 */
public class CbWorker
{
   public final String ME = "CbWorker";
   public CbWorker()
   {
      if (Log.CALL) Log.call(ME, "Entering Constructor");
   }
}

