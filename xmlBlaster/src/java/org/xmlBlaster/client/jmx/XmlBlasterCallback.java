/*------------------------------------------------------------------------------
Name:      XmlBlasterCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import java.rmi.RemoteException;
import org.xmlBlaster.util.admin.extern.MethodInvocation;

import org.xmlBlaster.util.admin.extern.XmlBlasterConnector;

public class XmlBlasterCallback implements Callback{

  private int status = XmlBlasterConnector.UNKNOWN;
  private String ID = null;
  private MethodInvocation mi = null;

  XmlBlasterCallback(String ID, MethodInvocation mi) {
    status = XmlBlasterConnector.SENDING;

    this.ID = ID;
    this.mi = mi;
  }

  protected void setMethodInvocation(MethodInvocation mi) {
    synchronized (this) {
      this.mi = mi;
      status = XmlBlasterConnector.FINISHED;
      notifyAll();
    }
  }

  public int peek() {
    return status;
  }

  public Object get() throws RemoteException {
    synchronized (this) {
      while (status != XmlBlasterConnector.FINISHED) {
        try {
          wait();
        }
        catch (InterruptedException e) {}
        }
      }
      return mi.getReturnValue();
    }
  }
