/*------------------------------------------------------------------------------
Name:      I_InvocationRecorder.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface
Version:   $Id: I_InvocationRecorder.java,v 1.3 2002/05/27 16:23:47 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Interface for InvocationRecorder, the supported methods.
 * <p />
 * @see InvocationRecorder
 */
public interface I_InvocationRecorder extends I_XmlBlaster
{
   public boolean isFull() throws XmlBlasterException;
   public int size();
   public long getNumLost();
   public void playback(long startDate, long endDate, double motionFactor) throws XmlBlasterException;
   public void reset();
}
