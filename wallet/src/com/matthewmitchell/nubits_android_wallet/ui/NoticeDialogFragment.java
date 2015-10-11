/*
 * Copyright (C) 2015 NuBits Developers
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;

import com.matthewmitchell.nubits_android_wallet.R;

/**
 *
 * @author Matthew Mitchell
 */
public class NoticeDialogFragment extends DialogFragment {
	
	private String title, message, ok, cancel;
	private Parcelable object;
	
	private static final String TITLE_INSTANCE = "title";
	private static final String MESSAGE_INSTANCE = "message";
	
    /**
     * Creates and shows a simple dialog showing a message.
     * @param title
     * @param message
     * @param manager
     */
    public static void create(String title, String message, FragmentManager manager) {
		
		final  NoticeDialogFragment instance = new NoticeDialogFragment();
		
		instance.title = title;
		instance.message = message;

		instance.show(manager, "dialog");
		
	}
	
	@Override
	public void onSaveInstanceState (Bundle outState) {
		
		super.onSaveInstanceState(outState);
		outState.putString(TITLE_INSTANCE, title);
		outState.putString(MESSAGE_INSTANCE, message);
		
	}
	
	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		
		if (savedInstanceState != null) {
			title = savedInstanceState.getString(TITLE_INSTANCE);
			message = savedInstanceState.getString(MESSAGE_INSTANCE);
		}
		
		return new AlertDialog.Builder(getActivity())
			.setTitle(title)
			.setMessage(message)
			.setPositiveButton(R.string.button_ok,
				new DialogInterface.OnClickListener() {
					
					public void onClick(DialogInterface dialog, int whichButton) {
						dialog.dismiss();
					}
					
				}
			)
			.create();
		
	}
	
}
