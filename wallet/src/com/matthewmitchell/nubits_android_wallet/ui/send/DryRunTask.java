/*
 * Copyright 2014 the original author or authors.
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

package com.matthewmitchell.nubits_android_wallet.ui.send;

import javax.annotation.Nonnull;

import com.matthewmitchell.nubitsj.crypto.KeyCrypter;
import org.spongycastle.crypto.params.KeyParameter;

import android.os.Handler;
import android.os.Looper;
import com.matthewmitchell.nubits_android_wallet.data.PaymentIntent;
import com.matthewmitchell.nubits_android_wallet.ui.CurrencyCalculatorLink;
import com.matthewmitchell.nubitsj.core.Address;
import com.matthewmitchell.nubitsj.core.Coin;
import com.matthewmitchell.nubitsj.core.Transaction;
import com.matthewmitchell.nubitsj.core.Wallet;

/**
 * @author Andreas Schildbach
 */
public abstract class DryRunTask
{
	private final Handler backgroundHandler;
	private final Handler callbackHandler;
    private int callbackCount = 0;

	public DryRunTask(@Nonnull final Handler backgroundHandler) {
		this.backgroundHandler = backgroundHandler;
		this.callbackHandler = new Handler(Looper.myLooper());
	}

	public final void execute(final PaymentIntent paymentIntent, final CurrencyCalculatorLink amountCalculatorLink, final Wallet wallet) {
        
        backgroundHandler.removeCallbacksAndMessages(null);
        
        final int ourCount = ++callbackCount;
        
		backgroundHandler.post(new Runnable() {
			@Override
			public void run() {
                
                Coin amount = amountCalculatorLink.getAmount();
                Transaction dryRunTransaction = null;
                Exception dryRunException = null; 

                if (amount != null) {
                    try {

                        final Address dummy = wallet.currentReceiveAddress(); // won't be used, tx is never committed
                        final Wallet.SendRequest sendRequest = paymentIntent.mergeWithEditedValues(amount, dummy).toSendRequest();
                        sendRequest.signInputs = false;
                        sendRequest.emptyWallet = paymentIntent.mayEditAmount() && amount.equals(wallet.getBalance(Wallet.BalanceType.ESTMINUSFEE));
                        sendRequest.feePerKb = Wallet.SendRequest.DEFAULT_FEE_PER_KB;
                        
                        // Double check we are the latest callback before doing expensive operation.
                        if (ourCount != callbackCount)
                            return;
                        
                        wallet.completeTx(sendRequest);
                        dryRunTransaction = sendRequest.tx;

                        if (sendRequest.emptyWallet)
                            amount = dryRunTransaction.getOutput(0).getValue();

                    } catch (final Exception x) {
                        dryRunException = x;
                    }
                }
                
                final Coin finalAmount = amount;
                final Transaction finalDryRunTransaction = dryRunTransaction;
                final Exception finalDryRunException = dryRunException;

                if (ourCount != callbackCount)
                    return;
                
				callbackHandler.post(new Runnable() {
					@Override
					public void run() {
						onSuccess(finalAmount, finalDryRunTransaction, finalDryRunException);
					}
				});
			}
		});
	}

	protected abstract void onSuccess(Coin amount, Transaction dryRunTransaction, Exception dryRunException);
}
