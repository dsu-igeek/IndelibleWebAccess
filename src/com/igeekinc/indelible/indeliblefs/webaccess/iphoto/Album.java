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

import java.util.ArrayList;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSString;

public class Album
{
	public final static String kAlbumIDKey = "AlbumId";
	public final static String kAlbumNameKey = "AlbumName";
	public final static String kAlbumTypeKey = "Album Type";
	public final static String kGUIDKey = "GUID";
	public final static String kMasterKey = "Master";
	public final static String kTransitionSpeedKey = "TransitionSpeed";
	public final static String kShuffleSlidesKey = "ShuffleSlides";
	public final static String kKeyListKey = "KeyList";
	public final static String kPhotoCountKey = "PhotoCount";
	
	private iPhotoLibrary parent;
	private int albumID;
	private String albumName;
	private String albumType;
	private String guid;
	private boolean master;
	private double transitionSpeed;
	private boolean shuffleSlides;
	private ArrayList<String>keyList;
	private int photoCount;
	
	public Album(iPhotoLibrary parent, NSDictionary albumDictionary)
	{
		this.parent = parent;
		albumID = ((NSNumber)albumDictionary.objectForKey(kAlbumIDKey)).intValue();
		albumName = ((NSString)albumDictionary.objectForKey(kAlbumNameKey)).toString();
		albumType = ((NSString)albumDictionary.objectForKey(kAlbumTypeKey)).toString();
		guid = ((NSString)albumDictionary.objectForKey(kGUIDKey)).toString();
		NSNumber masterObj = (NSNumber)albumDictionary.objectForKey(kMasterKey);
		if (masterObj != null)
			master = masterObj.boolValue();
		else
			master = false;
		NSNumber transitionSpeedObj = (NSNumber)albumDictionary.objectForKey(kTransitionSpeedKey);
		if (transitionSpeedObj != null)
			transitionSpeed = transitionSpeedObj.doubleValue();
		else
			transitionSpeed = 0.0;
		NSNumber shuffleSlidesObj = (NSNumber)albumDictionary.objectForKey(kShuffleSlidesKey);
		if (shuffleSlidesObj != null)
			shuffleSlides = shuffleSlidesObj.boolValue();
		else
			shuffleSlides = false;
		NSArray keyListNSArray = ((NSArray)albumDictionary.objectForKey(kKeyListKey));
		keyList = new ArrayList<String>();
		for (int curKeyNum = 0; curKeyNum < keyListNSArray.count(); curKeyNum++)
		{
			keyList.add(((NSString)keyListNSArray.objectAtIndex(curKeyNum)).toString());
		}
		photoCount =  ((NSNumber)albumDictionary.objectForKey(kPhotoCountKey)).intValue();
	}

	public int getAlbumID()
	{
		return albumID;
	}

	public String getAlbumName()
	{
		return albumName;
	}

	public String getAlbumType()
	{
		return albumType;
	}

	public String getGUID()
	{
		return guid;
	}

	public boolean isMaster()
	{
		return master;
	}

	public double getTransitionSpeed()
	{
		return transitionSpeed;
	}

	public boolean isShuffleSlides()
	{
		return shuffleSlides;
	}
	
	public int getNumPhotos()
	{
		return keyList.size();
	}
	
	public Image getImageAtIndex(int index)
	{
		return parent.getImageForKey(keyList.get(index));
	}
	
	public String [] getKeys()
	{
		String [] returnKeys = new String[keyList.size()];
		returnKeys = keyList.toArray(returnKeys);
		return returnKeys;
	}
	
	public Image getImageForKey(String key)
	{
		// We check the key list so that people can't get around permission restrictions on the album
		if (keyList.contains(key))
			return parent.getImageForKey(key);
		return null;
	}
}
