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
	<xsl:apply-templates/>
</xsl:template>
<xsl:template match="regular">
	<html xmlns="http://www.w3.org/1999/xhtml">
		<head>
			<meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
			<meta name="Keywords" content="Indelible" />
			<meta name="Description" content="iGeek Indelible Photo Sharing" />
			<meta name="robots" content="index, follow" />
			<meta name="Author" content="iGeek.Inc" />
			<title>iGeek Indelible Photo Sharing</title>
			<link rel="stylesheet" href="/assets/basic.css" type="text/css" />
			<link rel="stylesheet" href="/assets/galleriffic-2.css" type="text/css" /> 
			<script type="text/javascript" src="/assets/jquery-1.3.2.js"></script>
			<script type="text/javascript" src="/assets/jquery.galleriffic.js"></script>
			<script type="text/javascript" src="/assets/jquery.opacityrollover.js"></script>
			<!-- We only want the thunbnails to display when javascript is disabled -->
			<script type="text/javascript">
			<![CDATA[document.write('<style>.noscript { display: none; }</style>');]]>
			</script>
		</head>  
		<body>
			<xsl:apply-templates/>
			<h2>Indelible Infinity Beta</h2>
			<i>Copyright 2013, <a href="http://www.igeekinc.com">iGeek, K.K.</a></i>
		</body>
	</html>
</xsl:template>
<xsl:template match="librariesList">
	<table>
		<xsl:for-each select="library">
			<tr><td><a href="/photos/{id}?listalbums"><img src="/assets/CloudVolume.png"/><xsl:value-of select="name"/>(<xsl:value-of select="id"/>)</a></td></tr>
		</xsl:for-each>
	</table>
</xsl:template>
<xsl:template match="albumslist">
	<div id="page">
		<div id="container">
			<h1><xsl:value-of select="library"/></h1><br/>
			<h1>Albums</h1><br/>
			<table>
				<xsl:for-each select="album[position() mod 6 = 1]">
					<xsl:variable name = "current-pos" select="(position()-1) * 6 + 1"/>
					<tr>
						<xsl:for-each select="../album[position() &gt;= $current-pos and position() &lt; ($current-pos + 6)]">
							<td align="center">
								<a href="{../path}/{guid}?listphotos">
									<img src="{../path}/{guid}/{firstImageKey}?getimage&amp;format=width=80,height=80" alt="{name}" /><br/>
									<xsl:value-of select="name"/>
								</a>
							</td>
						</xsl:for-each>
					</tr>
				</xsl:for-each>
			</table>
		</div>
	</div>
</xsl:template>
<xsl:template match="albumsandfaceslist">
	<div id="page">
		<div id="container">
			<h1><xsl:value-of select="library"/></h1><br/>
			<h1>Albums</h1><br/>
			<table>
				<xsl:for-each select="album[position() mod 5 = 1]">
					<xsl:variable name = "current-pos" select="(position()-1) * 5 + 1"/>
					<tr>
						<xsl:for-each select="../album[position() &gt;= $current-pos and position() &lt; ($current-pos + 5)]">
							<td align="center">
								<a href="{../path}/{guid}?listphotos">
									<img src="{../path}/{guid}/{firstImageKey}?getimage&amp;format=width=150,height=150" alt="{name}" /><br/>
									<xsl:value-of select="name"/>
								</a>
							</td>
						</xsl:for-each>
					</tr>
				</xsl:for-each>
			</table>
			<h1>People</h1><br/>
			<table>
				<xsl:for-each select="face[position() mod 5 = 1]">
					<xsl:variable name = "current-pos" select="(position()-1) * 5 + 1"/>
					<tr>
						<xsl:for-each select="../face[position() &gt;= $current-pos and position() &lt; ($current-pos + 5)]">
							<td align="center">
								<a href="{../path}/{key}?imagesForFace">
									<img src="{../path}/{key}?getface" width="150" height="150"/><br/>
									<xsl:value-of select="name"/>
								</a>
							</td>
						</xsl:for-each>
					</tr>
				</xsl:for-each>
			</table>
		</div>
	</div>
