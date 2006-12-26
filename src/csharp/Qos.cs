/*----------------------------------------------------------------------------
Name:      Qos.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Access QoS informations
           Please consult the javadoc of the Java client library for API usage
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      12/2006
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
-----------------------------------------------------------------------------*/
using System;
using System.Xml;
using System.IO;
using System.Text;
using System.Collections;
using System.Runtime.InteropServices;

namespace org.xmlBlaster.client
{
   public class ClientProperty
   {
      public const string ENCODING_BASE64 = "base64";
      public const string ENCODING_FORCE_PLAIN = "forcePlain";
      public const string ENCODING_QUOTED_PRINTABLE = "quoted-printable";
      public const string ENCODING_NONE = null;
      public const string TYPE_STRING = "string"; // is default, same as ""
      public const string TYPE_BLOB = "byte[]";
      public const string TYPE_BOOLEAN = "boolean";
      public const string TYPE_BYTE = "byte";
      public const string TYPE_DOUBLE = "double";
      public const string TYPE_FLOAT = "float";
      public const string TYPE_INT = "int";
      public const string TYPE_SHORT = "short";
      public const string TYPE_LONG = "long";
      /** used to tell that the entry is really null (not just empty) */
      public const string TYPE_NULL = "null";

      private string name;
      private string type;
      /** The value encoded as specified with encoding */
      private string value;
      private string encoding;
      /** Mark the charset for a base64 encoded string */
      private string charset = "";
      /** Needed for Base64 encoding */
      //public static readonly bool isChunked = false;
      protected string tagName;
      //private long size = -1L;
      //private bool forceCdata = false;

      public ClientProperty(string tagName, string name, string type, string encoding, string value)
      {
         this.name = name;
         this.tagName = (tagName == null) ? "clientProperty" : tagName;
         this.type = type;
         this.encoding = encoding;
         SetValue(value);
      }

      private void SetValue(string value)
      {
         this.value = value;
      }

      public void SetCharset(string charset)
      {
         this.charset = charset;
      }

      public string GetName()
      {
         return this.name;
      }

      public string GetValueType()
      {
         if (this.type == null || this.type.Length < 1)
            return TYPE_STRING;
         return this.type;
      }

      public bool IsBase64()
      {
         return ENCODING_BASE64.Equals(this.encoding, StringComparison.OrdinalIgnoreCase);
      }

      /**
       * The raw still encoded value
       */
      public string GetValueRaw()
      {
         return this.value;
      }

      /// <summary>
      /// The string representation of the value.
      /// If the string is base64 encoded with a given charset, it is decoded
      /// and transformed to the default charset, typically "UTF-16" on Windows
      /// </summary>
      /// <returns>The value which is decoded (readable) in case it was base64 encoded, can be null</returns>
      public string GetStringValue()
      {
         if (this.value == null) return null;
         if (ENCODING_BASE64.Equals(this.encoding, StringComparison.OrdinalIgnoreCase))
         {
            byte[] content = GetBlobValue();
            try
            {
               Encoding enc = Encoding.GetEncoding(GetCharset());//, Encoding.UTF8, Encoding.UTF8); //EncoderFallback, DecoderFallback);
               if (enc != null)
                  return enc.GetString(content);
               return System.Text.Encoding.UTF8.GetString(content);
            }
            catch (Exception ex)
            {
               Console.WriteLine("Decode failed: " + ex.ToString());
            }
            return "";
         }
         return this.value;
      }

      /// <summary>
      /// 
      /// </summary>
      /// <returns>If was a string, it is encoded with UNICODE</returns>
      public byte[] GetBlobValue()
      {
         if (this.value == null) return null;
         if (ENCODING_BASE64.Equals(this.encoding, StringComparison.OrdinalIgnoreCase))
         {
            try
            {
               return System.Convert.FromBase64String(this.value);
            }
            catch (System.FormatException)
            {
               System.Console.WriteLine("Base 64 string length is not " +
                   "4 or is not an even multiple of 4.");
               return new byte[0];
            }
         }
         return System.Text.Encoding.Unicode.GetBytes(this.value);
      }

      public int GetIntValue()
      {
         return int.Parse(GetStringValue());
      }

      public long GetLongValue()
      {
         return long.Parse(GetStringValue());
      }

      public float GetFloatValue()
      {
         return float.Parse(GetStringValue());
      }

