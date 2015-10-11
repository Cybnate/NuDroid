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
public class ConfirmationDialogFragment extends DialogFragment {

    private String title, message, ok, cancel;
    private Parcelable object;

    private static final String OBJECT_INSTANCE = "object";
    private static final String TITLE_INSTANCE = "title";
    private static final String MESSAGE_INSTANCE = "message";
    private static final String OK_INSTANCE = "ok";
    private static final String CANCEL_INSTANCE = "cancel";

    public interface ConfirmationDialogCallbacks {
        public void onNegative(Parcelable object);
        public void onPositive(Parcelable object);
    } 

    /**
     * Creates and shows a simple dialog showing a message, allowing for a positive or negative response.
     * @param title
     * @param message
     * @param parent The Fragment to receive callbacks or null to use the Activity.
     * @param manager
     * @param object
     * @param ok
     * @param cancel
     */ 
    public static void create(String title, String message, Fragment parent, FragmentManager manager, Parcelable object, String ok, String cancel) {

        final ConfirmationDialogFragment instance = new ConfirmationDialogFragment();

        instance.title = title;
        instance.message = message;
        instance.object = object;
        instance.ok = ok;
        instance.cancel = cancel;

        instance.setTargetFragment(parent, 0);
        instance.show(manager, "dialog");

    }

    @Override
    public void onSaveInstanceState (Bundle outState) {

        super.onSaveInstanceState(outState);
        outState.putParcelable(OBJECT_INSTANCE, object);
        outState.putString(TITLE_INSTANCE, title);
        outState.putString(MESSAGE_INSTANCE, message);
        outState.putString(OK_INSTANCE, ok);
        outState.putString(CANCEL_INSTANCE, cancel);

    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        if (savedInstanceState != null) {
            object = savedInstanceState.getParcelable(OBJECT_INSTANCE);
            title = savedInstanceState.getString(TITLE_INSTANCE);
            message = savedInstanceState.getString(MESSAGE_INSTANCE);
            ok = savedInstanceState.getString(OK_INSTANCE);
            cancel = savedInstanceState.getString(CANCEL_INSTANCE);
        }

        // Allow callback to Activity or Fragment.

        final Fragment fragment = getTargetFragment();
        final ConfirmationDialogCallbacks cbs = (ConfirmationDialogCallbacks) (fragment != null ? fragment : getActivity());

        return new AlertDialog.Builder(getActivity())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(ok,
                    new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int whichButton) {
                            cbs.onPositive(object);
                            dialog.dismiss();
                        }

            }
            )
            .setNegativeButton(cancel,
                    new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface dialog, int whichButton) {
                            cbs.onNegative(object);
                            dialog.dismiss();
                        }

            }
            )
            .create();

    }

}
