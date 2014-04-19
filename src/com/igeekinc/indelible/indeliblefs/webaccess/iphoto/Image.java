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
import java.util.ArrayList;
import java.util.Date;

import org.apache.log4j.Logger;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSString;
import com.igeekinc.util.FilePath;
import com.igeekinc.util.logging.ErrorLogMessage;

public class Image
{
	public static final String kCaptionKey = "Caption";
	public static final String kCommentKey = "Comment";
	public static final String kGUIDKey = "GUID";
	public static final String kRollKey = "Roll";
	public static final String kRatingKey = "Rating";
	public static final String kImagePathKey = "ImagePath";
	public static final String kThumbPathKey = "ThumbPath";
	public static final String kFacesPathKey = "Faces";
	public static final String kModDateKey = "ModDateAsTimerInterval";
	public static final String kDateGMTKey = "DateAsTimerIntervalGMT";

	private String key;
	private iPhotoLibrary parent;
	private String caption;
	private String comment;
	private String guid;
	private int roll;
	private int rating;
	private FilePath imagePath;
	private String mediaType;
	private Date modDate, date;
	private FilePath thumbPath;
	private ArrayList<ImageFace>faces = new ArrayList<ImageFace>();
	public Image(String key, iPhotoLibrary parent, NSDictionary imageDictionary)
	{
		this.key = key;
		this.parent = parent;
		caption = ((NSString)imageDictionary.objectForKey(kCaptionKey)).toString();
		comment = ((NSString)imageDictionary.objectForKey(kCommentKey)).toString();
		guid = ((NSString)imageDictionary.objectForKey(kGUIDKey)).toString();
		roll = ((NSNumber)imageDictionary.objectForKey(kRollKey)).intValue();
		rating = ((NSNumber)imageDictionary.objectForKey(kRatingKey)).intValue();
		String imagePathStr = ((NSString)imageDictionary.objectForKey(kImagePathKey)).toString();
		imagePath = FilePath.getFilePath(imagePathStr);
		String thumbPathStr = ((NSString)imageDictionary.objectForKey(kThumbPathKey)).toString();
		thumbPath = FilePath.getFilePath(thumbPathStr);
		NSArray facesArray = (NSArray)imageDictionary.objectForKey(kFacesPathKey);
		if (facesArray != null)
		{
			for (int curFaceNum = 0; curFaceNum < facesArray.count(); curFaceNum++)
			{
				NSDictionary curFaceDict = (NSDictionary)facesArray.objectAtIndex(curFaceNum);
				try
				{
					ImageFace curFace = new ImageFace(parent, curFaceDict);
					faces.add(curFace);
				} catch (ParseException e)
				{
					// TODO Auto-generated catch block
					Logger.getLogger(getClass()).error(new ErrorLogMessage("Caught exception"), e);
				}
			}
		}
		Double modDateReal = ((NSNumber)imageDictionary.objectForKey(kModDateKey)).doubleValue();
		if (modDateReal != null)
		{
			modDate = new CFAbsoluteTime(modDateReal);
		}
		else
		{
			modDate = new Date(0);
		}
		
		Double dateReal = ((NSNumber)imageDictionary.objectForKey(kModDateKey)).doubleValue();
		if (dateReal != null)
		{
			date = new CFAbsoluteTime(dateReal);
		}
		else
		{
			date = new Date(0);
		}
	}
	public String getKey()
	{
		return key;
	}
	public String getCaption()
	{
		return caption;
	}
	public String getComment()
	{
		return comment;
	}
	public String getGUID()
	{
		return guid;
	}
	public int getRoll()
	{
		return roll;
	}
	public int getRating()
	{
		return rating;
	}
	public FilePath getImagePath()
	{
		return imagePath;
	}
	public String getMediaType()
	{
		return mediaType;
	}
	public Date getModDate()
	{
		return modDate;
	}
	
	public Date getDate()
	{
		return date;
	}
	
	public FilePath getThumbPath()
	{
		return thumbPath;
	}
	
	public String getImageMIMEType()
	{
		return getMIMETypeForFileExtension(imagePath);
	}
	
	public String getThumbMIMEType()
	{
		return getMIMETypeForFileExtension(thumbPath);
	}
	private String getMIMETypeForFileExtension(FilePath path)
	{
		String fileSuffix = path.getSuffix();
		if (fileSuffix.toLowerCase().equals("jpg") || fileSuffix.toLowerCase().equals("jpeg"))
			return "jpeg";
		if (fileSuffix.toLowerCase().equals("png"))
			return "png";
		if (fileSuffix.toLowerCase().equals("gif"))
			return "gif";
		return "jpeg";	// A reasonable default?
	}
	
	public ImageFace [] getFaces()
	{
		ImageFace [] returnFaces = new ImageFace[faces.size()];
		returnFaces = faces.toArray(returnFaces);
		return returnFaces;
	}
}
