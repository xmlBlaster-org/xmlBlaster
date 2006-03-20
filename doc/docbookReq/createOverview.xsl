<?xml version='1.0' encoding="UTF-8"?>
<!--
Name:      createMenu.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generating a part of the site.xml to build the navigation bar
Version:   $Id$
Author:    laghi@swissinfo.org
-->


<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                exclude-result-prefixes="#default">

<!--
<xsl:output method="xml" 
   			doctype-system="http://www.oasis-open.org/docbook/xml/4.2/docbookx.dtd" 
            doctype-public = "-//OASIS//DTD DocBook XML V4.2//EN"
	   		indent="yes"/>
-->
<xsl:output method="xml" indent="yes"/>

    <!-- 
       article/title/text() = requirement/@id
       article/articleinfo/@userlevel = requirement/@status
      -->

   <!-- Create the HTML output for one requirement -->


<!-- loop over all requirement files -->
<xsl:template match="/">
   <article>
      <title>xmlBlaster - Requirements</title>
      <subtitle>XmlBlaster Programmers Reference Overview</subtitle>
      
      <para>
         IMPORTANT NOTE: Many features are not yet documented here (work in progress).
      </para>
      <para>
         The examples are in Java, but similar coding is used for other languages like C++ and Perl
      </para>
      
      <para>
      	<table>
            <title></title>
      		<tgroup cols="4">
               <thead valign="top">
                  <row>
                     <entry><emphasis>Requirement - ID</emphasis></entry>
                     <entry>Status</entry>
                     <entry>Info</entry>
                     <entry>Topic</entry>
                  </row>
               </thead>
            	<tbody valign="top">
                  <xsl:for-each select="files/url">
   <!--                     <xsl:sort select="document(.)/article/title/text()" order="ascending"/> -->
                     <xsl:sort select="document(.)/article/title" order="ascending"/>
                     <xsl:sort select="document(.)/article/articleinfo/@userlevel" order="ascending"/>
                     <xsl:call-template name="article"/>
                  </xsl:for-each>
            	</tbody>
      		</tgroup>
      	</table>
      </para>
      <para role="sideend">Last updated $Date: 2002-12-18 15:42:18 +0100 (Wed, 18 Dec 2002) $ $Author: ruff $</para>
      <para role="sideend">
         This page is generated from the requirement XML files in the xmlBlaster/doc/requirements directory of the distribution
      </para>
   </article>
</xsl:template>


<xsl:template match="url">
</xsl:template>   
   
   <!-- Create the HTML output for one requirement -->
<xsl:template name="article">
   <xsl:if test="document(.)/article/title != ''">
      <row>
         <!--   title/text() = requirement/@id  articleinfo/@userlevel = requirement/@status  -->
         <entry role="reqId"><xsl:value-of select="document(.)/article/title"/></entry>
         <entry>
            <xsl:attribute name="role"><xsl:value-of select="document(.)/article/articleinfo/@userlevel"/></xsl:attribute>
            <xsl:value-of select="document(.)/article/articleinfo/@userlevel"/>
         </entry>
         <entry>
            <ulink><xsl:attribute name="url"><xsl:value-of select="document(.)/article/title"/>.html</xsl:attribute><inlinegraphic align="center" fileref="arrow.gif"/></ulink>
         </entry>
         <entry role="topic"><xsl:value-of select="document(.)/article/subtitle"/></entry>
      </row>
   </xsl:if>
</xsl:template>


<!-- Error handling -->
   <xsl:template match="*">
  <center><p class="xmlerror"><blink>XSL parsing error</blink><br />
  Can't handle your supplied XML file</p>
  <p><xsl:value-of select="name()" /></p></center>
  <xsl:apply-templates />
</xsl:template>

</xsl:stylesheet>



