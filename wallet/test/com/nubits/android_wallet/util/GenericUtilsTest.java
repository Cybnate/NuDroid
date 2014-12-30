/*
 * Copyright 2013-2014 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.matthewmitchell.nubits_android_wallet.util;

import static org.junit.Assert.assertEquals;

import java.math.BigInteger;

import org.junit.Test;

import com.matthewmitchell.nubits_android_wallet.util.GenericUtils;
import com.matthewmitchell.nubitsj.core.NetworkParameters;

/**
 * @author Andreas Schildbach
 */
public class GenericUtilsTest
{
	@Test
	public void formatValue() throws Exception
	{
		final BigInteger coin = new BigInteger("10000");
		assertEquals("1.00", GenericUtils.formatValue(coin, 4, 0));

		final BigInteger justNot = new BigInteger("9999");
		assertEquals("0.9999", GenericUtils.formatValue(justNot, 4, 0));

		final BigInteger slightlyMore = new BigInteger("10001");
		assertEquals("1.0001", GenericUtils.formatValue(slightlyMore, 4, 0));

		final BigInteger value = new BigInteger("11223344556677");
		assertEquals("1122334455.6677", GenericUtils.formatValue(value, 4, 0));

	}

}