      public double GetDoubleValue()
      {
         return double.Parse(GetStringValue());
      }

      public bool GetBoolValue()
      {
         return bool.Parse(GetStringValue());
      }

      /// For example "int" or "byte[]"
      public string GetEncoding()
      {
         return this.encoding;
      }

      /// Returns the charset, for example "cp1252" or "UTF-8", helpful if base64 encoded
      public string GetCharset()
      {
         return this.charset;
      }
   }


   public class SessionName
   {
      public const string ROOT_MARKER_TAG = "/node";
      public const string SUBJECT_MARKER_TAG = "client";
      public const string SESSION_MARKER_TAG = "session";
      private string absoluteName;
      private string relativeName;
      private string nodeId;
      private string subjectId; // == loginName
      private long pubSessionId;
      public SessionName(string name)
      {

         if (name == null)
         {
            throw new Exception("Your given name is null");
         }

         name = name.Trim();

         char[] splitter = { '/' };

         string relative = name;

         // parse absolute part
         if (name.StartsWith("/"))
         {
            string[] arr = name.Split(splitter, StringSplitOptions.RemoveEmptyEntries);
            if (arr.Length == 0)
            {
               throw new Exception("'" + name + "': The root tag must be '/node'.");
            }
            if (arr.Length > 0)
            {
               if (!"node".Equals(arr[0]))
                  throw new Exception("'" + name + "': The root tag must be '/node'.");
            }
            if (arr.Length > 1)
            {
               this.nodeId = arr[1]; // the parsed nodeId
            }
            if (arr.Length > 2)
            {
               if (!SUBJECT_MARKER_TAG.Equals(arr[2]))
                  throw new Exception("'" + name + "': 'client' tag is missing.");
            }

            relative = "";
            for (int i = 3; i < arr.Length; i++)
            {
               relative += arr[i];
               if (i < (arr.Length - 1))
                  relative += "/";
            }
         }

         // parse relative part
         if (relative.Length < 1)
         {
            throw new Exception("'" + name + "': No relative information found.");
         }

         int ii = 0;
         string[] arr2 = relative.Split(splitter);
         if (arr2.Length > ii)
         {
            string tmp = arr2[ii++];
            if (SUBJECT_MARKER_TAG.Equals(tmp))
            { // "client"
               if (arr2.Length > ii)
               {
                  this.subjectId = arr2[ii++];
               }
               else
               {
                  throw new Exception("'" + name + "': No relative information found.");
               }
            }
            else
            {
               this.subjectId = tmp;
            }
         }
         else
         {
            throw new Exception("'" + name + "': No relative information found.");
         }
         if (arr2.Length > ii)
         {
            string tmp = arr2[ii++];
            if (SESSION_MARKER_TAG.Equals(tmp))
            {
               if (arr2.Length > ii)
               {
                  tmp = arr2[ii++];
               }
            }
            this.pubSessionId = long.Parse(tmp);
         }
      }

      /**
       * If the nodeId is not known, the relative name is returned
       * @return e.g. "/node/heron/client/joe/2", never null
       */
      public string GetAbsoluteName()
      {
         if (this.absoluteName == null)
         {
            StringBuilder buf = new StringBuilder(256);
            if (this.nodeId != null)
            {
               buf.Append("/node/").Append(this.nodeId).Append("/");
            }
            buf.Append(GetRelativeName());
            this.absoluteName = buf.ToString();
         }
         return this.absoluteName;
      }

      /**
       * @return #GetAbsoluteName()
       */
      public override string ToString()
      {
         return GetAbsoluteName();
      }

      /**
       * @return e.g. "client/joe/2" or "client/joe", never null
       */
      public string GetRelativeName()
      {
         if (this.relativeName == null)
         {
            StringBuilder buf = new StringBuilder(126);
            // For example "client/joe/session/-1"
            buf.Append(SUBJECT_MARKER_TAG).Append("/").Append(subjectId);
            if (IsSession())
            {
               buf.Append("/");
               buf.Append(SESSION_MARKER_TAG).Append("/");
               buf.Append("" + this.pubSessionId);
            }
            this.relativeName = buf.ToString();
         }
         return this.relativeName;
      }

      /**
       * @return e.g. "heron", or null
       */
      public string GetNodeIdStr()
      {
         return this.nodeId;
      }

