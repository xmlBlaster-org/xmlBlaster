/*----------------------------------------------------------------------------
Name:      Key.cs
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Access Key informations
           Please consult the javadoc of the Java client library for API usage
Author:    "Marcel Ruff" <xmlBlaster@marcelruff.info>
Date:      12/2006
See:       http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.html
-----------------------------------------------------------------------------*/
using System;
using System.Text;

namespace org.xmlBlaster.client
{
   public class Key : KeyQosParser
   {
      public const string XPATH = "XPATH";
      public const string XPATH_URL_PREFIX = "xpath:";
      public const string EXACT = "EXACT";
      public const string EXACT_URL_PREFIX = "exact:";
      public const string DOMAIN = "DOMAIN";
      public const string DOMAIN_URL_PREFIX = "domain:";
      public const string REGEX = "REGEX";

      public const string INTERNAL_OID_PREFIX = "__sys__";
      public const string OID_DEAD_LETTER = INTERNAL_OID_PREFIX + "deadMessage";
      public const string INTERNAL_OID_ADMIN_CMD = "__cmd:";

      public const string DEFAULT_DOMAIN = null;

      public Key(string key)
         : base(key)
      {
      }

      public string ToXml()
      {
         return this.xml;
      }

      public string GetOid()
      {
         return GetXPath("/key/@oid", "");
      }

      public bool IsDeadMessage()
      {
         return OID_DEAD_LETTER.Equals(GetOid());
      }

      /// Messages starting with "__cmd:" are administrative messages
      public bool IsAdministrative()
      {
         string oid = GetOid();
         return (oid == null) ? false : oid.StartsWith(INTERNAL_OID_ADMIN_CMD);
      }

      public string GetContentMime()
      {
         return GetXPath("/key/@contentMime", "");
      }

      public string GetContentMimeExtended()
      {
         return GetXPath("/key/@contentMimeExtended", "");
      }

      public string GetDomain()
      {
         return GetXPath("/key/@domain", "");
      }

      /**
       * @return true if no domain is given (null or empty string). 
       */
      public bool IsDefaultDomain()
      {
         string domain = GetDomain();
         if (domain == null || domain.Length < 1 || domain.Equals(DEFAULT_DOMAIN))
            return true;
         return false;
      }
   }

   public class MsgKey : Key
   {
      public MsgKey(string key) : base(key) { }
      public string toXml()
      {
         return this.xml;
      }
      public string GetClientTags()
      {
         return GetXPath("/key/child::node()", "");
      }

   }

   public class UpdateKey : MsgKey
   {
      public UpdateKey(string qos)
         : base(qos)
      {
      }
   }

   public class GetKey : MsgKey
   {
      public GetKey(string qos)
         : base(qos)
      {
      }
   }

   public class StatusKey : Key
   {
      public StatusKey(string qos)
         : base(qos)
      {
      }
      public string GetQueryType()
      {
         return GetXPath("/key/@queryType", "");
      }

      public String GetQueryString()
      {
         // TODO: handle CDATA
         return GetXPath("/key/text()", "");
      }

      public bool IsExact()
      {
         return EXACT.Equals(GetQueryType());
      }

      public bool IsQuery()
      {
         return XPATH.Equals(GetQueryType()) ||
                REGEX.Equals(GetQueryType());
      }

      public bool IsXPath()
      {
         return XPATH.Equals(GetQueryType());
      }

      public bool IsDomain()
      {
         return DOMAIN.Equals(GetQueryType());
      }

      /// Access simplified URL like string. 
      /// Examples are "exact:hello", "xpath://key", "domain:sport"
      public string GetUrl()
      {
         if (IsExact())
            return EXACT_URL_PREFIX + GetOid();
         else if (IsXPath())
            return XPATH_URL_PREFIX + GetQueryString().Trim();
         else if (IsDomain())
            return DOMAIN_URL_PREFIX + GetDomain();
         throw new ApplicationException("getUrl() failed: Unknown query type: " + ToXml());
      }

      /**
       * Check if same query is used
       */
      public bool Equals(StatusKey other)
      {
         string oid = GetOid();
         string queryString = GetQueryString();
         string domain = GetDomain();
         return IsExact() && other.IsExact() && oid.Equals(other.GetOid()) ||
                IsQuery() && other.IsQuery() && queryString.Trim().Equals(other.GetQueryString().Trim()) ||
                IsDomain() && other.IsDomain() && domain.Trim().Equals(other.GetDomain().Trim());
      }
   }
}
