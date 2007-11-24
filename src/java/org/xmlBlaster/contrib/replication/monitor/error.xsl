<?xml version='1.0' encoding='UTF-8' ?>
<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>
	<xsl:output method='xml' version='1.0' encoding='UTF-8' indent='yes' />
	<xsl:template match="@*|*|text()|processing-instruction()">
		<xsl:copy>
			<xsl:apply-templates select="@*|*|text()|processing-instruction()"/>
		</xsl:copy>
	</xsl:template>
</xsl:stylesheet>
