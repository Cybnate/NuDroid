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

package com.matthewmitchell.nubits_android_wallet.ui;

import android.content.Context;
import android.content.SharedPreferences;

import android.os.Bundle;
import android.os.Parcelable;
import com.matthewmitchell.nubits_android_wallet.ui.preference.TrustedServerList;
import com.matthewmitchell.nubits_android_wallet.R;

/**
 * @author Andreas Schildbach
 */
public abstract class AfterUpdateActivity extends AbstractWalletActivity implements ConfirmationDialogFragment.ConfirmationDialogCallbacks {

    private final String PREF_KEY_VERSION_4_3_UPDATED = "version_4_3_updated";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        
        // Ensure that the dialog to add the Anton server only shows once.
        
        synchronized (this) {
        
            if (!prefs.getBoolean(PREF_KEY_VERSION_4_3_UPDATED, false)) {

                prefs.edit().putBoolean(PREF_KEY_VERSION_4_3_UPDATED, true).apply();

                if (TrustedServerList.getInstance(this).noAnton())

                    ConfirmationDialogFragment.create(
                        getString(R.string.trusted_servers_add_anton_title),
                        getString(R.string.trusted_servers_add_anton_message),
                        null,
                        getFragmentManager(),
                        null,
                        getString(R.string.button_yes),
                        getString(R.string.button_no)
                    );

            }
            
        }

        super.onCreate(savedInstanceState);
        
    }
    
    public void onNegative(Parcelable object) {}
    
	public void onPositive(Parcelable object) {
        
        TrustedServerList.getInstance(this).insertAnton();
        
        NoticeDialogFragment.create(
            getString(R.string.trusted_servers_add_anton_done_title),
            getString(R.string.trusted_servers_add_anton_done_message),
            getFragmentManager()
        );
        
    }

}
