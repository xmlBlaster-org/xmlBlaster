/*------------------------------------------------------------------------------
Name:      RequestContainer.java
Project:   xmlBlaster.org
Comment:   Contains fields for all necessary data provided by each
           client request
Author:    astelzl@avitech.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder.file;


import java.io.Serializable;

import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Contains fields for all necessary data provided by each
 * client request
 * @author astelzl@avitech.de
 */
public class RequestContainer implements Serializable
{
  long timestamp;
  /** publish/subscribe/get etc. */
  String method;
  String cbSessionId;
  String xmlKey;
  String xmlQos;
  MessageUnit msgUnit;
  MessageUnit[] msgUnitArr;

  RequestContainer()
  { timestamp = System.currentTimeMillis();
  }
}
