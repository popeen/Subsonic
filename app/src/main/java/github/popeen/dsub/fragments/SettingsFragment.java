/*
  This file is part of Subsonic.
	Subsonic is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.
	Subsonic is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
	GNU General Public License for more details.
	You should have received a copy of the GNU General Public License
	along with Subsonic. If not, see <http://www.gnu.org/licenses/>.
	Copyright 2014 (C) Scott Jackson
*/

package github.popeen.dsub.fragments;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.text.InputType;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.jsoup.Jsoup;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyManagementException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import github.popeen.dsub.R;
import github.popeen.dsub.activity.SubsonicActivity;
import github.popeen.dsub.service.DownloadService;
import github.popeen.dsub.service.HeadphoneListenerService;
import github.popeen.dsub.service.MusicService;
import github.popeen.dsub.service.MusicServiceFactory;
import github.popeen.dsub.util.Constants;
import github.popeen.dsub.util.EnvironmentVariables;
import github.popeen.dsub.util.FileUtil;
import github.popeen.dsub.util.LoadingTask;
import github.popeen.dsub.util.MediaRouteManager;
import github.popeen.dsub.util.SyncUtil;
import github.popeen.dsub.util.Util;
import github.popeen.dsub.view.CacheLocationPreference;
import github.popeen.dsub.view.ErrorDialog;

public class SettingsFragment extends PreferenceCompatFragment implements SharedPreferences.OnSharedPreferenceChangeListener {
	private final static String TAG = SettingsFragment.class.getSimpleName();

	private final Map<String, ServerSettings> serverSettings = new LinkedHashMap<String, ServerSettings>();
	private boolean testingConnection;
	private ListPreference theme;
	private ListPreference maxBitrateWifi;
	private ListPreference maxBitrateMobile;
	private ListPreference maxVideoBitrateWifi;
	private ListPreference maxVideoBitrateMobile;
	private ListPreference networkTimeout;
	private CacheLocationPreference cacheLocation;
	private ListPreference preloadCountWifi;
	private ListPreference preloadCountMobile;
	private ListPreference keepPlayedCount;
	private ListPreference tempLoss;
	private ListPreference pauseDisconnect;
	private Preference addServerPreference;
	private Preference serverHelpPreference;
	private PreferenceCategory serversCategory;
	private ListPreference songPressAction;
	private ListPreference videoPlayer;
	private ListPreference syncInterval;
	private CheckBoxPreference syncEnabled;
	private CheckBoxPreference syncWifi;
	private CheckBoxPreference syncNotification;
	private CheckBoxPreference syncStarred;
	private CheckBoxPreference syncMostRecent;
	private CheckBoxPreference replayGain;
	private ListPreference replayGainType;
	private Preference replayGainBump;
	private Preference replayGainUntagged;
	private String internalSSID;
	private String internalSSIDDisplay;
	private EditTextPreference cacheSize;
	private ListPreference openToTab;

	private int serverCount = 3;
	private SharedPreferences settings;
	private DecimalFormat megabyteFromat;

