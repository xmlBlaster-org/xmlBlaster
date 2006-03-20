<?xml version='1.0'?>
<!--
Name:      req2Docbook.xsl
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Generating a docbook document from a requirement
See:       xmlBlaster/doc/requirements/requirement.dtd
Version:   $Id$
Author:    laghi@swissinfo.org
-->

<!DOCTYPE xsl:stylesheet [
<!ENTITY nbsp   "&#160;">
]>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                version='1.0'
                exclude-result-prefixes="#default">
<!--
<xsl:output method="xml" 
			doctype-system="http://www.oasis-open.org/docbook/xml/4.2/docbookx.dtd" 
            doctype-public = "-//OASIS//DTD DocBook XML V4.2//EN"
			indent="yes"
/>
-->

<xsl:output method="xml" indent="yes"/>

<!--
<xsl:output method="xml" indent="yes" omit-xml-declaration="yes"/>
-->

<!-- here come html handling -->

<!-- these should pass as they are 

<xsl:template name="addAttributes">
	<xsl:for-each select="@*"><xsl:copy/></xsl:for-each>
</xsl:template>
-->

<xsl:template match="html | HTML">
	<article>
		<title/>
		<para>
			<xsl:apply-templates/>
		</para>
	</article>
</xsl:template>

<xsl:template match="h1 | H1| h2 | H2 | h3 | H3 | h4 | H4 | h5 | H5 |h6 | H6">
	<para>
		<emphasis>
			<xsl:attribute name="role"><xsl:value-of select="."/></xsl:attribute>
			<xsl:apply-templates/>
		</emphasis>
	</para>
</xsl:template>

<xsl:template match="meta | META | link | LINK | title | TITLE | body | BODY | area | AREA | style | STYLE">
	<xsl:apply-templates/>
	<!--
	<xsl:copy>
		<xsl:for-each select="@*"><xsl:copy/></xsl:for-each>
	</xsl:copy>
	-->
</xsl:template>

<!-- these should be wrapped inside a comment -->
<xsl:template match="head | HEAD | script | SCRIPT | map | MAP | hr | HR">
<!-- not doing anything since nested comments could occur
	<xsl:comment>
		<xsl:copy>
			<xsl:for-each select="@*"><xsl:copy/></xsl:for-each>
		</xsl:copy>
		<xsl:apply-templates/>
	</xsl:comment>
-->
</xsl:template>

<xsl:template match="p | P | font | FONT">
	<para>
  		<xsl:apply-templates/>
	</para>
</xsl:template>

<xsl:template match="ul | UL">
   <itemizedlist>
      <xsl:apply-templates/>
   </itemizedlist>
</xsl:template>

<xsl:template match="dl | DL">
   <itemizedlist>
      <xsl:apply-templates/>
   </itemizedlist>
</xsl:template>

<xsl:template match="ol | OL">
   <orderedlist>
      <xsl:apply-templates/>
   </orderedlist>
</xsl:template>

<xsl:template match="blockquote | BLOCKQUOTE">
	<blockquote>
		<para>
	    	<xsl:apply-templates/>
		</para>
	</blockquote>
</xsl:template>

<xsl:template match="tt | TT | code | CODE">
   <literallayout>
      <xsl:apply-templates/>
   </literallayout>
</xsl:template>

<xsl:template match="pre | PRE">
   <!-- <programlisting> since does not allow fancy stuff inside -->
   <programlisting>
      <xsl:apply-templates/>
   </programlisting>
</xsl:template>

<xsl:template match="pre/a | pre/strong | pre/i"> 
	<xsl:apply-templates/>
</xsl:template>

<xsl:template match="li | LI">
   <listitem>
		<para>
		<xsl:apply-templates/>
		</para>
   </listitem>	
</xsl:template>

<xsl:template match="dt | DT">
	<xsl:text disable-output-escaping="yes"><![CDATA[<listitem>]]></xsl:text>
	<abstract>
		<para>
		<xsl:apply-templates/>
		</para>
	</abstract>
</xsl:template>

<xsl:template match="dd | DD">
	<para>
		<xsl:apply-templates/>
	</para>
	<xsl:text disable-output-escaping="yes"><![CDATA[</listitem>]]></xsl:text>
</xsl:template>

<xsl:template match="br | BR">
<!--   <codelisting>
   </codelisting>
-->
	<para>
	</para>
</xsl:template>