</xsl:template>
<xsl:template match="photoslist">
	<div id="page">
		<div id="container">
			<h1><a href="{libraryPath}?listalbumsandfaces"><xsl:value-of select="library"/></a></h1>
			<h2><xsl:value-of select="albumName"/></h2>
			<a href="{path}?largeslideshow">フルスクリーンでスライドショー</a>
			<div id="gallery" class="content">
				<div id="controls" class="controls"></div>
				<div class="slideshow-container">
					<div id="loading" class="loader"></div>
					<div id="slideshow" class="slideshow"></div>
				</div>
				<div id="caption" class="caption-container"></div>
			</div>
			<div id="thumbs" class="navigation">
				<ul class="thumbs noscript">
					<xsl:for-each select="image">
		        		<li>
		            		<a class="thumb" name="optionalCustomIdentifier" href="{../path}/{key}?getimage&amp;format=height=350" title="{title}">
		                		<img src="{../path}/{key}?getimage&amp;format=width=80,height=80" alt="{title}" />
		            		</a>
		            		<div class="caption">
								<div class="download">
									<a href="{../path}/{key}?getimage">フルサイズで表示 (Original Size)</a>
								</div>
								<br/>
								<div class="download">
									<a href="{../path}/{../albumName}.zip?zipFileForAlbum"><xsl:value-of select="../albumName"/>の写真を一括ダウンロード (Download all for <xsl:value-of select="../albumName"/>)</a>
								</div>
								<p>
								In this picture:
									<xsl:for-each select="face">
										<a href="{../../volumePath}/{faceKey}?imagesForFace"><xsl:value-of select="name"/></a><xsl:text disable-output-escaping="yes">&amp;nbsp;</xsl:text>
									</xsl:for-each>
								</p>
		            		</div>
		        		</li>
					</xsl:for-each>
				</ul>
			</div>
			<div style="clear: both;"></div>
		</div>
	</div>
	<xsl:call-template name="addGalleryScript"/>
</xsl:template>
<xsl:template match="facelist">
	<table>
		<xsl:for-each select="face">
			<tr><td><a href="{../path}/{key}?imagesForFace">
			<img src="{../path}/{key}?getface" width="150" height="150"/><br/>
			<xsl:value-of select="name"/></a></td></tr>
		</xsl:for-each>
	</table>
</xsl:template>
<xsl:template match="faceimagelist">
	<div id="page">
		<div id="container">
			<h1><a href="{libraryPath}?listalbumsandfaces"><xsl:value-of select="library"/></a></h1>
			<h2><xsl:value-of select="name"/></h2>
			<a href="{path}?largeslideshowforperson">フルスクリーンでスライドショー (Large Slideshow)</a>
			<div id="gallery" class="content">
				<div id="controls" class="controls"></div>
				<div class="slideshow-container">
					<div id="loading" class="loader"></div>
					<div id="slideshow" class="slideshow"></div>
				</div>
				<div id="caption" class="caption-container"></div>
			</div>
			<div id="thumbs" class="navigation">
				<ul class="thumbs noscript">
					<xsl:for-each select="image">
						<li>
							<a href="{../path}/{key}?imageforface&amp;format=height=350" class="thumb"><img src="{../path}/{key}?imageforface&amp;format=width=80,height=80"/></a>
							<div class="caption">
								<div class="download">
									<a href="{../path}/{key}?imageforface">フルサイズで表示 (Original Size)</a>
								</div>
								<br/>
								<div class="download">
									<a href="{../volumePath}/{../faceKey}/{../name}.zip?zipFileForFace"> <xsl:value-of select="../name"/>の写真を一括ダウンロード (Download all for <xsl:value-of select="../name"/>)</a>
								</div>
								<p>
								In this picture:
									<xsl:for-each select="face">
										<a href="{../../volumePath}/{faceKey}?imagesForFace"><xsl:value-of select="name"/></a><xsl:text disable-output-escaping="yes">&amp;nbsp;</xsl:text>
									</xsl:for-each>
								</p>
							</div>
						</li>
					</xsl:for-each>
				</ul>
			</div>
		</div>
	</div>
	<xsl:call-template name="addGalleryScript"/>
