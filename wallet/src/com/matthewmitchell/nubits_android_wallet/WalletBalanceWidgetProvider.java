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

package com.matthewmitchell.nubits_android_wallet;

import java.math.BigInteger;

import javax.annotation.Nonnull;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.widget.RemoteViews;

import com.matthewmitchell.nubitsj.core.Wallet;
import com.matthewmitchell.nubitsj.core.Wallet.BalanceType;

import com.matthewmitchell.nubits_android_wallet.ui.RequestCoinsActivity;
import com.matthewmitchell.nubits_android_wallet.ui.SendCoinsActivity;
import com.matthewmitchell.nubits_android_wallet.ui.SendCoinsQrActivity;
import com.matthewmitchell.nubits_android_wallet.ui.WalletActivity;
import com.matthewmitchell.nubits_android_wallet.util.GenericUtils;
import com.matthewmitchell.nubits_android_wallet.util.WalletUtils;
import com.matthewmitchell.nubits_android_wallet.R;

/**
 * @author Andreas Schildbach
 */
public class WalletBalanceWidgetProvider extends AppWidgetProvider
{
	@Override
	public void onUpdate(final Context context, final AppWidgetManager appWidgetManager, final int[] appWidgetIds)
	{
		final WalletApplication application = (WalletApplication) context.getApplicationContext();
		final Wallet wallet = application.getWallet();
		final BigInteger balance = wallet.getBalance(BalanceType.ESTIMATED);

		updateWidgets(context, appWidgetManager, appWidgetIds, balance);
	}

	public static void updateWidgets(final Context context, @Nonnull final AppWidgetManager appWidgetManager, @Nonnull final int[] appWidgetIds,
			@Nonnull final BigInteger balance)
	{
		final Configuration config = new Configuration(PreferenceManager.getDefaultSharedPreferences(context));
		final Spannable balanceStr = new SpannableString(GenericUtils.formatValue(balance, config.getNBTPrecision(), config.getNBTShift()));
		WalletUtils.formatSignificant(balanceStr, WalletUtils.SMALLER_SPAN);

		for (final int appWidgetId : appWidgetIds)
		{
			final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.wallet_balance_widget_content);
			views.setTextViewText(R.id.widget_wallet_prefix, config.getNBTPrefix());
			views.setTextViewText(R.id.widget_wallet_balance, balanceStr);
			views.setOnClickPendingIntent(R.id.widget_button_balance,
					PendingIntent.getActivity(context, 0, new Intent(context, WalletActivity.class), 0));
			views.setOnClickPendingIntent(R.id.widget_button_request,
					PendingIntent.getActivity(context, 0, new Intent(context, RequestCoinsActivity.class), 0));
			views.setOnClickPendingIntent(R.id.widget_button_send,
					PendingIntent.getActivity(context, 0, new Intent(context, SendCoinsActivity.class), 0));
			views.setOnClickPendingIntent(R.id.widget_button_send_qr,
					PendingIntent.getActivity(context, 0, new Intent(context, SendCoinsQrActivity.class), 0));

			appWidgetManager.updateAppWidget(appWidgetId, views);
		}
	}
}
