/*
 * Copyright 2011-2015 the original author or authors.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.os.Bundle;
import android.os.Parcelable;

import com.matthewmitchell.nubits_android_wallet.WalletApplication;
import com.matthewmitchell.nubits_android_wallet.ui.RestoreWalletTask.CloseAction;
import com.matthewmitchell.nubits_android_wallet.ui.preference.TrustedServerList;

import java.io.File;
import java.io.InputStream;

/**
 * @author Andreas Schildbach
 */
public abstract class AbstractWalletActivity extends LoaderActivity
{
    protected static final int DIALOG_RESTORE_WALLET = 0;
    private WalletApplication application;

    protected RestoreWalletTask restoreTask = null;
    public TransactionsListAdapter txListAdapter = null;

    protected static final Logger log = LoggerFactory.getLogger(AbstractWalletActivity.class);

    @Override
    protected void onCreate(final Bundle savedInstanceState) {

        application = (WalletApplication) getApplication();
        super.onCreate(savedInstanceState);

    }

    protected WalletApplication getWalletApplication()
    {
        return application;
    }

    protected void restoreWalletFromEncrypted(@Nonnull final InputStream cipher, @Nonnull final String password, final CloseAction closeAction) {
        restoreTask = new RestoreWalletTask();
        restoreTask.restoreWalletFromEncrypted(cipher, password, this, closeAction);
    }

    protected void restoreWalletFromEncrypted(@Nonnull final File file, @Nonnull final String password, final CloseAction closeAction) {
        restoreTask = new RestoreWalletTask();
        restoreTask.restoreWalletFromEncrypted(file, password, this, closeAction);
    }

    protected void restoreWalletFromProtobuf(@Nonnull final File file, final CloseAction closeAction) {
        restoreTask = new RestoreWalletTask();
        restoreTask.restoreWalletFromProtobuf(file, this, closeAction);
    }

    protected void restorePrivateKeysFromBase58(@Nonnull final File file, final CloseAction closeAction) {
        restoreTask = new RestoreWalletTask();
        restoreTask.restorePrivateKeysFromBase58(file, this, closeAction);
    }

    @Override
    protected void onStop() {
        if (restoreTask != null) { 
            restoreTask.cancel(false);
            restoreTask = null;
        }
        super.onStop();
    }

}
