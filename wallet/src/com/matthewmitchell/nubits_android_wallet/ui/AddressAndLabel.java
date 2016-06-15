/*
 * Copyright 2013-2015 the original author or authors.
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

package com.matthewmitchell.nubits_android_wallet.ui;

import android.os.Parcel;
import android.os.Parcelable;
import com.matthewmitchell.nubitsj.core.Address;
import com.matthewmitchell.nubitsj.core.AddressFormatException;
import com.matthewmitchell.nubitsj.core.NetworkParameters;
import com.matthewmitchell.nubitsj.core.WrongNetworkException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import com.google.common.base.Objects;

import com.matthewmitchell.nubits_android_wallet.Constants;
import com.matthewmitchell.nubits_android_wallet.util.WalletUtils;

/**
 * @author Andreas Schildbach
 */
public class AddressAndLabel implements Parcelable
{
	public final Address address;
	public final String label;

	public AddressAndLabel(final Address address, @Nullable final String label)
	{
		this.address = address;
		this.label = label;
	}

	public AddressAndLabel(final NetworkParameters addressParams, final String address, @Nullable final String label) throws WrongNetworkException,
			AddressFormatException
	{
		this(new Address(addressParams, address), label);
	}
	
	public AddressAndLabel(@Nonnull final List<NetworkParameters> addressParams, @Nonnull final String address, @Nullable final String label)
			throws WrongNetworkException, AddressFormatException {
		this.address = new Address(addressParams, address);
		this.label = label;
	}

	@Override
	public boolean equals(final Object o)
	{
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		final AddressAndLabel other = (AddressAndLabel) o;
		return Objects.equal(this.address, other.address) && Objects.equal(this.label, other.label);
	}

	@Override
	public int hashCode()
	{
		return Objects.hashCode(address, label);
	}

	@Override
	public int describeContents()
	{
		return 0;
	}

	@Override
	public void writeToParcel(final Parcel dest, final int flags) {
		
		List<NetworkParameters> networks = address.getParameters();
		
		dest.writeInt(networks.size());
		
		for (NetworkParameters network: networks)
			dest.writeSerializable(network);
		
		dest.writeString(address.toString());
		dest.writeString(label);
	}

	public static final Parcelable.Creator<AddressAndLabel> CREATOR = new Parcelable.Creator<AddressAndLabel>()
	{
		@Override
		public AddressAndLabel createFromParcel(final Parcel in)
		{
			return new AddressAndLabel(in);
		}

		@Override
		public AddressAndLabel[] newArray(final int size)
		{
			return new AddressAndLabel[size];
		}
	};

	private AddressAndLabel(final Parcel in) {
		
		final int paramsSize = in.readInt();
		final List<NetworkParameters> addressParameters = new ArrayList<NetworkParameters>(paramsSize);
		
		for (int x = 0; x < paramsSize; x++)
			addressParameters.add((NetworkParameters) in.readSerializable());
		
		address = WalletUtils.newAddressOrThrow(addressParameters, in.readString());

		label = in.readString();
	}
}
