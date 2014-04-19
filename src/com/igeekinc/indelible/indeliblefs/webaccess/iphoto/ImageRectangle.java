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

import java.text.MessageFormat;
import java.text.ParseException;

/**
 * Handles an iPhoto style rectangle.  X & Y are specified as doubles
 * as percentages of the photo???
 * @author dave
 *
 */
public class ImageRectangle
{
	// <key>rectangle</key><string>{{0.463735, 0.434317}, {0.058449, 0.087674}}</string>
	private double x1, y1, x2, y2;
	public ImageRectangle(String rectangleString) throws ParseException
	{
		MessageFormat rectangleParser = new MessageFormat("'{{'{0, number}, {1, number}'}', '{'{2,number}, {3, number}'}}'");
		Object [] numbers = rectangleParser.parse(rectangleString);
		if (numbers == null || numbers.length != 4)
			throw new NumberFormatException("Not enough numbers in rectangle");
		if (numbers[0] == null || !(numbers[0] instanceof Number))
			throw new NumberFormatException("Number 1 of rectangle is not a number");
		if (numbers[1] == null || !(numbers[1] instanceof Number))
			throw new NumberFormatException("Number 1 of rectangle is not a number");
		if (numbers[2] == null || !(numbers[2] instanceof Number))
			throw new NumberFormatException("Number 1 of rectangle is not a number");
		if (numbers[3] == null || !(numbers[3] instanceof Number))
			throw new NumberFormatException("Number 1 of rectangle is not a number");
		x1 = ((Number)numbers[0]).doubleValue();
		y1 = ((Number)numbers[1]).doubleValue();
		x2 = ((Number)numbers[2]).doubleValue();
		y2 = ((Number)numbers[3]).doubleValue();
	}
	public double getX1()
	{
		return x1;
	}
	public double getY1()
	{
		return y1;
	}
	public double getX2()
	{
		return x2;
	}
	public double getY2()
	{
		return y2;
	}
	
	public String toString()
	{
		return "Rectangle: "+x1+", "+y1+":"+x2+", "+y2;
	}
}
