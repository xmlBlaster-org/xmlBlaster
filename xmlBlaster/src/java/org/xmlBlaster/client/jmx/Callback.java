/*------------------------------------------------------------------------------
Name:      Callback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import java.rmi.*;
import java.io.*;

public interface Callback extends Serializable {

  int peek();

  Object get() throws RemoteException;
}