<!-- this should be parsed again 
<xsl:template match="p/br | p/BR | P/br | P/BR">
	<xsl:text disable-output-escaping="yes"><![CDATA[</para>]]></xsl:text>
	<xsl:variable name="line">
	    <xsl:text disable-output-escaping="yes"><![CDATA[<para]]></xsl:text>
		<xsl:for-each select="../@*">
			<xsl:text> </xsl:text><xsl:value-of select="name(.)"/><xsl:text>='</xsl:text><xsl:value-of select="."/><xsl:text>'</xsl:text>
		</xsl:for-each>
		<xsl:text disable-output-escaping="yes"><![CDATA[>]]></xsl:text>
	</xsl:variable>
	<xsl:value-of  disable-output-escaping="yes" select="$line"/>
	<xsl:message terminate="no">THE ATTRIBUTES MUST BE PASSED TO THE NEW LINE PARAGRAPH <xsl:value-of  disable-output-escaping="yes" select="$line"/> END</xsl:message>
	<xsl:apply-templates/>
</xsl:template>
-->

<xsl:template match="p/br | p/BR | P/br | P/BR">
	<xsl:text disable-output-escaping="yes"><![CDATA[</para>]]></xsl:text>
	<xsl:apply-templates/>
    <xsl:text disable-output-escaping="yes"><![CDATA[<para>]]></xsl:text>
</xsl:template>


<xsl:template match="i | I">
   <acronym><xsl:apply-templates/></acronym>
</xsl:template>

<xsl:template match="u | U">
   <emphasis role="underline"><xsl:apply-templates/></emphasis>
</xsl:template>

<xsl:template match="b | B | strong | STRONG">
   <emphasis><xsl:apply-templates/></emphasis>
</xsl:template>

<!-- with 'graphic' alters the layout: probably still a transformation problem in forrest
<xsl:template match="img | IMG">
   <inlinegraphic align="center">
		<xsl:attribute name="fileref"><xsl:value-of select="@src"/></xsl:attribute>
   </inlinegraphic>
</xsl:template>
-->

<xsl:template match="img | IMG">
   <para>
   <figure>
    <title><xsl:value-of select="@title"/></title>
	<mediaobject>
    	<imageobject>
			<imagedata align="center">
				<xsl:if test="@src != ''">
					<xsl:attribute name="fileref"><xsl:value-of select="@src"/></xsl:attribute>
				</xsl:if>
				<xsl:if test="@SRC != ''">
					<xsl:attribute name="fileref"><xsl:value-of select="@SRC"/></xsl:attribute>
				</xsl:if>
            </imagedata>
		</imageobject>
	</mediaobject>
   </figure>
   </para>
</xsl:template>


<xsl:template match="a/img | a/IMG | A/img | A/IMG | td/img | td/IMG | TD/img | TD/IMG |  th/img | th/IMG | TH/img | TH/IMG">
	<inlinegraphic align="center">
		<xsl:if test="@src != ''">
			<xsl:attribute name="fileref"><xsl:value-of select="@src"/></xsl:attribute>
		</xsl:if>
		<xsl:if test="@SRC != ''">
			<xsl:attribute name="fileref"><xsl:value-of select="@SRC"/></xsl:attribute>
		</xsl:if>
	</inlinegraphic>
</xsl:template>

<xsl:template match="center | CENTER">
   <para role="centered">
    	<xsl:apply-templates/>
	</para>
</xsl:template>


<xsl:template match="table | TABLE">
	<para>
		<table>
    	    <title></title>
			<tgroup>
				<xsl:attribute name="cols"><xsl:value-of select="count(tr[last()]/td)"/></xsl:attribute>
				<!-- check if there is a header in this table -->
				<xsl:if test="count(tr[1]/td) = 0"> <!-- counters start by 1 not by 0 -->
					<thead>
						<xsl:for-each select="tr">
        					<xsl:call-template name="tr">
				              	<xsl:with-param name="type">head</xsl:with-param>
							</xsl:call-template>
        		  		</xsl:for-each>
					</thead>
				</xsl:if>

				<tbody>
					<xsl:if test="count(@valign) != 0"><xsl:attribute name="valign"><xsl:value-of select="@valign"/></xsl:attribute></xsl:if>
					<xsl:if test="count(@VALIGN) != 0"><xsl:attribute name="valign"><xsl:value-of select="@VALIGN"/></xsl:attribute></xsl:if>

					<xsl:for-each select="tr">
       					<xsl:call-template name="tr">
			              	<xsl:with-param name="type">body</xsl:with-param>
						</xsl:call-template>
       		  		</xsl:for-each>
				</tbody>

			</tgroup>
		</table>
	</para>
