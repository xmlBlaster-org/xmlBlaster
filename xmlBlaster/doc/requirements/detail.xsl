<?xml version='1.0'?>
<!--
Name:      detail.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generating a detailed html view for one requirement
See:       xmlBlaster/doc/requirements/requirement.dtd
Version:   $Id: detail.xsl,v 1.25 2002/12/18 14:42:17 ruff Exp $
Author:    xmlBlaster@marcelruff.info
-->

<!DOCTYPE xsl:stylesheet [
<!ENTITY nbsp   "&#160;">
]>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                xmlns="http://www.w3.org/TR/xhtml1/transitional"
                exclude-result-prefixes="#default">


<xsl:output method="html"/>


<!-- Create the HTML output for one requirement -->
<xsl:template match="requirement">
   <html>
   <head>
      <meta http-equiv="Content-Type" content="text/html; charset=iso-8859-1" />
      <meta http-equiv="Pragma" content="no-cache" />
      <meta http-equiv="Expires" content="Tue, 31 Dec 1997 23:59:59 GMT" />
      <link REL="stylesheet" type="text/css" href="../howto/xmlBlaster.css" />
      <title>xmlBlaster - Requirement <xsl:value-of select="@id"/></title>
   </head>

   <body>

   <!-- p class="sideend"> Last updated $Date: 2002/12/18 14:42:17 $ $Author: ruff $ </p -->
   <table width="700" border="1">
   <tr>
      <td>
      <p>
      <a href="http://www.xmlBlaster.org"><img src="logo_xmlBlaster_2.gif" border="0" title="XmlBlaster Logo" alt="XmlBlaster Logo" /></a>
      </p>
      </td>
      <td>
      <p class="sitetitel">REQUIREMENT</p>
      <p class="sitetitel"><xsl:value-of select="@id"/></p>
      </td>
      <td align="right">
      <p>
      <a href="http://www.xmlBlaster.org"><img src="logo_xmlBlaster_2.gif" border="0" title="XmlBlaster Logo" alt="XmlBlaster Logo" /></a>
      </p>
      </td>
   </tr>
   </table>

   <p />
   <table cellpadding="2" cellspacing="4">
      <tr>
         <td class="reqId">Type</td>
         <td><xsl:value-of select="@type"/></td>
      </tr>
      <tr>
         <td class="reqId">Priority</td>
         <td><xsl:value-of select="@prio"/></td>
      </tr>
      <tr>
         <td class="reqId">Status</td>
         <td>
            <xsl:attribute name="class"><xsl:value-of select="@status"/></xsl:attribute>
            <xsl:value-of select="@status"/>
         </td>
      </tr>
   <p /><br />
   </table>

   <table cellpadding="2" cellspacing="4">
      <tr>
         <td class="reqId">Topic</td>
         <td class="topic"><xsl:value-of select="topic"/></td>
      </tr>
      <tr>
         <td class="reqId">Des<br />cription</td>
         <td class="description">
            <!-- xsl:value-of select="description" disable-output-escaping="yes" / -->
            <xsl:copy-of select="description" />
         </td>
      </tr>

      <xsl:for-each select="example">
      <tr>
         <td class="reqId">Example<br /><i><xsl:value-of select="@lang"/></i></td>
         <td class="example">
         <xsl:choose>
         <xsl:when test="@type='HTML'">
            <!-- xsl:value-of select="example"/-->
            <xsl:copy-of select="." />
         </xsl:when>
         <xsl:otherwise>
            <pre><xsl:value-of select="."/></pre>
         </xsl:otherwise>
         </xsl:choose>
         </td>
      </tr>
      </xsl:for-each>

      <tr>
         <td class="reqId">Configure</td>
         <td class="configuration">
            <xsl:copy-of select="configuration" />
            <p class="sideend">
               NOTE: Configuration parameters are specified on command line (-someValue 17) or in the
               xmlBlaster.properties file (someValue=17). See requirement "util.property" for details.<br />
               Columns named <b>Impl</b> tells you if the feature is implemented.<br />
               Columns named <b>Hot</b> tells you if the configuration is changeable in hot operation.
            </p>
         </td>
      </tr>
      <xsl:for-each select="todo">
         <tr>
            <td class="reqId">Todo</td>
            <td class="todo">
               <xsl:choose>
               <xsl:when test="@type='HTML'">
                  <xsl:copy-of select="." />
               </xsl:when>
               <xsl:otherwise>
                  <pre><xsl:value-of select="."/></pre>
               </xsl:otherwise>
               </xsl:choose>
            </td>
         </tr>
      </xsl:for-each>

      <xsl:for-each select="changerequest">
         <tr>
            <td class="reqId">Change Request</td>
            <td class="changerequest"><pre><xsl:value-of select="." /></pre></td>
         </tr>
      </xsl:for-each>

      <xsl:for-each select="see">
         <tr>
            <xsl:if test="@type='API'">
               <td class="reqId">See API</td>
               <td>
               <a>
                  <xsl:attribute name="href">../api/<xsl:value-of select="translate(.,'.','/')"/>.html</xsl:attribute>
                  <xsl:value-of select="."/>
               </a>
               </td>
            </xsl:if>

            <xsl:if test="@type='REQ'">
               <td class="reqId">See REQ</td>
               <td>
               <a>
                  <xsl:attribute name="href"><xsl:value-of select="."/>.html</xsl:attribute>
                  <xsl:value-of select="."/>
               </a>
               </td>
            </xsl:if>

            <xsl:if test="@type='OTHER'">
               <td class="reqId">See</td>
               <td><xsl:value-of select="."/></td>
            </xsl:if>

            <xsl:if test="@type='INTERNET'">
               <td class="reqId">See</td>
               <td>
               <a>
                  <xsl:attribute name="href"><xsl:value-of select="."/></xsl:attribute>
                  <xsl:attribute name="target">others</xsl:attribute>
                  <xsl:value-of select="."/>
               </a>
               </td>
            </xsl:if>

            <xsl:if test="@type='LOCAL'">
               <td class="reqId">See</td>
               <td>
               <a>
                  <xsl:attribute name="href"><xsl:value-of select="."/></xsl:attribute>
                  <xsl:value-of select="."/>
               </a>
               </td>
            </xsl:if>


         </tr>
      </xsl:for-each>

      <xsl:for-each select="testcase">
         <xsl:if test="@status!='OPEN'">
         <xsl:for-each select="test">
            <tr>
               <xsl:if test="@tool='SUITE'">
                  <td class="reqId">See TEST</td>
                    <td>
                  <a>
                    <xsl:attribute name="href">../../testsuite/src/java/<xsl:value-of select="translate(.,'.','/')"/>.java.html</xsl:attribute>
                    <xsl:value-of select="."/>
                  </a>
                  </td>
               </xsl:if>

               <xsl:if test="@tool!='SUITE'">
                  <td class="reqId">Testcase</td>
                  <td><xsl:value-of select="."/></td>
               </xsl:if>
            </tr>
         </xsl:for-each>
         </xsl:if>
      </xsl:for-each>

   </table>

   <p class="sideend">
   This page is generated from the requirement XML file xmlBlaster/doc/requirements/<xsl:value-of select="@id"/>.xml
   </p>
   <p class="sideend">
      <a href="requirement.html" target="Content">Back to overview</a><br />
   </p>
   </body>
   </html>
</xsl:template>


<!-- Error handling -->
<xsl:template match="*">
  <center><p class="xmlerror"><blink>XSL parsing error</blink><br />
  Can't handle your supplied XML file</p>
  <p><xsl:value-of select="name()" /></p></center>
  <xsl:apply-templates />
</xsl:template>


</xsl:stylesheet>