</xsl:template>
<xsl:template name="addGalleryScript">
	<script type="text/javascript">
			<![CDATA[jQuery(document).ready(function($) {
				// We only want these styles applied when javascript is enabled
				$('div.navigation').css({'width' : '300px', 'float' : 'left'});
				$('div.content').css('display', 'block');

				// Initially set opacity on thumbs and add
				// additional styling for hover effect on thumbs
				var onMouseOutOpacity = 0.67;
				$('#thumbs ul.thumbs li').opacityrollover({
					mouseOutOpacity:   onMouseOutOpacity,
					mouseOverOpacity:  1.0,
					fadeSpeed:         'fast',
					exemptionSelector: '.selected'
				});
				
				// Initialize Advanced Galleriffic Gallery
				var gallery = $('#thumbs').galleriffic({
					delay:                     2500,
					numThumbs:                 15,
					preloadAhead:              10,
					enableTopPager:            true,
					enableBottomPager:         true,
					maxPagesToShow:            7,
					imageContainerSel:         '#slideshow',
					controlsContainerSel:      '#controls',
					captionContainerSel:       '#caption',
					loadingContainerSel:       '#loading',
					renderSSControls:          true,
					renderNavControls:         true,
					playLinkText:              'スライドショー開始　(Slideshow)',
					pauseLinkText:             'Pause Slideshow',
					prevLinkText:              '&lsaquo; 前の写真 (Prev)',
					nextLinkText:              '次の写真 &rsaquo; (Next)',
					nextPageLinkText:          'Next &rsaquo;',
					prevPageLinkText:          '&lsaquo; Prev',
					enableHistory:             false,
					autoStart:                 false,
					syncTransitions:           true,
					defaultTransitionDuration: 900,
					onSlideChange:             function(prevIndex, nextIndex) {
						// 'this' refers to the gallery, which is an extension of $('#thumbs')
						this.find('ul.thumbs').children()
							.eq(prevIndex).fadeTo('fast', onMouseOutOpacity).end()
							.eq(nextIndex).fadeTo('fast', 1.0);
					},
					onPageTransitionOut:       function(callback) {
						this.fadeTo('fast', 0.0, callback);
					},
					onPageTransitionIn:        function() {
						this.fadeTo('fast', 1.0);
					}
				});
			});]]>
	</script>