      /**
       * @return e.g. "joe", never null
       */
      public string GetLoginName()
      {
         return this.subjectId;
      }

      /**
       * @return The public session identifier e.g. "2" or 0 if in subject context
       */
      public long GetPublicSessionId()
      {
         return this.pubSessionId;
      }

      /**
       * Check if we hold a session or a subject
       */
      public bool IsSession()
      {
         return this.pubSessionId != 0L;
      }

      /** @return true it publicSessionId is given by xmlBlaster server (if < 0) */
      public bool IsPubSessionIdInternal()
      {
         return this.pubSessionId < 0L;
      }

      /** @return true it publicSessionId is given by user/client (if > 0) */
      public bool IsPubSessionIdUser()
      {
         return this.pubSessionId > 0L;
      }

      /**
       * @return true if relative name equals
       */
      public bool equalsRelative(SessionName sessionName)
      {
         return GetRelativeName().Equals(sessionName.GetRelativeName());
      }

      public bool equalsAbsolute(SessionName sessionName)
      {
         return GetAbsoluteName().Equals(sessionName.GetAbsoluteName());
      }
   }

   public class KeyQosParser
   {
      protected string xml;
      protected XmlDocument xmlDocument;

      public KeyQosParser(string xml)
      {
         this.xml = xml;
      }

      public XmlNodeList GetXmlNodeList(string key)
      {
         if (this.xmlDocument == null)
         {
            this.xmlDocument = new XmlDocument();
            string tmp = (this.xml == null || this.xml.Length < 1) ? "<error/>" : this.xml;
            this.xmlDocument.LoadXml(tmp);
         }
         XmlNodeList xmlNodeList = this.xmlDocument.SelectNodes(key);
         Console.WriteLine("Found " + xmlNodeList.Count);
         return xmlNodeList;
      }

      /// <summary>
      /// Extract e.g. "hello" from "<a>hello</a>"
      /// </summary>
      /// <param name="xml"></param>
      /// <param name="tag"></param>
      /// <returns></returns>
      public static string extract(string xml, string tag) {
         if (xml == null || tag == null) return null;
         string startToken = "<" + tag + ">";
         string endToken = "</" + tag + ">";
         int start = xml.IndexOf(startToken);
         int end = xml.IndexOf(endToken);
         if (start != -1 && end != -1) {
            start += startToken.Length;
            return xml.Substring(start, end-start);
         }
         return null;
      }


      /// <summary>
      /// Supports query on attributes or tag values
      /// </summary>
      /// <param name="key">For example "/qos/priority/text()"</param>
      /// <param name="defaultValue"></param>
      /// <returns></returns>
      public string GetXPath(string key, string defaultValue)
      {
         XmlNodeList xmlNodeList = GetXmlNodeList(key);
         for (int i = 0; i < xmlNodeList.Count; i++)
         {
            XmlNode node = xmlNodeList[i];
            if (node.NodeType == XmlNodeType.Attribute)
               return node.Value;
            else if (node.NodeType == XmlNodeType.Text)
               return node.Value;
            else if (node.NodeType == XmlNodeType.Element && node.HasChildNodes) {
               if (node.FirstChild.NodeType == XmlNodeType.CDATA) {
                  // Returns string inside a CDATA section
                  return node.FirstChild.Value;
               }
               else if (node.FirstChild.NodeType == XmlNodeType.Element) {
                  // Returns string with all sub-tags
                  return node.OuterXml;
               }
            }
         }
         return defaultValue;
      }

