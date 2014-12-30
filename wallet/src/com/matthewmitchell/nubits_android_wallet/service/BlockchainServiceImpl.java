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

package com.matthewmitchell.nubits_android_wallet.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.annotation.SuppressLint;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.app.NotificationCompat;
import android.text.format.DateUtils;

import com.matthewmitchell.nubitsj.core.AbstractPeerEventListener;
import com.matthewmitchell.nubitsj.core.Address;
import com.matthewmitchell.nubitsj.core.Block;
import com.matthewmitchell.nubitsj.core.BlockChain;
import com.matthewmitchell.nubitsj.core.CheckpointManager;
import com.matthewmitchell.nubitsj.core.Peer;
import com.matthewmitchell.nubitsj.core.PeerEventListener;
import com.matthewmitchell.nubitsj.core.PeerGroup;
import com.matthewmitchell.nubitsj.core.Sha256Hash;
import com.matthewmitchell.nubitsj.core.StoredBlock;
import com.matthewmitchell.nubitsj.core.Transaction;
import com.matthewmitchell.nubitsj.core.TransactionConfidence.ConfidenceType;
import com.matthewmitchell.nubitsj.core.Wallet;
import com.matthewmitchell.nubitsj.core.Wallet.BalanceType;
import com.matthewmitchell.nubitsj.core.WalletEventListener;
import com.matthewmitchell.nubitsj.net.discovery.DnsDiscovery;
import com.matthewmitchell.nubitsj.net.discovery.PeerDiscovery;
import com.matthewmitchell.nubitsj.net.discovery.PeerDBDiscovery;
import com.matthewmitchell.nubitsj.net.discovery.PeerDiscoveryException;
import com.matthewmitchell.nubitsj.store.BlockStore;
import com.matthewmitchell.nubitsj.store.BlockStoreException;
import com.matthewmitchell.nubitsj.store.SPVBlockStore;
import com.matthewmitchell.nubitsj.store.ValidHashStore;
import com.matthewmitchell.nubitsj.utils.Threading;

import com.matthewmitchell.nubits_android_wallet.AddressBookProvider;
import com.matthewmitchell.nubits_android_wallet.Configuration;
import com.matthewmitchell.nubits_android_wallet.Constants;
import com.matthewmitchell.nubits_android_wallet.WalletApplication;
import com.matthewmitchell.nubits_android_wallet.WalletBalanceWidgetProvider;
import com.matthewmitchell.nubits_android_wallet.ui.WalletActivity;
import com.matthewmitchell.nubits_android_wallet.util.CrashReporter;
import com.matthewmitchell.nubits_android_wallet.util.GenericUtils;
import com.matthewmitchell.nubits_android_wallet.util.ThrottlingWalletChangeListener;
import com.matthewmitchell.nubits_android_wallet.util.WalletUtils;
import com.matthewmitchell.nubits_android_wallet.R;

/**
 * @author Andreas Schildbach
 */
public class BlockchainServiceImpl extends android.app.Service implements BlockchainService
{
	private WalletApplication application;
	private Configuration config;

	private BlockStore blockStore;
	private File blockChainFile;
	private File validHashStoreFile;
	private BlockChain blockChain;
	@CheckForNull
	private PeerGroup peerGroup;

    private final static int PORT = Constants.NETWORK_PARAMETERS.getPort();

    private final static InetSocketAddress hardcodedPeers[] = {
            new InetSocketAddress("198.52.160.71", PORT),
            new InetSocketAddress("198.52.217.4", PORT),
            new InetSocketAddress("198.52.199.75", PORT),
            new InetSocketAddress("198.52.199.46", PORT),
            new InetSocketAddress("162.242.208.43", PORT),
            new InetSocketAddress("192.237.200.146", PORT),
            new InetSocketAddress("119.9.75.189", PORT),
            new InetSocketAddress("119.9.12.63", PORT),
    };

	private ValidHashStore validHashStore;

	private final Handler handler = new Handler();
	private final Handler delayHandler = new Handler();
	private WakeLock wakeLock;

