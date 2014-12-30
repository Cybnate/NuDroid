/*
 * Copyright 2011-2014 the original author or authors.
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

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Currency;
import java.util.Locale;

import javax.annotation.Nonnull;

import android.view.View;
import android.widget.TextView;

import com.matthewmitchell.nubitsj.core.NetworkParameters;

import com.matthewmitchell.nubits_android_wallet.Constants;

/**
 * @author Andreas Schildbach
 */
public class GenericUtils
{
	public static final BigInteger ONE_NBT = new BigInteger("10000", 10);

	private static final int ONE_NBT_INT = ONE_NBT.intValue();

	public static String formatValue(@Nonnull final BigInteger value, final int precision, final int shift)
	{
		return formatValue(value, "", "-", precision, shift);
	}

	public static String formatValue(@Nonnull final BigInteger value, @Nonnull final String plusSign, @Nonnull final String minusSign,
			final int precision, final int shift)
	{
		long longValue = value.longValue();

		final String sign = longValue < 0 ? minusSign : plusSign;

		if (shift == 0)
		{
			if  (precision == 2)
				longValue -= longValue % 100 + longValue % 100 / 50 * 100;
			else if (precision != 4)
				throw new IllegalArgumentException("cannot handle precision/shift: " + precision + "/" + shift);

			final long absValue = Math.abs(longValue);
			final long coins = absValue / ONE_NBT_INT;
			final int satoshis = (int) (absValue % ONE_NBT_INT);

			if (satoshis % 100 == 0)
				return String.format(Locale.US, "%s%d.%02d", sign, coins, satoshis / 100);
			else
				return String.format(Locale.US, "%s%d.%04d", sign, coins, satoshis);
		}
		else
		{
			throw new IllegalArgumentException("cannot handle shift: " + shift);
		}
	}

	public static String formatDebugValue(@Nonnull final BigInteger value)
	{
		return formatValue(value, Constants.NBT_MAX_PRECISION, 0);
	}

	public static BigInteger parseCoin(final String str, final int shift) throws ArithmeticException
	{
		final BigInteger coin = new BigDecimal(str).movePointRight(4 - shift).toBigInteger();

		if (coin.signum() < 0)
			throw new ArithmeticException("negative amount: " + str);
		if (coin.compareTo(NetworkParameters.MAX_MONEY) > 0)
			throw new ArithmeticException("amount too large: " + str);

		return coin;
	}

	public static boolean startsWithIgnoreCase(final String string, final String prefix)
	{
		return string.regionMatches(true, 0, prefix, 0, prefix.length());
	}

	public static void setNextFocusForwardId(final View view, final int nextFocusForwardId)
	{
		try
		{
			final Method setNextFocusForwardId = TextView.class.getMethod("setNextFocusForwardId", Integer.TYPE);
			setNextFocusForwardId.invoke(view, nextFocusForwardId);
		}
		catch (final NoSuchMethodException x)
		{
			// expected on API levels below 11
		}
		catch (final Exception x)
		{
			throw new RuntimeException(x);
		}
	}

	public static String currencySymbol(@Nonnull final String currencyCode)
	{
		try
		{
			final Currency currency = Currency.getInstance(currencyCode);
			return currency.getSymbol();
		}
		catch (final IllegalArgumentException x)
		{
			return currencyCode;
		}
	}
}