      /// <summary>
      /// Query bools without text() to find empty tags like "<persistent/>"
      /// </summary>
      /// <param name="key">"/qos/persistent" or "qos/persistent/text()"</param>
      /// <param name="defaultValue"></param>
      /// <returns></returns>
      public bool GetXPath(string key, bool defaultValue)
      {
         if (key == null) return defaultValue;
         if (key.EndsWith("/text()"))
            key = key.Substring(0, key.Length - "/text()".Length);
         XmlNodeList xmlNodeList = GetXmlNodeList(key);
         string value = "" + defaultValue;
         for (int i = 0; i < xmlNodeList.Count; i++)
         {
            XmlNode node = xmlNodeList[i];
            if (node.NodeType == XmlNodeType.Attribute) {
               value = node.Value;
               break;
            }
            else if (node.NodeType == XmlNodeType.Text)
            {
               value = node.Value;
               break;
            }
            else if (node.NodeType == XmlNodeType.Element)
            {
               if (node.HasChildNodes &&
                   node.FirstChild.NodeType == XmlNodeType.Text) {
                  value = node.FirstChild.Value;
                  break;
               }
               else if (node.Value == null)
                  return true;
            }
         }
         try {
            return bool.Parse(value);
         }
         catch (Exception) {
            return defaultValue;
         }
      }
      /*
      public bool GetXPath(string key, bool defaultValue)
      {
         string ret = GetXPath(key, null);
         if (ret == null) return defaultValue;
         try
         {
            return bool.Parse(ret);
         }
         catch (Exception)
         {
            return defaultValue;
         }
      }
      */
      public long GetXPath(string key, long defaultValue)
      {
         string ret = GetXPath(key, null);
         if (ret == null) return defaultValue;
         try
         {
            return long.Parse(ret);
         }
         catch (Exception)
         {
            return defaultValue;
         }
      }

      public int GetXPath(string key, int defaultValue)
      {
         string ret = GetXPath(key, null);
         if (ret == null) return defaultValue;
         try
         {
            return int.Parse(ret);
         }
         catch (Exception)
         {
            return defaultValue;
         }
      }
   }

   public class Qos : KeyQosParser
   {
      //private string NewLine = "\r\n";
      public const string STATE_OK = "OK";
      public const string STATE_WARN = "WARNING";
      public const string STATE_TIMEOUT = "TIMEOUT";
      public const string STATE_EXPIRED = "EXPIRED";
      public const string STATE_ERASED = "ERASED";
      public const string STATE_FORWARD_ERROR = "FORWARD_ERROR";
      public const string INFO_QUEUED = "QUEUED";

      private Hashtable clientProperties;

      public Qos(string qos)
         : base(qos)
      {
      }

      public long GetRcvTimeNanos()
      {
         string ret = GetXPath("/qos/rcvTimestamp/@nanos", "");
         if (ret == null) return -1L;
         try
         {
            return long.Parse(ret);
         }
         catch (Exception)
         {
            return -1L;
         }
      }

      public string GetRcvTime()
      {
         return GetXPath("/qos/rcvTimestamp/text()", "").Trim();
      }

      public ClientProperty GetClientProperty(string key)
      {
         return (ClientProperty)GetClientProperties()[key];
      }

      public int GetClientProperty(string name, int defaultValue)
      {
         if (name == null) return defaultValue;
         ClientProperty clientProperty = GetClientProperty(name);
         if (clientProperty == null)
            return defaultValue;
         try
         {
            return clientProperty.GetIntValue();
         }
         catch (FormatException)
         {
            return defaultValue;
         }
      }

      public long GetClientProperty(string name, long defaultValue)
      {
         if (name == null) return defaultValue;
         ClientProperty clientProperty = GetClientProperty(name);
         if (clientProperty == null)
            return defaultValue;
         try
         {
            return clientProperty.GetLongValue();
         }
         catch (FormatException)
         {
            return defaultValue;
         }
      }

      public float GetClientProperty(string name, float defaultValue)
      {
         if (name == null) return defaultValue;
         ClientProperty clientProperty = GetClientProperty(name);
         if (clientProperty == null)
            return defaultValue;
         try
         {
            return clientProperty.GetFloatValue();
         }
         catch (FormatException)
         {
            return defaultValue;
         }
      }

      public double GetClientProperty(string name, double defaultValue)
      {
         if (name == null) return defaultValue;
         ClientProperty clientProperty = GetClientProperty(name);
         if (clientProperty == null)
            return defaultValue;
         try
         {
            return clientProperty.GetDoubleValue();
         }
         catch (FormatException)
         {
            return defaultValue;
         }
      }

      public byte[] GetClientProperty(string name, byte[] defaultValue)
      {
         if (name == null) return defaultValue;
         ClientProperty clientProperty = GetClientProperty(name);
         if (clientProperty == null)
            return defaultValue;
         try
         {
            return clientProperty.GetBlobValue();
         }
         catch (FormatException)
         {
            return defaultValue;
         }
      }

      public bool GetClientProperty(string name, bool defaultValue)
      {
         if (name == null) return defaultValue;
         ClientProperty clientProperty = GetClientProperty(name);
         if (clientProperty == null)
            return defaultValue;
         try
         {
            return clientProperty.GetBoolValue();
         }
         catch (FormatException)
         {
            return defaultValue;
         }
      }