	@Override
	public void onCreate(Bundle bundle) {
		super.onCreate(bundle);
		if(Build.VERSION.SDK_INT >= 21) {
			CheckBoxPreference mediaButtons = (CheckBoxPreference) findPreference("mediaButtons");
			if (mediaButtons != null) {
				PreferenceCategory otherCategory = (PreferenceCategory) findPreference("otherSettings");
				otherCategory.removePreference(mediaButtons);
			}
		}

		int instance = this.getArguments().getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, -1);
		if (instance != -1) {
			PreferenceScreen preferenceScreen = expandServer(instance);
			setPreferenceScreen(preferenceScreen);

			serverSettings.put(Integer.toString(instance), new ServerSettings(instance));
			onInitPreferences(preferenceScreen);
		}

	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		SharedPreferences prefs = Util.getPreferences(context);
		prefs.unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onStartNewFragment(String name) {
		SettingsFragment newFragment = new SettingsFragment();
		Bundle args = new Bundle();

		int xml = 0;
		if("appearance".equals(name)) {
			xml = R.xml.settings_appearance;
		} else if("drawer".equals(name)) {
			xml = R.xml.settings_drawer;
		} else if("cache".equals(name)) {
			xml = R.xml.settings_cache;
		} else if("sync".equals(name)) {
			xml = R.xml.settings_sync;
		} else if("playback".equals(name)) {
			xml = R.xml.settings_playback;
		} else if("servers".equals(name)) {
			// if(Util.installedFromPlayStore(context) || !Util.isSignedByPopeen(context)) {
			xml = R.xml.settings_servers;
			// }
		} else if ("cast".equals(name)) {
			xml = R.xml.settings_cast;
		} else if ("advanced".equals(name)) {
			xml = R.xml.settings_advanced;
		} else if ("help".equals(name)) {
			xml = R.xml.settings_help;
		}

		if(xml != 0) {
			args.putInt(Constants.INTENT_EXTRA_FRAGMENT_TYPE, xml);
			newFragment.setArguments(args);
			replaceFragment(newFragment);
		}
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// Random error I have no idea how to reproduce
		if(sharedPreferences == null) {
			return;
		}

		update();

		if (Constants.PREFERENCES_KEY_HIDE_MEDIA.equals(key)) {
			setHideMedia(sharedPreferences.getBoolean(key, false));
		}
		else if (Constants.PREFERENCES_KEY_MEDIA_BUTTONS.equals(key)) {
			setMediaButtonsEnabled(sharedPreferences.getBoolean(key, true));
		}
		else if (Constants.PREFERENCES_KEY_CACHE_LOCATION.equals(key)) {
			setCacheLocation(sharedPreferences.getString(key, ""));
		}
		else if (Constants.PREFERENCES_KEY_SLEEP_TIMER_DURATION.equals(key)){
			DownloadService downloadService = DownloadService.getInstance();
			downloadService.setSleepTimerDuration(Integer.parseInt(sharedPreferences.getString(key, "60")));
		}
		else if(Constants.PREFERENCES_KEY_SYNC_MOST_RECENT.equals(key)) {
			SyncUtil.removeMostRecentSyncFiles(context);
		} else if(Constants.PREFERENCES_KEY_REPLAY_GAIN.equals(key) || Constants.PREFERENCES_KEY_REPLAY_GAIN_BUMP.equals(key) || Constants.PREFERENCES_KEY_REPLAY_GAIN_UNTAGGED.equals(key)) {
			DownloadService downloadService = DownloadService.getInstance();
			if(downloadService != null) {
				downloadService.reapplyVolume();
			}
		} else if(Constants.PREFERENCES_KEY_START_ON_HEADPHONES.equals(key)) {
			Intent serviceIntent = new Intent();
			serviceIntent.setClassName(context.getPackageName(), HeadphoneListenerService.class.getName());

			if(sharedPreferences.getBoolean(key, false)) {
				context.startService(serviceIntent);
			} else {
				context.stopService(serviceIntent);
			}
		} else if(Constants.PREFERENCES_KEY_THEME.equals(key)) {
			String value = sharedPreferences.getString(key, null);
		/* TODO tag, location
			if("day/night".equals(value) || "day/black".equals(value)) {
				if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
					ActivityCompat.requestPermissions(context, new String[]{ Manifest.permission.ACCESS_COARSE_LOCATION }, SubsonicActivity.PERMISSIONS_REQUEST_LOCATION);
				}
			}
		 */
		} else if(Constants.PREFERENCES_KEY_DLNA_CASTING_ENABLED.equals(key)) {
			DownloadService downloadService = DownloadService.getInstance();
			if(downloadService != null) {
				MediaRouteManager mediaRouter = downloadService.getMediaRouter();

				Boolean enabled = sharedPreferences.getBoolean(key, true);
				if (enabled) {
					mediaRouter.addDLNAProvider();
				} else {
					mediaRouter.removeDLNAProvider();
				}
			}
		}

		scheduleBackup();
	}