</xsl:template>

<!-- parameters type='head|body' -->
<xsl:template name="tr">
	<!-- used in 'tr' template to distinguish between head and body of a table -->
	<xsl:param name="type"/>

	<xsl:if test="$type = 'head' and not(count(th) = 0)">
		<row>
		   <xsl:apply-templates select="th"/>
		</row>
	</xsl:if>
	<xsl:if test="$type = 'body' and not(count(td) = 0)">
		<row>
		   <xsl:apply-templates select="td"/>
		</row>
	</xsl:if>
</xsl:template>

<xsl:template match="td | th | TD | TH">
	<entry>
		<xsl:if test="count(@valign) != 0"><xsl:attribute name="valign"><xsl:value-of select="@valign"/></xsl:attribute></xsl:if>
		<xsl:if test="count(@VALIGN) != 0"><xsl:attribute name="valign"><xsl:value-of select="@VALIGN"/></xsl:attribute></xsl:if>
		<xsl:if test="count(@align) != 0"><xsl:attribute name="align"><xsl:value-of select="@align"/></xsl:attribute></xsl:if>
		<xsl:if test="count(@ALIGN) != 0"><xsl:attribute name="align"><xsl:value-of select="@ALIGN"/></xsl:attribute></xsl:if>
		<!-- <para>  -->
		   <xsl:apply-templates/>
		<!-- </para> -->
	</entry>
</xsl:template>


<!-- there are three types of links handled by the 'a' tag:
    - external links (mapped to ulink)
	- internal links (cross references) mapped to link
    - and labeling for the crossreference adding the id tag to the parent element
-->
<xsl:template match="a | A">
	<!-- if it is a label for a cross reference -->
	<xsl:if test="@name != ''">
		<anchor>
			<xsl:attribute name="id"><xsl:value-of select="@name"/></xsl:attribute>
		</anchor>
	</xsl:if>
	<xsl:if test="@NAME != ''">
		<anchor>
			<xsl:attribute name="id"><xsl:value-of select="@NAME"/></xsl:attribute>
		</anchor>
	</xsl:if>

	<xsl:if test="@href != ''">
		<xsl:if test="starts-with(@href, '#')">
			<link>
				<xsl:attribute name="linkend"><xsl:value-of select="substring-after(@href,'#')"/></xsl:attribute>
				<xsl:apply-templates/>
			</link>
		</xsl:if>
		<xsl:if test="not(starts-with(@href, '#'))">
			<ulink>
				<xsl:attribute name="url"><xsl:value-of select="@href"/></xsl:attribute>
				<xsl:apply-templates/>
			</ulink>
		</xsl:if>
	</xsl:if>

	<xsl:if test="@HREF != ''">
		<xsl:if test="starts-with(@HREF, '#')">
			<link>
				<xsl:attribute name="linkend"><xsl:value-of select="substring-after(@HREF,'#')"/></xsl:attribute>
				<xsl:apply-templates/>
			</link>
		</xsl:if>
		<xsl:if test="not(starts-with(@HREF, '#'))">
			<ulink>
				<xsl:attribute name="url"><xsl:value-of select="@HREF"/></xsl:attribute>
				<xsl:apply-templates/>
			</ulink>
		</xsl:if>
	</xsl:if>
</xsl:template>



<xsl:template match="pre[@class = 'embed']"> 
	<ulink role="embed">
		<!-- the 'type' is either 'API' or 'CODE' and
             the 'userlevel' is the programming language as defined in 'see' attributes -->
		<xsl:attribute name="type"><xsl:value-of select="substring-before(@style,'.')"/></xsl:attribute>
		<xsl:attribute name="userlevel"><xsl:value-of select="substring-after(@style,'.')"/></xsl:attribute>
		<xsl:attribute name="url"><xsl:value-of select="@title"/></xsl:attribute>
		<!-- all content for this type of element is ignored (since it will be added dynamically
             from the link when converting from docbook to html -->
		<xsl:value-of select="@title"/>
   </ulink>
</xsl:template>


<!-- Error handling  -->
<xsl:template match="*">
  <center><p class="xmlerror"><blink>XSL parsing error</blink><br />
  Can't handle your supplied XML file</p>
  <p><xsl:value-of select="name()" /></p></center>
  <xsl:apply-templates />
</xsl:template>
</xsl:stylesheet>