</xsl:template>
<xsl:template match="largeslide">
<html xmlns="http://www.w3.org/1999/xhtml">
	<head>
		<title><xsl:value-of select="web/photoslist/albumName"/></title>
		<meta http-equiv="content-type" content="text/html; charset=UTF-8" />
		
		<link rel="stylesheet" href="/assets/supersized.css" type="text/css" media="screen" />
		<link rel="stylesheet" href="/assets/supersized.shutter.css" type="text/css" media="screen" />
		
		<script type="text/javascript" src="https://ajax.googleapis.com/ajax/libs/jquery/1.6.1/jquery.min.js"></script>
		<script type="text/javascript" src="/assets/jquery.easing.min.js"></script>
		
		<script type="text/javascript" src="/assets/supersized.3.2.7.min.js"></script>
		<script type="text/javascript" src="/assets/supersized.shutter.min.js"></script>
		
		<script type="text/javascript">
			<![CDATA[
			jQuery(function($){
				
				$.supersized({
				
					// Functionality
					slideshow               :   1,			// Slideshow on/off
					autoplay				:	1,			// Slideshow starts playing automatically
					start_slide             :   1,			// Start slide (0 is random)
					stop_loop				:	0,			// Pauses slideshow on last slide
					random					: 	0,			// Randomize slide order (Ignores start slide)
					slide_interval          :   3000,		// Length between transitions
					transition              :   6, 			// 0-None, 1-Fade, 2-Slide Top, 3-Slide Right, 4-Slide Bottom, 5-Slide Left, 6-Carousel Right, 7-Carousel Left
					transition_speed		:	1000,		// Speed of transition
					new_window				:	1,			// Image links open in new window/tab
					pause_hover             :   0,			// Pause slideshow on hover
					keyboard_nav            :   1,			// Keyboard navigation on/off
					performance				:	1,			// 0-Normal, 1-Hybrid speed/quality, 2-Optimizes image quality, 3-Optimizes transition speed // (Only works for Firefox/IE, not Webkit)
					image_protect			:	0,			// Disables image dragging and right click with Javascript
															   
					// Size & Position						   
					min_width		        :   0,			// Min width allowed (in pixels)
					min_height		        :   0,			// Min height allowed (in pixels)
					vertical_center         :   1,			// Vertically center background
					horizontal_center       :   1,			// Horizontally center background
					fit_always				:	1,			// Image will never exceed browser width or height (Ignores min. dimensions)
					fit_portrait         	:   0,			// Portrait images will not exceed browser height
					fit_landscape			:   0,			// Landscape images will not exceed browser width
															   
					// Components							
					slide_links				:	'blank',	// Individual links for each slide (Options: false, 'num', 'name', 'blank')
					thumb_links				:	1,			// Individual thumb links for each slide
					thumbnail_navigation    :   0,			// Thumbnail navigation
					slides 					:  	[			// Slideshow Images]]>
					<xsl:for-each select="web/photoslist/image">
						<![CDATA[{image : ']]><xsl:value-of select="../path"/>/<xsl:value-of select="key"/>?getimage&amp;format=width=1920<![CDATA[', title : ']]><xsl:value-of select="title"/><![CDATA[', thumb : ']]><xsl:value-of select="../path"/>/<xsl:value-of select="key"/>?getimage&amp;format=height=150,width=150"<![CDATA[', url : ']]><xsl:value-of select="../path"/>/<xsl:value-of select="key"/>?getimage<![CDATA['},]]>						
					</xsl:for-each>
					<![CDATA[							
												],
												
					// Theme Options			   
					progress_bar			:	1,			// Timer for each slide							
					mouse_scrub				:	0
					
				});
		    });
		    ]]>
		</script>
		
	</head>
	
	<style type="text/css">
		ul#demo-block{ margin:0 15px 15px 15px; }
			ul#demo-block li{ margin:0 0 10px 0; padding:10px; display:inline; float:left; clear:both; color:#aaa; background:url('/assets/bg-black.png'); font:11px Helvetica, Arial, sans-serif; }
			ul#demo-block li a{ color:#eee; font-weight:bold; }
	</style>

	<body>
		<!--Thumbnail Navigation-->
		<div id="prevthumb"></div>
		<div id="nextthumb"></div>
		
		<!--Arrow Navigation-->
		<a id="prevslide" class="load-item"></a>
		<a id="nextslide" class="load-item"></a>
		
		<div id="thumb-tray" class="load-item">
			<div id="thumb-back"></div>
			<div id="thumb-forward"></div>
		</div>
		
		<!--Time Bar-->
		<div id="progress-back" class="load-item">
			<div id="progress-bar"></div>
		</div>
		
		<!--Control Bar-->
		<div id="controls-wrapper" class="load-item">
			<div id="controls">
				
				<a id="play-button"><img id="pauseplay" src="/assets/pause.png"/></a>
			
				<!--Slide counter-->
				<div id="slidecounter">
					<span class="slidenumber"></span> / <span class="totalslides"></span>
				</div>
				
				<!--Slide captions displayed here-->
				<div id="slidecaption"></div>
				
				<!--Thumb Tray button-->
				<a id="tray-button"><img id="tray-arrow" src="/assets/button-tray-up.png"/></a>
				
				<!--Navigation-->
				<ul id="slide-list"></ul>
				
			</div>
		</div>
	</body>
</html>

</xsl:template>
<xsl:template match="largefaceslide">
<html xmlns="http://www.w3.org/1999/xhtml">
	<head>
		<title><xsl:value-of select="web/photoslist/albumName"/></title>
		<meta http-equiv="content-type" content="text/html; charset=UTF-8" />
		
		<link rel="stylesheet" href="/assets/supersized.css" type="text/css" media="screen" />
		<link rel="stylesheet" href="/assets/supersized.shutter.css" type="text/css" media="screen" />
		
		<script type="text/javascript" src="https://ajax.googleapis.com/ajax/libs/jquery/1.6.1/jquery.min.js"></script>
		<script type="text/javascript" src="/assets/jquery.easing.min.js"></script>
		
		<script type="text/javascript" src="/assets/supersized.3.2.7.min.js"></script>
		<script type="text/javascript" src="/assets/supersized.shutter.min.js"></script>
		
		<script type="text/javascript">
			<![CDATA[
			jQuery(function($){
				
				$.supersized({
				
					// Functionality
					slideshow               :   1,			// Slideshow on/off
					autoplay				:	1,			// Slideshow starts playing automatically
					start_slide             :   1,			// Start slide (0 is random)
					stop_loop				:	0,			// Pauses slideshow on last slide
					random					: 	0,			// Randomize slide order (Ignores start slide)
					slide_interval          :   3000,		// Length between transitions
					transition              :   6, 			// 0-None, 1-Fade, 2-Slide Top, 3-Slide Right, 4-Slide Bottom, 5-Slide Left, 6-Carousel Right, 7-Carousel Left
					transition_speed		:	1000,		// Speed of transition
					new_window				:	1,			// Image links open in new window/tab
					pause_hover             :   0,			// Pause slideshow on hover
					keyboard_nav            :   1,			// Keyboard navigation on/off
					performance				:	1,			// 0-Normal, 1-Hybrid speed/quality, 2-Optimizes image quality, 3-Optimizes transition speed // (Only works for Firefox/IE, not Webkit)
					image_protect			:	0,			// Disables image dragging and right click with Javascript
															   
					// Size & Position						   
					min_width		        :   0,			// Min width allowed (in pixels)
					min_height		        :   0,			// Min height allowed (in pixels)
					vertical_center         :   1,			// Vertically center background
					horizontal_center       :   1,			// Horizontally center background
					fit_always				:	1,			// Image will never exceed browser width or height (Ignores min. dimensions)
					fit_portrait         	:   0,			// Portrait images will not exceed browser height
					fit_landscape			:   0,			// Landscape images will not exceed browser width
															   
					// Components							
					slide_links				:	'blank',	// Individual links for each slide (Options: false, 'num', 'name', 'blank')
					thumb_links				:	1,			// Individual thumb links for each slide
					thumbnail_navigation    :   0,			// Thumbnail navigation
					slides 					:  	[			// Slideshow Images]]>
					<xsl:for-each select="web/photoslist/image">
						<![CDATA[{image : ']]><xsl:value-of select="../path"/>/<xsl:value-of select="key"/>?imageforface&amp;format=width=1920<![CDATA[', title : ']]><xsl:value-of select="title"/><![CDATA[', thumb : ']]><xsl:value-of select="../path"/>/<xsl:value-of select="key"/>?imageforface&amp;format=height=150,width=150"<![CDATA[', url : ']]><xsl:value-of select="../path"/>/<xsl:value-of select="key"/>?imageforface<![CDATA['},]]>						
					</xsl:for-each>
					<![CDATA[							
												],
												
					// Theme Options			   
					progress_bar			:	1,			// Timer for each slide							
					mouse_scrub				:	0
					
				});
		    });
		    ]]>
		</script>
		
	</head>
	
	<style type="text/css">
		ul#demo-block{ margin:0 15px 15px 15px; }
			ul#demo-block li{ margin:0 0 10px 0; padding:10px; display:inline; float:left; clear:both; color:#aaa; background:url('/assets/bg-black.png'); font:11px Helvetica, Arial, sans-serif; }
			ul#demo-block li a{ color:#eee; font-weight:bold; }
	</style>

	<body>
		<!--Thumbnail Navigation-->
		<div id="prevthumb"></div>
		<div id="nextthumb"></div>
		
		<!--Arrow Navigation-->
		<a id="prevslide" class="load-item"></a>
		<a id="nextslide" class="load-item"></a>
		
		<div id="thumb-tray" class="load-item">
			<div id="thumb-back"></div>
			<div id="thumb-forward"></div>
		</div>
		
		<!--Time Bar-->
		<div id="progress-back" class="load-item">
			<div id="progress-bar"></div>
		</div>
		
		<!--Control Bar-->
		<div id="controls-wrapper" class="load-item">
			<div id="controls">
				
				<a id="play-button"><img id="pauseplay" src="/assets/pause.png"/></a>
			
				<!--Slide counter-->
				<div id="slidecounter">
					<span class="slidenumber"></span> / <span class="totalslides"></span>
				</div>
				
				<!--Slide captions displayed here-->
				<div id="slidecaption"></div>
				
				<!--Thumb Tray button-->
				<a id="tray-button"><img id="tray-arrow" src="/assets/button-tray-up.png"/></a>
				
				<!--Navigation-->
				<ul id="slide-list"></ul>
				
			</div>
		</div>
	</body>
</html>

</xsl:template>
</xsl:stylesheet>