      public string GetClientProperty(string name, string defaultValue)
      {
         ClientProperty clientProperty = GetClientProperty(name);
         if (clientProperty == null)
            return defaultValue;
         return clientProperty.GetStringValue();
      }

      public Hashtable GetClientProperties()
      {
         if (this.clientProperties != null)
            return this.clientProperties;
         this.clientProperties = new Hashtable();
         String key = "/qos/clientProperty";
         XmlNodeList xmlNodeList = GetXmlNodeList(key);
         for (int i = 0; i < xmlNodeList.Count; i++)
         {
            XmlNode node = xmlNodeList[i];
            if (node.NodeType == XmlNodeType.Element)
            {
               // <clientProperty name='myDescription' encoding="base64" charset="windows-1252">bla</clientProperty>
               XmlNode attr = node.Attributes.GetNamedItem("name");
               if (attr == null)
               {
                  throw new ApplicationException("qos clientProperty without name attribute");
               }
               string name = attr.Value;
               attr = node.Attributes.GetNamedItem("type");
               string type = (attr != null) ? attr.Value : "";
               attr = node.Attributes.GetNamedItem("encoding");
               string encoding = (attr != null) ? attr.Value : "";
               attr = node.Attributes.GetNamedItem("charset");
               string charset = (attr != null) ? attr.Value : "";
               string value = (node.FirstChild != null) ? node.FirstChild.Value : "";
               ClientProperty clientProperty = new ClientProperty("clientProperty", name, type, encoding, value);
               clientProperty.SetCharset(charset);
               this.clientProperties.Add(name, clientProperty);
            }
            else
            {
               Console.WriteLine("Ignoring '" + key + "' with nodeType=" + node.NodeType);
            }
         }
         return this.clientProperties;
      }
   }


   public class MsgQos : Qos
   {
      public MsgQos(string qos) : base(qos) { }

      public string GetState()
      {
         return GetXPath("/qos/state/@id", STATE_OK);
      }

      public bool IsOk()
      {
         return STATE_OK.Equals(GetState());
      }

      public bool IsErased()
      {
         return STATE_ERASED.Equals(GetState());
      }

      public bool IsPtp()
      {
         return GetXPath("", false);
      }

      public bool IsPersistent()
      {
         return GetXPath("/qos/persistent/text()", false);
      }

      //public bool isTimeout()
      //{
      //   return GetXPath("", "");
      //}

      public string GetStateInfo()
      {
         return GetXPath("/qos/state/@info", "");
      }

      public int GetPriority()
      {
         return GetXPath("/qos/priority/text()", 5);
      }

      public long GetQueueIndex()
      {
         return GetXPath("/qos/queue/@index", 0L);
      }

      public long GetQueueSize()
      {
         return GetXPath("/qos/queue/@size", 0L);
      }

      //public Timestamp GetRcvTimestamp()
      //{
      //   return GetXPath("", "");
      //}

      public int GetRedeliver()
      {
         return GetXPath("/qos/redeliver/text()", 0);
      }

      //long GetRemainingLifeStatic()

      public SessionName GetSender()
      {
         string text = GetXPath("/qos/sender/text()", "");
         if (text == null || text.Length < 1) return null;
         return new SessionName(text);
      }

      public string GetSubscriptionId()
      {
         return GetXPath("/qos/subscribe/@id", "");
      }

      public long GetLifeTime()
      {
         return GetXPath("/qos/expiration/@lifeTime", -1L);
      }

      public string ToXml()
      {
         return this.xml;
      }
   }

   public class UpdateQos : MsgQos
   {
      public UpdateQos(string qos)
         : base(qos)
      {
      }
   }

   internal class StatusQos : Qos
   {
      public StatusQos(string qos) : base(qos) { }

      public string GetState()
      {
         return GetXPath("/qos/state/@id", STATE_OK);
      }

      public bool IsOk()
      {
         return STATE_OK.Equals(GetState());
      }

      public string GetStateInfo()
      {
         return GetXPath("/qos/state/@info", "");
      }

      public string GetSubscriptionId()
      {
         return GetXPath("/qos/subscribe/@id", "");
      }

      public string GetKeyOid()
      {
         return GetXPath("/qos/key/@oid", "");
      }

