/*------------------------------------------------------------------------------
Name:      RequestContainer.java
Project:   xmlBlaster.org
Comment:   Contains fields for all necessary data provided by each
           client request
Author:    astelzl@avitech.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.recorder.file;


import java.io.Serializable;

import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.MethodName;

/**
 * Contains fields for all necessary data provided by each
 * client request
 * @author astelzl@avitech.de
 */
public class RequestContainer implements Serializable
{
  long timestamp;
  /** publish/subscribe/get etc. */
  MethodName method;
  String cbSessionId;
  String xmlKey;
  String xmlQos;
  //MsgUnit msgUnit;
  MsgUnit[] msgUnitArr;

  RequestContainer()
  { timestamp = System.currentTimeMillis();
  }
}