	private PeerConnectivityListener peerConnectivityListener;
	private NotificationManager nm;
	private static final int NOTIFICATION_ID_CONNECTED = 0;
	private static final int NOTIFICATION_ID_COINS_RECEIVED = 1;

	private int notificationCount = 0;
	private BigInteger notificationAccumulatedAmount = BigInteger.ZERO;
	private final List<Address> notificationAddresses = new LinkedList<Address>();
	private AtomicInteger transactionsReceived = new AtomicInteger();
	private int bestChainHeightEver;
	private long serviceCreatedAt;
	private boolean resetBlockchainOnShutdown = false;

	private final String backupDNS[] = new String[]{
		"svr1.nubitsexplorer.nu"
	};

	private static final int MIN_COLLECT_HISTORY = 2;
	private static final int IDLE_BLOCK_TIMEOUT_MIN = 2;
	private static final int IDLE_TRANSACTION_TIMEOUT_MIN = 9;
	private static final int MAX_HISTORY_SIZE = Math.max(IDLE_TRANSACTION_TIMEOUT_MIN, IDLE_BLOCK_TIMEOUT_MIN);
	private static final long APPWIDGET_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS;

	private static final Logger log = LoggerFactory.getLogger(BlockchainServiceImpl.class);

	private final WalletEventListener walletEventListener = new ThrottlingWalletChangeListener(APPWIDGET_THROTTLE_MS)
	{
		@Override
		public void onThrottledWalletChanged()
		{
			notifyWidgets();
		}

		@Override
		public void onCoinsReceived(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			transactionsReceived.incrementAndGet();

			final int bestChainHeight = blockChain.getBestChainHeight();

			final Address from = WalletUtils.getFirstFromAddress(tx);
			final BigInteger amount = tx.getValue(wallet);
			final ConfidenceType confidenceType = tx.getConfidence().getConfidenceType();

			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					final boolean isReceived = amount.signum() > 0;
					final boolean replaying = bestChainHeight < bestChainHeightEver;
					final boolean isReplayedTx = confidenceType == ConfidenceType.BUILDING && replaying;

					if (isReceived && !isReplayedTx)
						notifyCoinsReceived(from, amount);
				}
			});
		}

		@Override
		public void onCoinsSent(final Wallet wallet, final Transaction tx, final BigInteger prevBalance, final BigInteger newBalance)
		{
			transactionsReceived.incrementAndGet();
		}
	};

	private void notifyCoinsReceived(@Nullable final Address from, @Nonnull final BigInteger amount)
	{
		if (notificationCount == 1)
			nm.cancel(NOTIFICATION_ID_COINS_RECEIVED);

		notificationCount++;
		notificationAccumulatedAmount = notificationAccumulatedAmount.add(amount);
		if (from != null && !notificationAddresses.contains(from))
			notificationAddresses.add(from);

		final int NBTPrecision = config.getNBTPrecision();
		final int NBTShift = config.getNBTShift();
		final String NBTPrefix = config.getNBTPrefix();

		final String packageFlavor = application.applicationPackageFlavor();
		final String msgSuffix = packageFlavor != null ? " [" + packageFlavor + "]" : "";

		final String tickerMsg = getString(R.string.notification_coins_received_msg,
				NBTPrefix + ' ' + GenericUtils.formatValue(amount, NBTPrecision, NBTShift))
				+ msgSuffix;

		final String msg = getString(R.string.notification_coins_received_msg,
				NBTPrefix + ' ' + GenericUtils.formatValue(notificationAccumulatedAmount, NBTPrecision, NBTShift))
				+ msgSuffix;

		final StringBuilder text = new StringBuilder();
		for (final Address address : notificationAddresses)
		{
			if (text.length() > 0)
				text.append(", ");

			final String addressStr = address.toString();
			final String label = AddressBookProvider.resolveLabel(getApplicationContext(), addressStr);
			text.append(label != null ? label : addressStr);
		}

		final NotificationCompat.Builder notification = new NotificationCompat.Builder(this);
		notification.setSmallIcon(R.drawable.stat_notify_received);
		notification.setTicker(tickerMsg);
		notification.setContentTitle(msg);
		if (text.length() > 0)
			notification.setContentText(text);
		notification.setContentIntent(PendingIntent.getActivity(this, 0, new Intent(this, WalletActivity.class), 0));
		notification.setNumber(notificationCount == 1 ? 0 : notificationCount);
		notification.setWhen(System.currentTimeMillis());
		notification.setSound(Uri.parse("android.resource://" + getPackageName() + "/" + R.raw.coins_received));
		nm.notify(NOTIFICATION_ID_COINS_RECEIVED, notification.getNotification());
	}

	private final class PeerConnectivityListener extends AbstractPeerEventListener implements OnSharedPreferenceChangeListener
	{
		private int peerCount;
		private AtomicBoolean stopped = new AtomicBoolean(false);

		public PeerConnectivityListener()
		{
			config.registerOnSharedPreferenceChangeListener(this);
		}

		public void stop()
		{
			stopped.set(true);

			config.unregisterOnSharedPreferenceChangeListener(this);

			nm.cancel(NOTIFICATION_ID_CONNECTED);
		}

		@Override
		public void onPeerConnected(final Peer peer, final int peerCount)
		{
			this.peerCount = peerCount;
			changed(peerCount);
		}

		@Override
		public void onPeerDisconnected(final Peer peer, final int peerCount)
		{
			this.peerCount = peerCount;
			changed(peerCount);
		}

		@Override
		public void onSharedPreferenceChanged(final SharedPreferences sharedPreferences, final String key)
		{
			if (Configuration.PREFS_KEY_CONNECTIVITY_NOTIFICATION.equals(key))
				changed(peerCount);
		}

		private void changed(final int numPeers)
		{
			if (stopped.get())
				return;

			handler.post(new Runnable()
			{
				@Override
				public void run()
				{
					final boolean connectivityNotificationEnabled = config.getConnectivityNotificationEnabled();

					if (!connectivityNotificationEnabled || numPeers == 0)
					{
						nm.cancel(NOTIFICATION_ID_CONNECTED);
					}
					else
					{
						final NotificationCompat.Builder notification = new NotificationCompat.Builder(BlockchainServiceImpl.this);
						notification.setSmallIcon(R.drawable.stat_sys_peers, numPeers > 4 ? 4 : numPeers);
						notification.setContentTitle(getString(R.string.app_name));
						notification.setContentText(getString(R.string.notification_peers_connected_msg, numPeers));
						notification.setContentIntent(PendingIntent.getActivity(BlockchainServiceImpl.this, 0, new Intent(BlockchainServiceImpl.this,
								WalletActivity.class), 0));
						notification.setWhen(System.currentTimeMillis());
						notification.setOngoing(true);
						nm.notify(NOTIFICATION_ID_CONNECTED, notification.getNotification());
					}

					// send broadcast
					sendBroadcastPeerState(numPeers);
				}
			});
		}
	}

	private final PeerEventListener blockchainDownloadListener = new AbstractPeerEventListener()
	{
		private final AtomicLong lastMessageTime = new AtomicLong(0);

		@Override
		public void onBlocksDownloaded(final Peer peer, final Block block, final int blocksLeft)
		{
			bestChainHeightEver = Math.max(bestChainHeightEver, blockChain.getChainHead().getHeight());

			delayHandler.removeCallbacksAndMessages(null);

			final long now = System.currentTimeMillis();

			if (now - lastMessageTime.get() > Constants.BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS)
				delayHandler.post(runnable);
			else
				delayHandler.postDelayed(runnable, Constants.BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS);
		}

		private final Runnable runnable = new Runnable()
		{
			@Override
			public void run()
			{
				lastMessageTime.set(System.currentTimeMillis());

				sendBroadcastBlockchainState(ACTION_BLOCKCHAIN_STATE_DOWNLOAD_OK);
			}
		};
	};

	private final BroadcastReceiver connectivityReceiver = new BroadcastReceiver()
	{
		private boolean hasConnectivity;
		private boolean hasStorage = true;

		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			final String action = intent.getAction();

			if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action))
			{
				hasConnectivity = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
				log.info("network is " + (hasConnectivity ? "up" : "down"));

				check();
			}
			else if (Intent.ACTION_DEVICE_STORAGE_LOW.equals(action))
			{
				hasStorage = false;
				log.info("device storage low");

				check();
			}
			else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action))
			{
				hasStorage = true;
				log.info("device storage ok");

				check();
			}
		}

		@SuppressLint("Wakelock")
		private void check()
		{
			final Wallet wallet = application.getWallet();
			final boolean hasEverything = hasConnectivity && hasStorage;

			if (hasEverything && peerGroup == null)
			{
				log.debug("acquiring wakelock");
				wakeLock.acquire();

				// consistency check
				final int walletLastBlockSeenHeight = wallet.getLastBlockSeenHeight();
				final int bestChainHeight = blockChain.getBestChainHeight();
				if (walletLastBlockSeenHeight != -1 && walletLastBlockSeenHeight != bestChainHeight)
				{
					final String message = "wallet/blockchain out of sync: " + walletLastBlockSeenHeight + "/" + bestChainHeight;
					log.error(message);
					CrashReporter.saveBackgroundTrace(new RuntimeException(message), application.packageInfo());
				}

				log.info("starting peergroup");
				peerGroup = new PeerGroup(Constants.NETWORK_PARAMETERS, blockChain);
				peerGroup.setMinBroadcastConnections(1);
				peerGroup.addWallet(wallet);
				peerGroup.setUserAgent(Constants.USER_AGENT, application.packageInfo().versionName);
				peerGroup.addEventListener(peerConnectivityListener);

				final int maxConnectedPeers = application.maxConnectedPeers();

				final String trustedPeerHost = config.getTrustedPeerHost();
				final boolean hasTrustedPeer = !trustedPeerHost.isEmpty();

				final boolean connectTrustedPeerOnly = hasTrustedPeer && config.getTrustedPeerOnly();
				peerGroup.setMaxConnections(connectTrustedPeerOnly ? 1 : maxConnectedPeers);
				
				if (!connectTrustedPeerOnly) {
					PeerDBDiscovery dbDiscovery = new PeerDBDiscovery(Constants.NETWORK_PARAMETERS, new File(getDir("peers", Context.MODE_PRIVATE), Constants.PEERS_FILENAME), peerGroup);

					peerGroup.addPeerDiscovery(dbDiscovery);
					dbDiscovery.listenForPeers(peerGroup);

					// Use backup nodes when needed
					DnsDiscovery backup = new DnsDiscovery(backupDNS, Constants.NETWORK_PARAMETERS);
					peerGroup.addPeerDiscovery(backup, true);
				}

				peerGroup.addPeerDiscovery(new PeerDiscovery()
				{

					@Override
					public InetSocketAddress[] getPeers(final long timeoutValue, final TimeUnit timeoutUnit) throws PeerDiscoveryException
					{
						final List<InetSocketAddress> peers = new LinkedList<InetSocketAddress>();

						boolean needsTrimPeersWorkaround = false;

						if (hasTrustedPeer)
						{
							log.info("trusted peer '" + trustedPeerHost + "'" + (connectTrustedPeerOnly ? " only" : ""));

							final InetSocketAddress addr = new InetSocketAddress(trustedPeerHost, Constants.NETWORK_PARAMETERS.getPort());
							if (addr.getAddress() != null)
							{
								peers.add(addr);
								needsTrimPeersWorkaround = true;
							}
						}

						if (!connectTrustedPeerOnly)
							peers.addAll(Arrays.asList(hardcodedPeers));

						// workaround because PeerGroup will shuffle peers
						if (needsTrimPeersWorkaround)
							while (peers.size() >= maxConnectedPeers)
								peers.remove(peers.size() - 1);

						return peers.toArray(new InetSocketAddress[0]);
					}

					@Override
					public void shutdown() {
					}
				});

				// start peergroup
				peerGroup.start();
				peerGroup.startBlockChainDownload(blockchainDownloadListener);
			}
			else if (!hasEverything && peerGroup != null)
			{
				log.info("stopping peergroup");
				peerGroup.removeEventListener(peerConnectivityListener);
				peerGroup.removeWallet(wallet);
				peerGroup.stop();
				peerGroup = null;

				log.debug("releasing wakelock");
				wakeLock.release();
			}

			final int download = (hasConnectivity ? 0 : ACTION_BLOCKCHAIN_STATE_DOWNLOAD_NETWORK_PROBLEM)
					| (hasStorage ? 0 : ACTION_BLOCKCHAIN_STATE_DOWNLOAD_STORAGE_PROBLEM);

			sendBroadcastBlockchainState(download);
		}
	};

	private final static class ActivityHistoryEntry
	{
		public final int numTransactionsReceived;
		public final int numBlocksDownloaded;

		public ActivityHistoryEntry(final int numTransactionsReceived, final int numBlocksDownloaded)
		{
			this.numTransactionsReceived = numTransactionsReceived;
			this.numBlocksDownloaded = numBlocksDownloaded;
		}

		@Override
		public String toString()
		{
			return numTransactionsReceived + "/" + numBlocksDownloaded;
		}
	}

	private final BroadcastReceiver tickReceiver = new BroadcastReceiver()
	{
		private int lastChainHeight = 0;
		private final List<ActivityHistoryEntry> activityHistory = new LinkedList<ActivityHistoryEntry>();

		@Override
		public void onReceive(final Context context, final Intent intent)
		{
			final int chainHeight = blockChain.getBestChainHeight();

			if (lastChainHeight > 0)
			{
				final int numBlocksDownloaded = chainHeight - lastChainHeight;
				final int numTransactionsReceived = transactionsReceived.getAndSet(0);

				// push history
				activityHistory.add(0, new ActivityHistoryEntry(numTransactionsReceived, numBlocksDownloaded));

				// trim
				while (activityHistory.size() > MAX_HISTORY_SIZE)
					activityHistory.remove(activityHistory.size() - 1);

				// print
				final StringBuilder builder = new StringBuilder();
				for (final ActivityHistoryEntry entry : activityHistory)
				{
					if (builder.length() > 0)
						builder.append(", ");
					builder.append(entry);
				}
				log.info("History of transactions/blocks: " + builder);

				// determine if block and transaction activity is idling
				boolean isIdle = false;
				if (activityHistory.size() >= MIN_COLLECT_HISTORY)
				{
					isIdle = true;
					for (int i = 0; i < activityHistory.size(); i++)
					{
						final ActivityHistoryEntry entry = activityHistory.get(i);
						final boolean blocksActive = entry.numBlocksDownloaded > 0 && i <= IDLE_BLOCK_TIMEOUT_MIN;
						final boolean transactionsActive = entry.numTransactionsReceived > 0 && i <= IDLE_TRANSACTION_TIMEOUT_MIN;

						if (blocksActive || transactionsActive)
						{
							isIdle = false;
							break;
						}
					}
				}

				// if idling, shutdown service
				if (isIdle)
				{
					log.info("idling detected, stopping service");
					stopSelf();
				}
			}

			lastChainHeight = chainHeight;
		}
	};

	public class LocalBinder extends Binder
	{
		public BlockchainService getService()
		{
			return BlockchainServiceImpl.this;
		}
	}

	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(final Intent intent)
	{
		log.debug(".onBind()");

		return mBinder;
	}

	@Override
	public boolean onUnbind(final Intent intent)
	{
		log.debug(".onUnbind()");

		return super.onUnbind(intent);
	}

	@Override
	public void onCreate()
	{
		serviceCreatedAt = System.currentTimeMillis();
		log.debug(".onCreate()");

		super.onCreate();

		nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		final String lockName = getPackageName() + " blockchain sync";

		final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
		wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, lockName);

		application = (WalletApplication) getApplication();
		config = application.getConfiguration();
		final Wallet wallet = application.getWallet();

		bestChainHeightEver = config.getBestChainHeightEver();

		peerConnectivityListener = new PeerConnectivityListener();

		sendBroadcastPeerState(0);

		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW);
		intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
		registerReceiver(connectivityReceiver, intentFilter);

		blockChainFile = new File(getDir("blockstore", Context.MODE_PRIVATE), Constants.BLOCKCHAIN_FILENAME);
		final boolean blockChainFileExists = blockChainFile.exists();

		if (!blockChainFileExists)
		{
			log.info("blockchain does not exist, resetting wallet");

			wallet.clearTransactions(0);
			wallet.setLastBlockSeenHeight(-1); // magic value
			wallet.setLastBlockSeenHash(null);
		}

		try {
			blockStore = new SPVBlockStore(Constants.NETWORK_PARAMETERS, blockChainFile);
			blockStore.getChainHead(); // detect corruptions as early as possible
		} catch (final BlockStoreException x) {
			blockChainFile.delete();

			final String msg = "blockstore cannot be created";
			log.error(msg, x);
			throw new Error(msg, x);
		}

		log.info("using " + blockStore.getClass().getName());
		
		validHashStoreFile = new File(getDir("validhashes", Context.MODE_PRIVATE), Constants.VALID_HASHES_FILENAME);
		
		try{
			validHashStore = new ValidHashStore(validHashStoreFile);
		}catch (IOException x){
			validHashStoreFile.delete();
			final String msg = "validhashstore cannot be created";
			log.error(msg, x);
			throw new Error(msg, x);
		}

		try
		{
			blockChain = new BlockChain(Constants.NETWORK_PARAMETERS, wallet, blockStore, validHashStore);
		}
		catch (final BlockStoreException x)
		{
			throw new Error("blockchain cannot be created", x);
		}

		application.getWallet().addEventListener(walletEventListener, Threading.SAME_THREAD);

		registerReceiver(tickReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));

		maybeRotateKeys();
	}

	@Override
	public int onStartCommand(final Intent intent, final int flags, final int startId)
	{
		if (intent != null)
		{
			log.info("service start command: " + intent
					+ (intent.hasExtra(Intent.EXTRA_ALARM_COUNT) ? " (alarm count: " + intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 0) + ")" : ""));

			final String action = intent.getAction();

			if (BlockchainService.ACTION_CANCEL_COINS_RECEIVED.equals(action))
			{
				notificationCount = 0;
				notificationAccumulatedAmount = BigInteger.ZERO;
				notificationAddresses.clear();

				nm.cancel(NOTIFICATION_ID_COINS_RECEIVED);
			}
			else if (BlockchainService.ACTION_RESET_BLOCKCHAIN.equals(action))
			{
				log.info("will remove blockchain on service shutdown");

				resetBlockchainOnShutdown = true;
				stopSelf();
			}
			else if (BlockchainService.ACTION_BROADCAST_TRANSACTION.equals(action))
			{
				final Sha256Hash hash = new Sha256Hash(intent.getByteArrayExtra(BlockchainService.ACTION_BROADCAST_TRANSACTION_HASH));
				final Transaction tx = application.getWallet().getTransaction(hash);

				if (peerGroup != null)
				{
					log.info("broadcasting transaction " + tx.getHashAsString());
					peerGroup.broadcastTransaction(tx);
				}
				else
				{
					log.info("peergroup not available, not broadcasting transaction " + tx.getHashAsString());
				}
			}
		}
		else
		{
			log.warn("service restart, although it was started as non-sticky");
		}

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy()
	{
		log.debug(".onDestroy()");

		WalletApplication.scheduleStartBlockchainService(this);

		unregisterReceiver(tickReceiver);

		application.getWallet().removeEventListener(walletEventListener);

		if (peerGroup != null)
		{
			peerGroup.removeEventListener(peerConnectivityListener);
			peerGroup.removeWallet(application.getWallet());
			peerGroup.stopAsync();

			log.info("peergroup stopped");
		}

		peerConnectivityListener.stop();

		unregisterReceiver(connectivityReceiver);

		removeBroadcastPeerState();
		removeBroadcastBlockchainState();

		config.setBestChainHeightEver(bestChainHeightEver);

		delayHandler.removeCallbacksAndMessages(null);

		try
		{
			blockStore.close();
		}
		catch (final BlockStoreException x)
		{
			throw new RuntimeException(x);
		}

		validHashStore.close();
		validHashStoreFile.delete();
		
		application.saveWallet();

		if (wakeLock.isHeld())
		{
			log.debug("wakelock still held, releasing");
			wakeLock.release();
		}

		if (resetBlockchainOnShutdown)
		{
			log.info("removing blockchain");
			blockChainFile.delete();
		}

		super.onDestroy();

		log.info("service was up for " + ((System.currentTimeMillis() - serviceCreatedAt) / 1000 / 60) + " minutes");
	}

	@Override
	public void onLowMemory()
	{
		log.warn("low memory detected, stopping service");
		stopSelf();
	}

	@Override
	public List<Peer> getConnectedPeers()
	{
		if (peerGroup != null)
			return peerGroup.getConnectedPeers();
		else
			return null;
	}

	@Override
	public List<StoredBlock> getRecentBlocks(final int maxBlocks)
	{
		final List<StoredBlock> blocks = new ArrayList<StoredBlock>(maxBlocks);

		try
		{
			StoredBlock block = blockChain.getChainHead();

			while (block != null)
			{
				blocks.add(block);

				if (blocks.size() >= maxBlocks)
					break;

				block = block.getPrev(blockStore);
			}
		}
		catch (final BlockStoreException x)
		{
			// swallow
		}

		return blocks;
	}

	private void sendBroadcastPeerState(final int numPeers)
	{
		final Intent broadcast = new Intent(ACTION_PEER_STATE);
		broadcast.setPackage(getPackageName());
		broadcast.putExtra(ACTION_PEER_STATE_NUM_PEERS, numPeers);
		sendStickyBroadcast(broadcast);
	}

	private void removeBroadcastPeerState()
	{
		removeStickyBroadcast(new Intent(ACTION_PEER_STATE));
	}

	private void sendBroadcastBlockchainState(final int download)
	{
		final StoredBlock chainHead = blockChain.getChainHead();

		final Intent broadcast = new Intent(ACTION_BLOCKCHAIN_STATE);
		broadcast.setPackage(getPackageName());
		broadcast.putExtra(ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_DATE, chainHead.getHeader().getTime());
		broadcast.putExtra(ACTION_BLOCKCHAIN_STATE_BEST_CHAIN_HEIGHT, chainHead.getHeight());
		broadcast.putExtra(ACTION_BLOCKCHAIN_STATE_REPLAYING, chainHead.getHeight() < bestChainHeightEver);
		broadcast.putExtra(ACTION_BLOCKCHAIN_STATE_DOWNLOAD, download);

		sendStickyBroadcast(broadcast);
	}

	private void removeBroadcastBlockchainState()
	{
		removeStickyBroadcast(new Intent(ACTION_BLOCKCHAIN_STATE));
	}

	public void notifyWidgets()
	{
		final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);

		final ComponentName providerName = new ComponentName(this, WalletBalanceWidgetProvider.class);

		try
		{
			final int[] appWidgetIds = appWidgetManager.getAppWidgetIds(providerName);

			if (appWidgetIds.length > 0)
			{
				final Wallet wallet = application.getWallet();
				final BigInteger balance = wallet.getBalance(BalanceType.ESTIMATED);

				WalletBalanceWidgetProvider.updateWidgets(this, appWidgetManager, appWidgetIds, balance);
			}
		}
		catch (final RuntimeException x) // system server dead?
		{
			log.warn("cannot update app widgets", x);
		}
	}

	private void maybeRotateKeys()
	{
		final Wallet wallet = application.getWallet();
		wallet.setKeyRotationEnabled(false);

		final StoredBlock chainHead = blockChain.getChainHead();

		new Thread()
		{
			@Override
			public void run()
			{
				final boolean replaying = chainHead.getHeight() < bestChainHeightEver; // checking again

				wallet.setKeyRotationEnabled(!replaying);
			}
		}.start();
	}
}
