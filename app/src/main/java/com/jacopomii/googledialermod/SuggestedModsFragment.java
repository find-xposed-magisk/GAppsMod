package com.jacopomii.googledialermod;

import static com.jacopomii.googledialermod.Utils.deleteCallrecordingpromptFolder;
import static com.jacopomii.googledialermod.Utils.revertAllMods;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;

import com.google.protobuf.ByteString;
import com.jacopomii.googledialermod.protos.Call_screen_i18n_config;
import com.topjohnwu.superuser.Shell;

public class SuggestedModsFragment extends Fragment {
    private View mView;
    private SwitchCompat mForceEnableCallRecordingSwitch;
    private SwitchCompat mSilenceCallRecordingAlertsSwitch;
    private SwitchCompat mForceEnableCallScreenSwitch;
    private DBFlagsSingleton mDBFlagsSingleton;

    // The following boolean flags force enable or disable Call Recording features
    private final String[] ENABLE_CALL_RECORDING_FLAGS = {
            // Enable Call Recording feature
            "G__enable_call_recording",
            "enable_call_recording_system_feature",
            // Enable Call Recording also for Google Fi / Fides (e2e calls, etc)
            "CallRecording__enable_call_recording_for_fi",
            // Bypass country-related restrictions for call recording feature
            "G__force_within_call_recording_geofence_value",
            // Bypass country-related restrictions for automatic call recording ("always record") feature
            "G__force_within_crosby_geofence_value",
            // Allow the usage of the above two "force geofence" flags
            "G__use_call_recording_geofence_overrides",
            // Show call recording button
            "enable_tidepods_call_recording"
    };

    // The following extensionVal flags concern the announcement audio played when a call recording starts or ends
    private final String[] SILENCE_CALL_RECORDING_ALERTS_FLAGS = {
            // The following flag contains a protobuf list of countries where the use of embedded audio is enforced.
            // If its value is blank, the Dialer will by default use TTS to generate audio call recording alerts.
            "CallRecording__call_recording_countries_with_built_in_audio_file",
            // The following flags are no longer used in recent versions of the Dialer and remain here for backwards compatibility.
            // They were used to contain a protobuf list of countries where the use of embedded or TTS audio was enforced.
            "CallRecording__call_recording_force_enable_built_in_audio_file_countries",
            "CallRecording__call_recording_force_enable_tts_countries",
            // The following flag contains a protobuf hashset with country-language matches, used by Dialer to generate call recording audio alerts via TTS
            // in the right language. If its value is empty, TTS will always fall back to en_US (hardcoded in the Dialer sources).
            "CallRecording__call_recording_countries"
    };

    // The following boolean flags enable or disable Call Screen / Revelio features
    private final String[] ENABLE_CALL_SCREEN_FLAGS = {
            // Enable Call Screen feature for both calls and video-calls
            "G__speak_easy_enabled",
            "enable_video_calling_screen",
            // Bypass Call Screen locale restrictions
            "G__speak_easy_bypass_locale_check",
            // Enable translations for additional locales
            "enable_call_screen_i18n_tidepods",
            // Enable the "listen in" button, which is located at the bottom right during screening
            "G__speak_easy_enable_listen_in_button",
            // Enable the Call Screen Demo page in Dialer settings
            "enable_call_screen_demo",
            // Enable the "See transcript" button in call history, which allows to read call screen transcripts and listen to recordings
            "G__enable_speakeasy_details",
            // Enable Revelio,an advanced version of the Call Screen which allows to automatically filter calls
            "G__enable_revelio",
            "G__enable_revelio_on_bluetooth",
            "G__enable_revelio_on_wired_headset",
            // Bypass Revelio locale restrictions
            "G__bypass_revelio_roaming_check",
            // Enable translations for additional locales also for Revelio
            "G__enable_tidepods_revelio",
            // Enable the Dialer settings option to save screened call audio (it does not depend on the Call Recording feature, but depends on Revelio)
            "G__enable_call_screen_saving_audio",
            // Enable the saving of the transcript also for Revelio
            "enable_revelio_transcript"
    };

