/*
 * Copyright 2002-2014 iGeek, Inc.
 * All Rights Reserved
 * @Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.@
 */
 
package com.igeekinc.indelible.indeliblefs.webaccess.iphoto;

import java.text.ParseException;

import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSString;

/**
 * One of these exists for each Face identified in an Image.  
 * @author dave
 *
 */
public class ImageFace
{
	private String faceKey;
	private ImageRectangle imageRectangle;
	private int faceIndex;
	/*
	 * <dict>
     * <key>face key</key><string>1439</string> 
     * <key>rectangle</key><string>{{0.463735, 0.434317}, {0.058449, 0.087674}}</string>
     * <key>face index</key><integer>3</integer>
     * </dict>
	 */
	public static final String kFaceKeyPropertyName = "face key";
	public static final String kRectanglePropertyName = "rectangle";
	public static final String kFaceIndexPropertyName = "face index";
	
	public ImageFace(iPhotoLibrary parent, NSDictionary imageFaceDictionary) throws ParseException
	{
		faceKey = ((NSString)imageFaceDictionary.objectForKey(kFaceKeyPropertyName)).toString();
		String rectangleString = ((NSString)imageFaceDictionary.objectForKey(kRectanglePropertyName)).toString();
		imageRectangle = new ImageRectangle(rectangleString);
		faceIndex = ((NSNumber)imageFaceDictionary.objectForKey(kFaceIndexPropertyName)).intValue();
	}

	public String getFaceKey()
	{
		return faceKey;
	}

	public int getFaceIndex()
	{
		return faceIndex;
	}
	
	public ImageRectangle getImageRectangle()
	{
		return imageRectangle;
	}
}
