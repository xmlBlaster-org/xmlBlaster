/*
 * Copyright (c) 2003 Peter Antman, Teknik i Media  <peter.antman@tim.se>
 *
 * $Id$
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.xmlBlaster.protocol.local;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Is implemented by the callback client (LOCAL protocol driver). 
 *
 * Created: Mon Sep 15 18:39:08 2003
 * @author <a href="mailto:pra@tim.se">Peter Antman</a>
 * @version $Revision: 1.2 $
 * @see org.xmlBlaster.client.protocol.local.LocalCallbackImpl
 */

public interface I_LocalCallback {
   public void updateOneway(String cbSessionId, org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr) throws XmlBlasterException;
   public String[] update(String cbSessionId, MsgUnitRaw[] msgUnitArr) throws XmlBlasterException;
   public String ping(String str);
   
}// I_LocalCallback