    // The following extensionVal flag contains a protobuf (see call_screen_i18n.proto for its definition)
    // which matches the languages to be used for the Call Screen feature to the supported countries
    private final String CALL_SCREEN_I18N_CONFIG_FLAG = "CallScreenI18n__call_screen_i18n_config";

    private CompoundButton.OnCheckedChangeListener mForceEnableCallRecordingSwitchOnCheckedChangeListener;
    private CompoundButton.OnCheckedChangeListener mSilenceCallRecordingAlertsSwitchOnCheckedChangeListener;
    private CompoundButton.OnCheckedChangeListener mForceEnableCallScreenSwitchOnCheckedChangeListener;

    public SuggestedModsFragment() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.suggested_mods_fragment, container, false);

        mDBFlagsSingleton = DBFlagsSingleton.getInstance(requireActivity());

        mForceEnableCallRecordingSwitch = mView.findViewById(R.id.force_enable_call_recording_switch);
        mSilenceCallRecordingAlertsSwitch = mView.findViewById(R.id.silence_call_recording_alerts_switch);
        mForceEnableCallScreenSwitch = mView.findViewById(R.id.force_enable_call_screen_switch);

        mForceEnableCallRecordingSwitchOnCheckedChangeListener = (buttonView, isChecked) -> {
            for (String flag : ENABLE_CALL_RECORDING_FLAGS) {
                mDBFlagsSingleton.updateDBFlag(flag, isChecked);
            }
        };
        mForceEnableCallRecordingSwitch.setOnCheckedChangeListener(mForceEnableCallRecordingSwitchOnCheckedChangeListener);

        mSilenceCallRecordingAlertsSwitchOnCheckedChangeListener = (buttonView, isChecked) -> {
            if (isChecked) {
                for (String flag : SILENCE_CALL_RECORDING_ALERTS_FLAGS) {
                    mDBFlagsSingleton.updateDBFlag(flag, "");
                }
                try {
                    final String dataDir = requireActivity().getApplicationInfo().dataDir;
                    final int uid = requireActivity().getPackageManager().getApplicationInfo("com.google.android.dialer", 0).uid;
                    //TODO: eventualmente splittare in più comandi?
                    Shell.cmd("rm -r /data/data/com.google.android.dialer/files/callrecordingprompt; " +
                            "mkdir /data/data/com.google.android.dialer/files/callrecordingprompt; " +
                            "cp " + dataDir + "/silent_wav.wav /data/data/com.google.android.dialer/files/callrecordingprompt/starting_voice-en_US.wav; " +
                            "cp " + dataDir + "/silent_wav.wav /data/data/com.google.android.dialer/files/callrecordingprompt/ending_voice-en_US.wav; " +
                            "chown -R " + uid + ":" + uid + " /data/data/com.google.android.dialer/files/callrecordingprompt; " +
                            "chmod -R 755 /data/data/com.google.android.dialer/files/callrecordingprompt; " +
                            "chmod 444 /data/data/com.google.android.dialer/files/callrecordingprompt/*; " +
                            "restorecon -R /data/data/com.google.android.dialer/files/callrecordingprompt").exec();
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
            } else {
                mDBFlagsSingleton.deleteFlagOverrides(SILENCE_CALL_RECORDING_ALERTS_FLAGS);
                deleteCallrecordingpromptFolder();
            }
        };
        mSilenceCallRecordingAlertsSwitch.setOnCheckedChangeListener(mSilenceCallRecordingAlertsSwitchOnCheckedChangeListener);

        mForceEnableCallScreenSwitchOnCheckedChangeListener = (buttonView, isChecked) -> {
            if (isChecked) {
                // Ask the user what language the Call Screen feature should use
                String[] supportedLanguages = {"en", "en-AU", "en-GB", "en-IN", "ja-JP", "fr-FR", "hi-IN", "de-DE", "it-IT", "es-ES"};
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.choose_a_language)
                        .setItems(supportedLanguages, (dialog, choice) -> {
                            // Update boolean flags
                            for (String flag : ENABLE_CALL_SCREEN_FLAGS)
                                mDBFlagsSingleton.updateDBFlag(flag, true);

                            // Override the call screen i18n config flag with the user desired language
                            TelephonyManager telephonyManager = (TelephonyManager) requireActivity().getSystemService(Context.TELEPHONY_SERVICE);
                            String simCountryIso = telephonyManager.getSimCountryIso();

                            String chosenLanguage = supportedLanguages[choice];

                            Call_screen_i18n_config call_screen_i18n_config = Call_screen_i18n_config.newBuilder()
                                    .addCountryConfigs(
                                            Call_screen_i18n_config.CountryConfig.newBuilder()
                                                    .setCountry(simCountryIso)
                                                    .setLanguageConfig(
                                                            Call_screen_i18n_config.LanguageConfig.newBuilder()
                                                                    .addLanguages(
                                                                            Call_screen_i18n_config.Language.newBuilder()
                                                                                    .setLanguageCode(chosenLanguage)
                                                                                    .setA6(
                                                                                            Call_screen_i18n_config.A6.newBuilder()
                                                                                                    .setA7(ByteString.copyFrom(new byte[]{2}))
                                                                                    )
                                                                    )
                                                    )
                                    ).build();
                            mDBFlagsSingleton.updateDBFlag(CALL_SCREEN_I18N_CONFIG_FLAG, call_screen_i18n_config.toByteArray());
                        }).create().show();
            } else {
                // Update boolean flags
                for (String flag : ENABLE_CALL_SCREEN_FLAGS)
                    mDBFlagsSingleton.updateDBFlag(flag, false);
                // Remove the call screen i18n config flag overrides
                mDBFlagsSingleton.deleteFlagOverrides("CallScreenI18n__call_screen_i18n_config");
            }
        };
        mForceEnableCallScreenSwitch.setOnCheckedChangeListener(mForceEnableCallScreenSwitchOnCheckedChangeListener);


        return mView;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshSwitchesStatus();
    }

    public void refreshSwitchesStatus() {
        // mForceEnableCallRecordingSwitch
        mForceEnableCallRecordingSwitch.setOnCheckedChangeListener(null);
        mForceEnableCallRecordingSwitch.setChecked(
                mDBFlagsSingleton.areAllBooleanFlagsTrue(ENABLE_CALL_RECORDING_FLAGS)
        );
        mForceEnableCallRecordingSwitch.setOnCheckedChangeListener(mForceEnableCallRecordingSwitchOnCheckedChangeListener);

        // mSilenceCallRecordingAlertsSwitch
        int startingVoiceSize = -1;
        Shell.Result result;
        try {
            result = Shell.cmd("stat -c%s /data/data/com.google.android.dialer/files/callrecordingprompt/starting_voice-en_US.wav").exec();
            if (!result.isSuccess()) // Fallback if stat is not a command
                result = Shell.cmd("ls -lS starting_voice-en_US.wav | awk '{print $5}'").exec();
            startingVoiceSize = Integer.parseInt(result.getOut().get(0));
        } catch (Exception ignored) {}
        mSilenceCallRecordingAlertsSwitch.setOnCheckedChangeListener(null);
        mSilenceCallRecordingAlertsSwitch.setChecked(
                mDBFlagsSingleton.areAllStringFlagsEmpty(SILENCE_CALL_RECORDING_ALERTS_FLAGS) &&
                startingVoiceSize > 0 && startingVoiceSize <= 100
        );
        mSilenceCallRecordingAlertsSwitch.setOnCheckedChangeListener(mSilenceCallRecordingAlertsSwitchOnCheckedChangeListener);

        // mForceEnableCallScreenSwitch
        mForceEnableCallScreenSwitch.setOnCheckedChangeListener(null);
        mForceEnableCallScreenSwitch.setChecked(
                mDBFlagsSingleton.areAllBooleanFlagsTrue(ENABLE_CALL_SCREEN_FLAGS) && mDBFlagsSingleton.areAllFlagsOverridden(CALL_SCREEN_I18N_CONFIG_FLAG)
        );
        mForceEnableCallScreenSwitch.setOnCheckedChangeListener(mForceEnableCallScreenSwitchOnCheckedChangeListener);
    }
}