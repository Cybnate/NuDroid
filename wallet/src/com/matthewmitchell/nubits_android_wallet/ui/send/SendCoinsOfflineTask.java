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

package com.matthewmitchell.nubits_android_wallet.ui.send;


import com.matthewmitchell.nubitsj.core.Coin;
import com.matthewmitchell.nubitsj.core.ECKey;
import com.matthewmitchell.nubitsj.core.InsufficientMoneyException;
import com.matthewmitchell.nubitsj.core.Transaction;
import com.matthewmitchell.nubitsj.core.Wallet;
import com.matthewmitchell.nubitsj.core.Wallet.CompletionException;
import com.matthewmitchell.nubitsj.core.Wallet.CouldNotAdjustDownwards;
import com.matthewmitchell.nubitsj.core.Wallet.SendRequest;
import com.matthewmitchell.nubitsj.crypto.KeyCrypterException;

import com.matthewmitchell.nubits_android_wallet.Constants;

import android.os.Handler;
import android.os.Looper;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Andreas Schildbach
 */
public abstract class SendCoinsOfflineTask
{
    private final Wallet wallet;
    private final Handler backgroundHandler;
    private final Handler callbackHandler;

	public SendCoinsOfflineTask(final Wallet wallet, final Handler backgroundHandler)
    {
        this.wallet = wallet;
        this.backgroundHandler = backgroundHandler;
        this.callbackHandler = new Handler(Looper.myLooper());
    }

	public final void sendCoinsOffline(final SendRequest sendRequest)
    {
        backgroundHandler.post(new Runnable()
                {
                    @Override
                    public void run()
                    {
			Context.propagate(Constants.CONTEXT);
                        try
                        {
                            final Transaction transaction = wallet.sendCoinsOffline(sendRequest); // can take long

                            callbackHandler.post(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            onSuccess(transaction);
                                        }
                                    });
                        }
                        catch (final InsufficientMoneyException x)
                        {
                            callbackHandler.post(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            onInsufficientMoney(x.missing);
                                        }
                                    });
                        }
			catch (final ECKey.KeyIsEncryptedException x)
				{
					callbackHandler.post(new Runnable()
					{
						@Override
						public void run()
						{
							onFailure(x);
						}
					});
				}
                        catch (final KeyCrypterException x)
                        {
                            callbackHandler.post(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            onInvalidKey();
                                        }
                                    });
                        }
                        catch (final CouldNotAdjustDownwards x)
                        {
                            callbackHandler.post(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            onEmptyWalletFailed();
                                        }
                                    });
                        }
                        catch (final CompletionException x)
                        {
                            callbackHandler.post(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            onFailure(x);
                                        }
                                    });
                        } catch (final IOException x) {
                            callbackHandler.post(new Runnable()
                                    {
                                        @Override
                                        public void run()
                                        {
                                            onFailure(x);
                                        }
                                    });
                        }
                    }
                });
    }

	protected abstract void onSuccess(Transaction transaction);

	protected abstract void onInsufficientMoney(Coin missing);

    protected abstract void onInvalidKey();

    protected void onEmptyWalletFailed()
    {
        onFailure(new CouldNotAdjustDownwards());
    }

	protected abstract void onFailure(Exception exception);
}
