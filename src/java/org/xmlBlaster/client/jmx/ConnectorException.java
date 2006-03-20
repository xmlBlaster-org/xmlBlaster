/*------------------------------------------------------------------------------
Name:      ConnectorException.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.jmx;

import javax.management.JMException;

public class ConnectorException extends JMException {

  private Exception exception = null;

  public ConnectorException() {
    super();
  }

  public ConnectorException(String msg) {
    super(msg);
  }

  public ConnectorException(String msg, Exception e) {
    super(msg);
    this.exception = e;
  }

  public Exception getTargetException() {
    return exception;
  }

}