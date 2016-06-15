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

import java.util.Set;

import javax.annotation.Nullable;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.matthewmitchell.nubits_android_wallet.Configuration;
import com.matthewmitchell.nubits_android_wallet.WalletApplication;
import com.matthewmitchell.nubits_android_wallet.service.BlockchainState;
import com.matthewmitchell.nubits_android_wallet.service.BlockchainState.Impediment;
import com.matthewmitchell.nubits_android_wallet.service.BlockchainStateLoader;
import com.matthewmitchell.nubits_android_wallet.R;

/**
 * @author Andreas Schildbach
 */
public final class WalletDisclaimerFragment extends Fragment implements OnSharedPreferenceChangeListener
{
	private WalletActivity activity;
	private WalletApplication application;
	private LoaderManager loaderManager;

	@Nullable
	private BlockchainState blockchainState = null;

	private TextView messageView;

	private static final int ID_BLOCKCHAIN_STATE_LOADER = 0;

	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);

		this.activity = (WalletActivity) activity;
		application = (WalletApplication) activity.getApplication();
		loaderManager = getLoaderManager();
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState)
	{
		messageView = (TextView) inflater.inflate(R.layout.wallet_disclaimer_fragment, container);

		messageView.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(final View v)
			{
					HelpDialogFragment.page(getFragmentManager(), R.string.help_safety);
			}
		});

		return messageView;
	}

	@Override
	public void onResume() {
	    super.onResume();

	    activity.runAfterLoad(new Runnable() {

		@Override
		public void run() {
		    loaderManager.initLoader(ID_BLOCKCHAIN_STATE_LOADER, null, blockchainStateLoaderCallbacks);
		    application.getConfiguration().registerOnSharedPreferenceChangeListener(WalletDisclaimerFragment.this);
		    updateView();
		}

	    });
		
	}

	@Override
	public void onPause() {
	    
	    activity.runAfterLoad(new Runnable() {

		@Override
		public void run() {
		    loaderManager.destroyLoader(ID_BLOCKCHAIN_STATE_LOADER);
		    application.getConfiguration().unregisterOnSharedPreferenceChangeListener(WalletDisclaimerFragment.this);
		}
		
	    });

	    super.onPause();
	    
	}

	@Override
	public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
	{
		if (Configuration.PREFS_KEY_DISCLAIMER.equals(key))
			updateView();
	}

	private void updateView()
	{
		if (!isResumed())
			return;

		final Configuration config = application.getConfiguration();
		final boolean showDisclaimer = config.getDisclaimerEnabled();

		int progressResId = 0;
		if (blockchainState != null && blockchainState.loaded)
		{
			final Set<Impediment> impediments = blockchainState.impediments;
			if (impediments.contains(Impediment.STORAGE))
				progressResId = R.string.blockchain_state_progress_problem_storage;
			else if (impediments.contains(Impediment.NETWORK))
				progressResId = R.string.blockchain_state_progress_problem_network;
		}

		final SpannableStringBuilder text = new SpannableStringBuilder();
		if (progressResId != 0)
			text.append(Html.fromHtml("<b>" + getString(progressResId) + "</b>"));
		if (progressResId != 0 && showDisclaimer)
			text.append('\n');
		if (showDisclaimer)
			text.append(Html.fromHtml(getString(R.string.wallet_disclaimer_fragment_remind_safety)));
		messageView.setText(text);

		final View view = getView();
		final ViewParent parent = view.getParent();
		final View fragment = parent instanceof FrameLayout ? (FrameLayout) parent : view;
		fragment.setVisibility(text.length() > 0 ? View.VISIBLE : View.GONE);
	}

	private final LoaderCallbacks<BlockchainState> blockchainStateLoaderCallbacks = new LoaderManager.LoaderCallbacks<BlockchainState>()
	{
		@Override
		public Loader<BlockchainState> onCreateLoader(final int id, final Bundle args)
		{
			return new BlockchainStateLoader(activity);
		}

		@Override
		public void onLoadFinished(final Loader<BlockchainState> loader, final BlockchainState blockchainState)
		{
			WalletDisclaimerFragment.this.blockchainState = blockchainState;

			updateView();
		}

		@Override
		public void onLoaderReset(final Loader<BlockchainState> loader)
		{
		}
	};
}