	@Override
	protected void onInitPreferences(PreferenceScreen preferenceScreen) {
		this.setTitle(preferenceScreen.getTitle());

		internalSSID = Util.getSSID(context);
		if (internalSSID == null) {
			internalSSID = "";
		}
		internalSSIDDisplay = context.getResources().getString(R.string.settings_server_local_network_ssid_hint, internalSSID);

		theme = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_THEME);
		maxBitrateWifi = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_MAX_BITRATE_WIFI);
		maxBitrateMobile = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_MAX_BITRATE_MOBILE);;
		//maxVideoBitrateWifi = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_MAX_VIDEO_BITRATE_WIFI);
		//maxVideoBitrateMobile = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_MAX_VIDEO_BITRATE_MOBILE);
		networkTimeout = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_NETWORK_TIMEOUT);
		cacheLocation = (CacheLocationPreference) this.findPreference(Constants.PREFERENCES_KEY_CACHE_LOCATION);
		preloadCountWifi = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_PRELOAD_COUNT_WIFI);
		preloadCountMobile = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_PRELOAD_COUNT_MOBILE);
		keepPlayedCount = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_KEEP_PLAYED_CNT);
		tempLoss = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_TEMP_LOSS);
		pauseDisconnect = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_PAUSE_DISCONNECT);
		serversCategory = (PreferenceCategory) this.findPreference(Constants.PREFERENCES_KEY_SERVER_KEY);
		addServerPreference = this.findPreference(Constants.PREFERENCES_KEY_SERVER_ADD);
		serverHelpPreference = this.findPreference(Constants.PREFERENCES_KEY_SERVER_HELP);
		//videoPlayer = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_VIDEO_PLAYER);
		songPressAction = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_SONG_PRESS_ACTION);
		syncInterval = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_SYNC_INTERVAL);
		syncEnabled = (CheckBoxPreference) this.findPreference(Constants.PREFERENCES_KEY_SYNC_ENABLED);
		syncWifi = (CheckBoxPreference) this.findPreference(Constants.PREFERENCES_KEY_SYNC_WIFI);
		syncNotification = (CheckBoxPreference) this.findPreference(Constants.PREFERENCES_KEY_SYNC_NOTIFICATION);
		syncStarred = (CheckBoxPreference) this.findPreference(Constants.PREFERENCES_KEY_SYNC_STARRED);
		syncMostRecent = (CheckBoxPreference) this.findPreference(Constants.PREFERENCES_KEY_SYNC_MOST_RECENT);
		replayGain = (CheckBoxPreference) this.findPreference(Constants.PREFERENCES_KEY_REPLAY_GAIN);
		replayGainType = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_REPLAY_GAIN_TYPE);
		replayGainBump = this.findPreference(Constants.PREFERENCES_KEY_REPLAY_GAIN_BUMP);
		replayGainUntagged = this.findPreference(Constants.PREFERENCES_KEY_REPLAY_GAIN_UNTAGGED);
		cacheSize = (EditTextPreference) this.findPreference(Constants.PREFERENCES_KEY_CACHE_SIZE);
		openToTab = (ListPreference) this.findPreference(Constants.PREFERENCES_KEY_OPEN_TO_TAB);

		settings = Util.getPreferences(context);
		serverCount = settings.getInt(Constants.PREFERENCES_KEY_SERVER_COUNT, 1);

		if(cacheSize != null) {
			this.findPreference("clearCache").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Util.confirmDialog(context, R.string.common_delete, R.string.common_confirm_message_cache, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							new LoadingTask<Void>(context, false) {
								@Override
								protected Void doInBackground() throws Throwable {
									FileUtil.deleteMusicDirectory(context);
									FileUtil.deleteSerializedCache(context);
									FileUtil.deleteArtworkCache(context);
									FileUtil.deleteAvatarCache(context);
									return null;
								}

								@Override
								protected void done(Void result) {
									Util.toast(context, R.string.settings_cache_clear_complete);
								}

								@Override
								protected void error(Throwable error) {
									Util.toast(context, getErrorMessage(error), false);
								}
							}.execute();
						}
					});
					return false;
				}
			});




		}

		if((PreferenceCategory) this.findPreference("helpCategory") != null) {

			this.findPreference("visitFaq").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Uri uri = Uri.parse("https://booksonic.org/faq");
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
					startActivity(browserIntent);
					return true;
				}
			});

			this.findPreference("visitSubredit").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Uri uri = Uri.parse("https://www.reddit.com/r/Booksonic/");
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
					startActivity(browserIntent);
					return true;
				}
			});

			this.findPreference("sendLogfile").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					try {
						final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
						new LoadingTask<String>(context) {
							@Override
							protected String doInBackground() throws Throwable {
								updateProgress("Gathering Logs");
								File logcat = new File(Environment.getExternalStorageDirectory(), "dsub-logcat.txt");
								Util.delete(logcat);
								Process logcatProc = null;

								try {
									List<String> progs = new ArrayList<String>();
									progs.add("logcat");
									progs.add("-v");
									progs.add("time");
									progs.add("-d");
									progs.add("-f");
									progs.add(logcat.getCanonicalPath());
									progs.add("*:I");

									logcatProc = Runtime.getRuntime().exec(progs.toArray(new String[progs.size()]));
									logcatProc.waitFor();
								} finally {
									if(logcatProc != null) {
										logcatProc.destroy();
									}
								}

								URL url = new URL("https://ptjwebben.se/logs/index.php");
								HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
								StringBuffer responseBuffer = new StringBuffer();
								try {
									urlConnection.setReadTimeout(10000);
									urlConnection.setConnectTimeout(15000);
									urlConnection.setRequestMethod("POST");
									urlConnection.setDoInput(true);
									urlConnection.setDoOutput(true);

									OutputStream os = urlConnection.getOutputStream();
									BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, Constants.UTF_8));
									writer.write("api_dev_key=" + URLEncoder.encode(EnvironmentVariables.LOG_API_KEY, Constants.UTF_8) + "&api_option=paste&api_paste_private=1&api_paste_code=");

									BufferedReader reader = null;
									try {
										reader = new BufferedReader(new InputStreamReader(new FileInputStream(logcat)));
										String line;
										while ((line = reader.readLine()) != null) {
											writer.write(URLEncoder.encode(line + "\n", Constants.UTF_8));
										}
									} finally {
										Util.close(reader);
									}

									File stacktrace = new File(Environment.getExternalStorageDirectory(), "dsub-stacktrace.txt");
									if(stacktrace.exists() && stacktrace.isFile()) {
										writer.write("\n\nMost Recent Stacktrace:\n\n");

										reader = null;
										try {
											reader = new BufferedReader(new InputStreamReader(new FileInputStream(stacktrace)));
											String line;
											while ((line = reader.readLine()) != null) {
												writer.write(URLEncoder.encode(line + "\n", Constants.UTF_8));
											}
										} finally {
											Util.close(reader);
										}
									}

									writer.flush();
									writer.close();
									os.close();

									BufferedReader in = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
									String inputLine;
									while ((inputLine = in.readLine()) != null) {
										responseBuffer.append(inputLine);
									}
									in.close();
								} finally {
									urlConnection.disconnect();
								}

								String response = responseBuffer.toString();

								urlConnection.disconnect();
								if(response.indexOf("http") == 0) {
									return response.replace("http:", "https:");
								} else {
									throw new Exception("Paste Error: " + response);
								}
							}

							@Override
							protected void error(Throwable error) {
								Log.e(TAG, "Failed to gather logs", error);
								Util.toast(context, "Failed to gather logs");
							}

							@Override
							protected void done(String logcat) {
								String footer = "\nLogs: " + logcat;
								footer += "Android SDK: " + Build.VERSION.SDK;
								footer += "\nDevice Model: " + Build.MODEL;
								footer += "\nDevice Name: " + Build.MANUFACTURER + " "  + Build.PRODUCT;
								footer += "\nROM: " + Build.DISPLAY;
								footer += "\nBuild Number: " + packageInfo.versionCode;

								try {
									MessageDigest md;
									md = MessageDigest.getInstance("SHA");
									md.update(context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES).signatures[0].toByteArray());
									footer += "\nSignature: " + new String(Base64.encode(md.digest(), 0));
								}catch(Exception e){}


								Intent selectorIntent = new Intent(Intent.ACTION_SENDTO);
								selectorIntent.setData(Uri.parse("mailto:"));

								final Intent emailIntent = new Intent(Intent.ACTION_SEND);
								emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{"support@booksonic.org"});
								emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Booksonic " + packageInfo.versionName + " Error Logs");
								emailIntent.putExtra(Intent.EXTRA_TEXT, "Describe the problem here\n\n\n" + footer);
								emailIntent.setSelector( selectorIntent );

								startActivity(Intent.createChooser(emailIntent, "Send log..."));
							}
						}.execute();
					} catch(Exception e) {}
					return true;
				}
			});

			this.findPreference("visitWindowsGuide").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Uri uri = Uri.parse("https://popeen.com/2016/01/14/how-to-stream-audiobooks-to-your-phone-with-booksonic/");
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
					startActivity(browserIntent);
					return true;
				}
			});

			this.findPreference("visitDockerGuide").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Uri uri = Uri.parse("https://hub.docker.com/r/linuxserver/booksonic/");
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
					startActivity(browserIntent);
					return true;
				}
			});


			try {
				MessageDigest md;
				md = MessageDigest.getInstance("SHA");
				md.update(context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES).signatures[0].toByteArray());
				final PackageInfo packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
				String aboutInfo = "\nVersion: " + packageInfo.versionName;
				aboutInfo += "\nBuild Number: " + packageInfo.versionCode;
				aboutInfo += "\nSignature: " + new String(Base64.encode(md.digest(), 0));
				this.findPreference("copyAppInfo").setSummary("Click here to copy the information about your app version to the clipboard\n\n" + aboutInfo);
			}catch(Exception e){ this.findPreference("copyAppInfo").setSummary("Click here to copy the information about your app version to the clipboard\n\nCould not get app data"); }

			this.findPreference("copyAppInfo").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
					android.content.ClipData clip = android.content.ClipData.newPlainText("Copied to clipboard", preference.getSummary().toString().split("\n\n")[1]);
					clipboard.setPrimaryClip(clip);
					Util.toast(getActivity(), "Copied to clipboard");
					return true;
				}
			});
		}

		if(syncEnabled != null) {
			this.findPreference(Constants.PREFERENCES_KEY_SYNC_ENABLED).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					Boolean syncEnabled = (Boolean) newValue;

					Account account = new Account(Constants.SYNC_ACCOUNT_NAME, Constants.SYNC_ACCOUNT_TYPE);
					ContentResolver.setSyncAutomatically(account, Constants.SYNC_ACCOUNT_PLAYLIST_AUTHORITY, syncEnabled);
					ContentResolver.setSyncAutomatically(account, Constants.SYNC_ACCOUNT_PODCAST_AUTHORITY, syncEnabled);

					return true;
				}
			});
			syncInterval.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
				@Override
				public boolean onPreferenceChange(Preference preference, Object newValue) {
					Integer syncInterval = Integer.parseInt(((String) newValue));

					Account account = new Account(Constants.SYNC_ACCOUNT_NAME, Constants.SYNC_ACCOUNT_TYPE);
					ContentResolver.addPeriodicSync(account, Constants.SYNC_ACCOUNT_PLAYLIST_AUTHORITY, new Bundle(), 60L * syncInterval);
					ContentResolver.addPeriodicSync(account, Constants.SYNC_ACCOUNT_PODCAST_AUTHORITY, new Bundle(), 60L * syncInterval);

					return true;
				}
			});
		}

		if(serversCategory != null) {
			addServerPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					serverCount++;
					int instance = serverCount;
					serversCategory.addPreference(addServer(serverCount));

					SharedPreferences.Editor editor = settings.edit();
					editor.putInt(Constants.PREFERENCES_KEY_SERVER_COUNT, serverCount);
					// Reset set folder ID
					editor.putString(Constants.PREFERENCES_KEY_MUSIC_FOLDER_ID + instance, null);
					editor.putString(Constants.PREFERENCES_KEY_SERVER_URL + instance, "http://yourhost");
					editor.putString(Constants.PREFERENCES_KEY_SERVER_NAME + instance, getResources().getString(R.string.settings_server_unused));
					editor.commit();

					ServerSettings ss = new ServerSettings(instance);
					serverSettings.put(String.valueOf(instance), ss);
					ss.update();

					return true;
				}
			});

			serverHelpPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
				@Override
				public boolean onPreferenceClick(Preference preference) {
					Uri uri = Uri.parse("https://popeen.com/2016/01/14/how-to-stream-audiobooks-to-your-phone-with-booksonic/");
					Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
					startActivity(browserIntent);
					return true;
				}
			});

			serversCategory.setOrderingAsAdded(false);
			for (int i = 1; i <= serverCount; i++) {
				serversCategory.addPreference(addServer(i));
				serverSettings.put(String.valueOf(i), new ServerSettings(i));
			}
		}

		SharedPreferences prefs = Util.getPreferences(context);
		prefs.registerOnSharedPreferenceChangeListener(this);

		update();
	}

	private void scheduleBackup() {
		try {
			Class managerClass = Class.forName("android.app.backup.BackupManager");
			Constructor managerConstructor = managerClass.getConstructor(Context.class);
			Object manager = managerConstructor.newInstance(context);
			Method m = managerClass.getMethod("dataChanged");
			m.invoke(manager);
		} catch(ClassNotFoundException e) {
			Log.e(TAG, "No backup manager found");
		} catch(Throwable t) {
			Log.e(TAG, "Scheduling backup failed " + t);
			t.printStackTrace();
		}
	}

	private void update() {
		if (testingConnection) {
			return;
		}

		if(theme != null) {
			theme.setSummary(theme.getEntry());
		}
		if(openToTab != null) {
			openToTab.setSummary(openToTab.getEntry());
		}

		if(cacheSize != null) {
			maxBitrateWifi.setSummary(maxBitrateWifi.getEntry());
			maxBitrateMobile.setSummary(maxBitrateMobile.getEntry());;
			//maxVideoBitrateWifi.setSummary(maxVideoBitrateWifi.getEntry());
			//maxVideoBitrateMobile.setSummary(maxVideoBitrateMobile.getEntry());
			networkTimeout.setSummary(networkTimeout.getEntry());
			cacheLocation.setSummary(cacheLocation.getText());
			preloadCountWifi.setSummary(preloadCountWifi.getEntry());
			preloadCountMobile.setSummary(preloadCountMobile.getEntry());

			try {
				if(megabyteFromat == null) {
					megabyteFromat = new DecimalFormat(getResources().getString(R.string.util_bytes_format_megabyte));
				}

				String maxSize = megabyteFromat.format((double) Integer.parseInt(cacheSize.getText())).replace(".00", "");
				cacheSize.setSummary("If " + maxSize + " has been downloaded the app will automatically delete the oldest files when downloading new ones");
			} catch(Exception e) {
				Log.e(TAG, "Failed to format cache size", e);
				cacheSize.setSummary(cacheSize.getText());
			}
		}

		if(keepPlayedCount != null) {
			keepPlayedCount.setSummary(keepPlayedCount.getEntry());
			tempLoss.setSummary(tempLoss.getEntry());
			pauseDisconnect.setSummary(pauseDisconnect.getEntry());
			//videoPlayer.setSummary(videoPlayer.getEntry());
		}

		if(syncEnabled != null) {
			syncInterval.setSummary(syncInterval.getEntry());

			if(syncEnabled.isChecked()) {
				if(!syncInterval.isEnabled()) {
					syncInterval.setEnabled(true);
					syncWifi.setEnabled(true);
					syncNotification.setEnabled(true);
					syncStarred.setEnabled(true);
					syncMostRecent.setEnabled(true);
				}
			} else {
				if(syncInterval.isEnabled()) {
					syncInterval.setEnabled(false);
					syncWifi.setEnabled(false);
					syncNotification.setEnabled(false);
					syncStarred.setEnabled(false);
					syncMostRecent.setEnabled(false);
				}
			}
		}

		if(replayGain != null) {

			if(replayGain.isChecked()) {
				replayGainType.setEnabled(true);
				replayGainBump.setEnabled(true);
				replayGainUntagged.setEnabled(true);
			} else {
				replayGainType.setEnabled(false);
				replayGainBump.setEnabled(false);
				replayGainUntagged.setEnabled(false);
			}
			replayGainType.setSummary(replayGainType.getEntry());

		}
		for (ServerSettings ss : serverSettings.values()) {
			ss.update();
		}
	}
	public void checkForRemoved() {
		for (ServerSettings ss : serverSettings.values()) {
			if(!ss.update()) {
				serversCategory.removePreference(ss.getScreen());
				serverCount--;
			}
		}
	}

	private PreferenceScreen addServer(final int instance) {
		final PreferenceScreen screen = this.getPreferenceManager().createPreferenceScreen(context);
		screen.setKey(Constants.PREFERENCES_KEY_SERVER_KEY + instance);
		screen.setOrder(instance);

		screen.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				SettingsFragment newFragment = new SettingsFragment();

				Bundle args = new Bundle();
				args.putInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, instance);
				newFragment.setArguments(args);

				replaceFragment(newFragment);
				return false;
			}
		});

		return screen;
	}

	private PreferenceScreen expandServer(final int instance) {
		final PreferenceScreen screen = this.getPreferenceManager().createPreferenceScreen(context);
		screen.setTitle(R.string.settings_server_unused);
		screen.setKey(Constants.PREFERENCES_KEY_SERVER_KEY + instance);

		final EditTextPreference serverNamePreference = new EditTextPreference(context);
		serverNamePreference.setKey(Constants.PREFERENCES_KEY_SERVER_NAME + instance);
		serverNamePreference.setDefaultValue(getResources().getString(R.string.settings_server_unused));
		serverNamePreference.setTitle(R.string.settings_server_name);
		serverNamePreference.setDialogTitle(R.string.settings_server_name);

		if (serverNamePreference.getText() == null) {
			serverNamePreference.setText(getResources().getString(R.string.settings_server_unused));
		}

		serverNamePreference.setSummary(serverNamePreference.getText());

		final EditTextPreference serverUrlPreference = new EditTextPreference(context);
		serverUrlPreference.setKey(Constants.PREFERENCES_KEY_SERVER_URL + instance);
		serverUrlPreference.getEditText().setInputType(InputType.TYPE_TEXT_VARIATION_URI);
		serverUrlPreference.setDefaultValue("http://yourhost");
		serverUrlPreference.setTitle(R.string.settings_server_address);
		serverUrlPreference.setDialogTitle(R.string.settings_server_address);

		if (serverUrlPreference.getText() == null) {
			serverUrlPreference.setText("http://yourhost");
		}

		serverUrlPreference.setSummary(serverUrlPreference.getText());
		screen.setSummary(serverUrlPreference.getText());

		/*
		final EditTextPreference serverLocalNetworkSSIDPreference = new EditTextPreference(context) {
			@Override
			protected void onAddEditTextToDialogView(View dialogView, final EditText editText) {

				super.onAddEditTextToDialogView(dialogView, editText);
				ViewGroup root = (ViewGroup) ((ViewGroup) dialogView).getChildAt(0);
				internalSSID = Util.getSSID(context);
				internalSSIDDisplay = context.getResources().getString(R.string.settings_server_local_network_ssid_hint, internalSSID);
				if(!(internalSSID == null || internalSSID.equals("") || internalSSID.equals("<unknown ssid>") || internalSSID.equals("NULL"))) {
					Button defaultButton = new Button(getContext());
					defaultButton.setText(internalSSIDDisplay);
					defaultButton.setOnClickListener(new View.OnClickListener() {
						@Override
						public void onClick(View v) {
							editText.setText(internalSSID);
						}
					});
					root.addView(defaultButton);
				}else{
					editText.setEnabled(false);
					WifiManager wifiManager = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
					if (!wifiManager.isWifiEnabled()) {
						TextView warning = new TextView(getContext());
						warning.setText("Your Wi-Fi is disabled, please enable it and then try again");
						root.addView(warning);
					}else{
						TextView warning = new TextView(getContext());
						warning.setText("Android considers SSID names location data, to use this feature you first need to give location permission");
						root.addView(warning);
					}
				}
			}
		};
		serverLocalNetworkSSIDPreference.setKey(Constants.PREFERENCES_KEY_SERVER_LOCAL_NETWORK_SSID + instance);
		serverLocalNetworkSSIDPreference.setTitle(R.string.settings_server_local_network_ssid);
		serverLocalNetworkSSIDPreference.setDialogTitle(R.string.settings_server_local_network_ssid);

		Preference locationPermissionPreference = new Preference(context);
		locationPermissionPreference.setKey("LocationPermission");
		locationPermissionPreference.setPersistent(false);
		locationPermissionPreference.setTitle("Give location permission");
		locationPermissionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				checkLocationPermission();
				return false;
			}
		});
		*/
		final EditTextPreference serverInternalUrlPreference = new EditTextPreference(context);
		serverInternalUrlPreference.setKey(Constants.PREFERENCES_KEY_SERVER_INTERNAL_URL + instance);
		serverInternalUrlPreference.getEditText().setInputType(InputType.TYPE_TEXT_VARIATION_URI);
		serverInternalUrlPreference.setDefaultValue("");
		serverInternalUrlPreference.setTitle(R.string.settings_server_internal_address);
		serverInternalUrlPreference.setDialogTitle(R.string.settings_server_internal_address);

		final EditTextPreference serverUsernamePreference = new EditTextPreference(context);
		serverUsernamePreference.setKey(Constants.PREFERENCES_KEY_USERNAME + instance);
		serverUsernamePreference.setTitle(R.string.settings_server_username);
		serverUsernamePreference.setDialogTitle(R.string.settings_server_username);

		final EditTextPreference serverPasswordPreference = new EditTextPreference(context);
		serverPasswordPreference.setKey(Constants.PREFERENCES_KEY_PASSWORD + instance);
		serverPasswordPreference.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
		serverPasswordPreference.setSummary("***");
		serverPasswordPreference.setTitle(R.string.settings_server_password);

		final CheckBoxPreference serverSyncPreference = new CheckBoxPreference(context);
		serverSyncPreference.setKey(Constants.PREFERENCES_KEY_SERVER_SYNC + instance);
		serverSyncPreference.setChecked(Util.isSyncEnabled(context, instance));
		serverSyncPreference.setSummary(R.string.settings_server_sync_summary);
		serverSyncPreference.setTitle(R.string.settings_server_sync);

		final CheckBoxPreference serverAuthHeaderPreference = new CheckBoxPreference(context);
		serverAuthHeaderPreference.setKey(Constants.PREFERENCES_KEY_SERVER_AUTHHEADER + instance);
		serverAuthHeaderPreference.setChecked(Util.isAuthHeaderEnabled(context, instance));
		serverAuthHeaderPreference.setSummary(R.string.settings_server_authheaders_summary);
		serverAuthHeaderPreference.setTitle(R.string.settings_server_authheaders);

		final Preference serverOpenBrowser = new Preference(context);
		serverOpenBrowser.setKey(Constants.PREFERENCES_KEY_OPEN_BROWSER);
		serverOpenBrowser.setPersistent(false);
		serverOpenBrowser.setTitle(R.string.settings_server_open_browser);
		serverOpenBrowser.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				openInBrowser(instance);
				return true;
			}
		});

		Preference serverRemoveServerPreference = new Preference(context);
		serverRemoveServerPreference.setKey(Constants.PREFERENCES_KEY_SERVER_REMOVE + instance);
		serverRemoveServerPreference.setPersistent(false);
		serverRemoveServerPreference.setTitle(R.string.settings_servers_remove);

		serverRemoveServerPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				Util.confirmDialog(context, R.string.common_delete, screen.getTitle().toString(), new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						// Reset values to null so when we ask for them again they are new
						serverNamePreference.setText(null);
						serverUrlPreference.setText(null);
						serverUsernamePreference.setText(null);
						serverPasswordPreference.setText(null);

						// Don't use Util.getActiveServer since it is 0 if offline
						int activeServer = Util.getPreferences(context).getInt(Constants.PREFERENCES_KEY_SERVER_INSTANCE, 1);
						for (int i = instance; i <= serverCount; i++) {
							Util.removeInstanceName(context, i, activeServer);
						}

						serverCount--;
						SharedPreferences.Editor editor = settings.edit();
						editor.putInt(Constants.PREFERENCES_KEY_SERVER_COUNT, serverCount);
						editor.commit();

						removeCurrent();

						SubsonicFragment parentFragment = context.getCurrentFragment();
						if(parentFragment instanceof SettingsFragment) {
							SettingsFragment serverSelectionFragment = (SettingsFragment) parentFragment;
							serverSelectionFragment.checkForRemoved();
						}
					}
				});

				return true;
			}
		});

		Preference serverTestConnectionPreference = new Preference(context);
		serverTestConnectionPreference.setKey(Constants.PREFERENCES_KEY_TEST_CONNECTION + instance);
		serverTestConnectionPreference.setPersistent(false);
		serverTestConnectionPreference.setTitle(R.string.settings_test_connection_title);
		serverTestConnectionPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
			@Override
			public boolean onPreferenceClick(Preference preference) {
				testConnection(instance);
				return false;
			}
		});

		screen.addPreference(serverNamePreference);
		screen.addPreference(serverUrlPreference);
		screen.addPreference(serverInternalUrlPreference);
		//screen.addPreference(serverLocalNetworkSSIDPreference);
		//screen.addPreference(locationPermissionPreference);
		screen.addPreference(serverUsernamePreference);
		screen.addPreference(serverPasswordPreference);
		screen.addPreference(serverSyncPreference);
		screen.addPreference(serverAuthHeaderPreference);
		screen.addPreference(serverTestConnectionPreference);
		screen.addPreference(serverOpenBrowser);
		screen.addPreference(serverRemoveServerPreference);

		return screen;
	}

	public boolean checkLocationPermission() {
		if (ContextCompat.checkSelfPermission(context,
				Manifest.permission.ACCESS_FINE_LOCATION)
				!= PackageManager.PERMISSION_GRANTED) {

			// Should we show an explanation?
			if (ActivityCompat.shouldShowRequestPermissionRationale(context,
					Manifest.permission.ACCESS_FINE_LOCATION)) {

				// Show an explanation to the user *asynchronously* -- don't block
				// this thread waiting for the user's response! After the user
				// sees the explanation, try again to request the permission.
				new AlertDialog.Builder(context)
						.setTitle("Location permission needed")
						.setMessage("Location permission needed")
						.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {
								//Prompt the user once explanation has been shown
								ActivityCompat.requestPermissions(context,
										new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
										99);
							}
						})
						.create()
						.show();


			} else {
				// No explanation needed, we can request the permission.
				ActivityCompat.requestPermissions(context,
						new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
						99);
			}
			return false;
		} else {
			return true;
		}
	}

	private void setHideMedia(boolean hide) {
		File nomediaDir = new File(FileUtil.getSubsonicDirectory(context), ".nomedia");
		File musicNoMedia = new File(FileUtil.getMusicDirectory(context), ".nomedia");
		if (hide && !nomediaDir.exists()) {
			try {
				if (!nomediaDir.createNewFile()) {
					Log.w(TAG, "Failed to create " + nomediaDir);
				}
			} catch(Exception e) {
				Log.w(TAG, "Failed to create " + nomediaDir, e);
			}

			try {
				if(!musicNoMedia.createNewFile()) {
					Log.w(TAG, "Failed to create " + musicNoMedia);
				}
			} catch(Exception e) {
				Log.w(TAG, "Failed to create " + musicNoMedia, e);
			}
		} else if (!hide && nomediaDir.exists()) {
			if (!nomediaDir.delete()) {
				Log.w(TAG, "Failed to delete " + nomediaDir);
			}
			if(!musicNoMedia.delete()) {
				Log.w(TAG, "Failed to delete " + musicNoMedia);
			}
		}
		Util.toast(context, R.string.settings_hide_media_toast, false);
	}

	private void setMediaButtonsEnabled(boolean enabled) {
		if (enabled) {
			Util.registerMediaButtonEventReceiver(context);
		} else {
			Util.unregisterMediaButtonEventReceiver(context);
		}
	}

	private void setCacheLocation(String path) {
		File dir = new File(path);
		if (!FileUtil.verifyCanWrite(dir)) {
			Util.toast(context, R.string.settings_cache_location_error, false);

			// Reset it to the default.
			String defaultPath = FileUtil.getDefaultMusicDirectory(context).getPath();
			if (!defaultPath.equals(path)) {
				SharedPreferences prefs = Util.getPreferences(context);
				SharedPreferences.Editor editor = prefs.edit();
				editor.putString(Constants.PREFERENCES_KEY_CACHE_LOCATION, defaultPath);
				editor.commit();

				if(cacheLocation != null) {
					cacheLocation.setSummary(defaultPath);
					cacheLocation.setText(defaultPath);
				}
			}

			// Clear download queue.
			DownloadService downloadService = DownloadService.getInstance();
			downloadService.clear();
		}
	}

	private void testConnection(final int instance) {
		LoadingTask<Boolean> task = new LoadingTask<Boolean>(context) {
			private int previousInstance;
			private int statusCode;
			@Override
			protected Boolean doInBackground() throws Throwable {
				updateProgress(R.string.settings_testing_connection);

				statusCode = Jsoup.connect(Util.getRestUrl(context, "ping")).sslSocketFactory(Util.socketFactory()).followRedirects(false).execute().statusCode();

				previousInstance = Util.getActiveServer(context);
				testingConnection = true;
				MusicService musicService = MusicServiceFactory.getMusicService(context);
				try {
					musicService.setInstance(instance);
					musicService.ping(context, this);
					return musicService.isLicenseValid(context, null);
				} finally {
					musicService.setInstance(null);
					testingConnection = false;
				}
			}

			@Override
			protected void done(Boolean licenseValid) {
				if (licenseValid) {
					Util.toast(context, R.string.settings_testing_ok);
				} else {
					Util.toast(context, R.string.settings_testing_unlicensed);
				}
			}

			@Override
			public void cancel() {
				super.cancel();
				Util.setActiveServer(context, previousInstance);
			}

			@Override
			protected void error(Throwable error) {
				Log.w(TAG, error.toString(), error);
				if(statusCode == 200){
					new ErrorDialog(context, getResources().getString(R.string.settings_connection_username_password_wrong), false);
				}else {
					new ErrorDialog(context, getResources().getString(R.string.settings_connection_failure) +
							" " + getErrorMessage(error), false);
				}
			}
		};
		task.execute();
	}



	private void openInBrowser(final int instance) {
		SharedPreferences prefs = Util.getPreferences(context);
		String url = prefs.getString(Constants.PREFERENCES_KEY_SERVER_URL + instance, null);
		if(url == null) {
			new ErrorDialog(context, R.string.settings_invalid_url, false);
			return;
		}
		Uri uriServer = Uri.parse(url);

		Intent browserIntent = new Intent(Intent.ACTION_VIEW, uriServer);
		startActivity(browserIntent);
	}

	private class ServerSettings {
		private int instance;
		private EditTextPreference serverName;
		private EditTextPreference serverUrl;
		private EditTextPreference serverLocalNetworkSSID;
		private EditTextPreference serverInternalUrl;
		private EditTextPreference username;
		private PreferenceScreen screen;

		private ServerSettings(int instance) {
			this.instance = instance;
			screen = (PreferenceScreen) SettingsFragment.this.findPreference(Constants.PREFERENCES_KEY_SERVER_KEY + instance);
			serverName = (EditTextPreference) SettingsFragment.this.findPreference(Constants.PREFERENCES_KEY_SERVER_NAME + instance);
			serverUrl = (EditTextPreference) SettingsFragment.this.findPreference(Constants.PREFERENCES_KEY_SERVER_URL + instance);
			serverLocalNetworkSSID = (EditTextPreference) SettingsFragment.this.findPreference(Constants.PREFERENCES_KEY_SERVER_LOCAL_NETWORK_SSID + instance);
			serverInternalUrl = (EditTextPreference) SettingsFragment.this.findPreference(Constants.PREFERENCES_KEY_SERVER_INTERNAL_URL + instance);
			username = (EditTextPreference) SettingsFragment.this.findPreference(Constants.PREFERENCES_KEY_USERNAME + instance);

			if(serverName != null) {
				serverUrl.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference preference, Object value) {
						try {
							String url = (String) value;
							new URL(url);
							if (url.contains(" ") || url.contains("@")) {
								throw new Exception();
							}
						} catch (Exception x) {
							new ErrorDialog(context, R.string.settings_invalid_url, false);
							return false;
						}
						return true;
					}
				});
				serverInternalUrl.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference preference, Object value) {
						try {
							String url = (String) value;
							// Allow blank internal IP address
							if ("".equals(url) || url == null) {
								return true;
							}

							new URL(url);
							if (url.contains(" ") || url.contains("@")) {
								throw new Exception();
							}
						} catch (Exception x) {
							new ErrorDialog(context, R.string.settings_invalid_url, false);
							return false;
						}
						return true;
					}
				});

				username.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
					@Override
					public boolean onPreferenceChange(Preference preference, Object value) {
						String username = (String) value;
						if (username == null || !username.equals(username.trim())) {
							new ErrorDialog(context, R.string.settings_invalid_username, false);
							return false;
						}
						return true;
					}
				});
			}
		}

		public PreferenceScreen getScreen() {
			return screen;
		}

		public boolean update() {
			SharedPreferences prefs = Util.getPreferences(context);

			if(prefs.contains(Constants.PREFERENCES_KEY_SERVER_NAME + instance)) {
				if (serverName != null) {
					serverName.setSummary(serverName.getText());
					serverUrl.setSummary(serverUrl.getText());
					//serverLocalNetworkSSID.setSummary(serverLocalNetworkSSID.getText());
					serverInternalUrl.setSummary(serverInternalUrl.getText());
					username.setSummary(username.getText());

					setTitle(serverName.getText());
				}

				String title = prefs.getString(Constants.PREFERENCES_KEY_SERVER_NAME + instance, null);
				String summary = prefs.getString(Constants.PREFERENCES_KEY_SERVER_URL + instance, null);

				if (title != null) {
					screen.setTitle(title);
				} else {
					screen.setTitle(R.string.settings_server_unused);
				}
				if (summary != null) {
					screen.setSummary(summary);
				}

				return true;
			} else {
				return false;
			}
		}
	}
}