      public string toXml()
      {
         return this.xml;
      }
   }

   public class SubscribeReturnQos
   {
      private readonly StatusQos statusQosData;
      private readonly bool isFakedReturn;

      internal SubscribeReturnQos(string xmlQos)
         : this(xmlQos, false)
      {
      }

      internal SubscribeReturnQos(string xmlQos, bool isFakedReturn)
      {
         this.isFakedReturn = isFakedReturn;
         this.statusQosData = new StatusQos(xmlQos);
         //this.statusQosData.SetMethod(MethodName.SUBSCRIBE);
      }

      //public boolean isFakedReturn() {
      //   return this.isFakedReturn;
      //}

      public string GetState()
      {
         return this.statusQosData.GetState();
      }

      public string GetStateInfo()
      {
         return this.statusQosData.GetStateInfo();
      }

      public string GetSubscriptionId()
      {
         return this.statusQosData.GetSubscriptionId();
      }

      public string toXml()
      {
         return this.statusQosData.toXml();
      }
   }

   public class UnSubscribeReturnQos : SubscribeReturnQos
   {
      internal UnSubscribeReturnQos(string xmlQos)
         : base(xmlQos)
      {
      }
   }

   public class EraseReturnQos
   {
      private readonly StatusQos statusQosData;

      internal EraseReturnQos(string xmlQos)
      {
         this.statusQosData = new StatusQos(xmlQos);
      }

      public string GetState()
      {
         return this.statusQosData.GetState();
      }

      public string GetStateInfo()
      {
         return this.statusQosData.GetStateInfo();
      }

      public string GetKeyOid()
      {
         return this.statusQosData.GetKeyOid();
      }

      public string toXml()
      {
         return this.statusQosData.toXml();
      }
   }

   public class PublishReturnQos
   {
      private readonly StatusQos statusQosData;

      internal PublishReturnQos(string xmlQos)
      {
         this.statusQosData = new StatusQos(xmlQos);
      }

      public string GetState()
      {
         return this.statusQosData.GetState();
      }

      public string GetStateInfo()
      {
         return this.statusQosData.GetStateInfo();
      }

      public string GetKeyOid()
      {
         return this.statusQosData.GetKeyOid();
      }

      /// "2002-02-10 10:52:40.879456789"
      public string GetRcvTime()
      {
         return this.statusQosData.GetRcvTime();
      }

      public long GetRcvTimeNanos()
      {
         return this.statusQosData.GetRcvTimeNanos();
      }

      public string toXml()
      {
         return this.statusQosData.toXml();
      }
   }

   public class ConnectReturnQos : Qos
   {
      internal ConnectReturnQos(string xmlQos)
         : base(xmlQos)
      {
      }

      public string GetSecurityServiceUser()
      {
         string ret = GetXPath("/qos/securityService", "").Trim();
         return extract(ret, "user");
      }

      public string GetSecurityServicePasswd()
      {
         //Fails if a CDATA section
         //return GetXPath("/qos/securityService/passwd/text()", "");
         string ret = GetXPath("/qos/securityService", "").Trim();
         return extract(ret, "passwd");
      }

      public SessionName GetSessionName()
      {
         string text = GetXPath("/qos/session/@name", "");
         if (text == null || text.Length < 1) return null;
         return new SessionName(text);
      }

      public long GetSessionTimeout()
      {
         return GetXPath("/qos/session/@timeout", 3600000L);
      }

      public int GetSessionMax()
      {
         return GetXPath("/qos/session/@maxSessions", 10);
      }

      public bool GetSessionClear()
      {
         return GetXPath("/qos/session/@clearSessions", false);
      }

      public bool GetSessionReconnectSameOnly()
      {
         return GetXPath("/qos/session/@reconnectSameClientOnly", false);
      }

      public string GetSecretSessionId()
      {
         return GetXPath("/qos/session/@sessionId", "");
      }

      public bool IsPtp()
      {
         return GetXPath("/qos/ptp/text()", false);
      }

      public bool IsReconnected()
      {
         return GetXPath("/qos/reconnected/text()", false);
      }

      public bool IsPersistent()
      {
         return GetXPath("/qos/persistent", false);
      }
      /*
                              public string Get()
                              {
                                 return GetXPath("/qos/", "");
                              }
                              */
      public string toXml()
      {
         return base.xml;
      }
   }
}
