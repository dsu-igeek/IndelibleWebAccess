<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE xsl:stylesheet  [
	<!ENTITY nbsp   "&#160;">
	<!ENTITY copy   "&#169;">
	<!ENTITY reg    "&#174;">
	<!ENTITY trade  "&#8482;">
	<!ENTITY mdash  "&#8212;">
	<!ENTITY ldquo  "&#8220;">
	<!ENTITY rdquo  "&#8221;"> 
	<!ENTITY pound  "&#163;">
	<!ENTITY yen    "&#165;">
	<!ENTITY euro   "&#8364;">
]>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
<xsl:output method="html" encoding="UTF-8" doctype-public="-//W3C//DTD XHTML 1.0 Strict//EN" doctype-system="http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd" indent="yes" version="4.0"/>
<xsl:template match="/">
<html xmlns="http://www.w3.org/1999/xhtml">
<head>
<meta http-equiv="Content-Type" content="text/html; charset=Shift_JIS" />
<meta name="Keywords" content="Indelible" />
<meta name="Description" content="iGeek Indelible File Sharing" />
<meta name="robots" content="index, follow" />
<meta name="Author" content="iGeek.Inc" />
<title>iGeek Indelible File Sharing</title>
</head>  
<body>
<xsl:apply-templates/>
</body>
</html>
</xsl:template>
<xsl:template match="volumesList">
	<table>
		<xsl:for-each select="volume">
			<tr><td><a href="/volumes/{id}?list"><img src="/assets/CloudVolume.png"/><xsl:value-of select="name"/>(<xsl:value-of select="id"/>)</a></td></tr>
		</xsl:for-each>
	</table>
</xsl:template>
<xsl:template match="list">
	<table>
		<tr><td>
		<xsl:if test="string-length(normalize-space(parentPath))>0">
			<a href="/volumes/{parentPath}?list"><img src="/assets/ReturnArrow.png"/></a>
		</xsl:if>
		<xsl:if test="string-length(normalize-space(parentPath))=0">
			<a href="/volumes?listvolumes"><img src="/assets/ReturnArrow.png"/></a>
		</xsl:if>
		</td><td><form name="uploadForm"  method="POST" enctype="multipart/form-data" action="/volumes/{path}?create">File to upload: <input type="file" name="upfile"/><input type="submit" value="Create"/></form></td></tr>
	</table>
	<table>	
		<xsl:for-each select="file">
			<xsl:if test="normalize-space(directory)='N'">
				<tr><td><img src="/assets/BasicCloudFile.png"/></td><td><a href="/volumes/{path}?get"><xsl:value-of select="name"/></a></td><td><xsl:value-of select="length"/></td><td><a href="/volumes/{path}?delete"><img src="/assets/TrashCan.png"/></a></td></tr>
			</xsl:if>
			<xsl:if test="normalize-space(directory)='Y'">
				<tr><td><img src="/assets/BasicCloudFolder.png"/></td><td><a href="/volumes/{path}?list"><xsl:value-of select="name"/></a></td><td><xsl:value-of select="length"/></td><td></td></tr>
			</xsl:if>
		</xsl:for-each>
	</table>
</xsl:template>
</xsl:stylesheet>