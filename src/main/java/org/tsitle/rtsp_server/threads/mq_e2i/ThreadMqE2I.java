package org.tsitle.rtsp_server.threads.mq_e2i;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_rtsp_mq.client.types.MqElementaryStreamSourceSettings;
import org.tsitle.lib_rtsp_mq.client.MqExternalSub;
import org.tsitle.lib_rtsp_mq.common.MqInternalPub;
import org.tsitle.lib_rtsp_mq.common.mqdata.MqCodecSettings;
import org.tsitle.lib_rtsp_mq.common.mqdata.MqPacketAv;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_rtsp_mq.exceptions.MqException;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.rtsp_server.threads.RunnableBase;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;

import java.util.Optional;

public final class ThreadMqE2I extends RunnableBase {

	private final @NonNull CodecSettingsChangedFromMqInterface codecSettingsChangedFromMqInterface;
	private final @NonNull RtspProtoIdEsSource idEsSource = RtspProtoIdEsSource.ofEmpty();
	private final @NonNull MqElementaryStreamSourceSettings mqSettings;
	private final @NonNull String mqSslCertPath;

	private final String threadName;

	private @Nullable MqExternalSub mqExternalSub = null;
	private final @NonNull MqInternalPub mqInternalPub;

	private final @NonNull BufferExt cachePayloadData = new BufferExt();
	private final @NonNull MqCodecSettings cacheCodecSettings = new MqCodecSettings();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param idEsSource Elementary-Stream Source identifier
	 * @param mqSettings Message Queue settings
	 * @param mqSslCertPath Path to the SSL certificate file (can be empty)
	 */
	public ThreadMqE2I(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull CodecSettingsChangedFromMqInterface codecSettingsChangedFromMqInterface,
				@NonNull RtspProtoIdEsSource idEsSource,
				@NonNull MqElementaryStreamSourceSettings mqSettings,
				@NonNull String mqSslCertPath
			) {
		super(logMsgInterface, cancelToken);

		//
		this.codecSettingsChangedFromMqInterface = codecSettingsChangedFromMqInterface;
		this.idEsSource.copyFrom(idEsSource);
		//
		this.mqSettings = mqSettings.clone();
		this.mqSslCertPath = mqSslCertPath;

		this.threadName = String.format("MQE2I#es%s#%s:%s:%s",
				idEsSource.getIdStr().orElse("-unset-"), mqSettings.getHostname(),
				mqSettings.getRscGroup(), mqSettings.getRscChannel());

		//
		mqInternalPub = new MqInternalPub(logMsgInterface, idEsSource);
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		Thread.currentThread().setName(threadName);

		//
		isRunning.set(true);
		logDebug(FNC_NAME, "Thread started");

		//
		try {
			mqInternalPub.connectToMq();

			int failCount = 0;
			while (! hasBeenRequestedToStop()) {
				mqExternalSub = new MqExternalSub(
						logMsgInterface,
						mqSettings,
						mqSslCertPath.strip()
					);
				try {
					mqExternalSub.connectToMq();
					logInfo(FNC_NAME, "Connected to MQ");
				} catch (MqException e) {
					failCount += 2;
					if (failCount > 120) {
						failCount = 120;
					}
					logDebug(FNC_NAME, "MqException caught: " + e.getMessage());
					logError(FNC_NAME, "Connection to MQ failed");
				}
				try {
					while (! hasBeenRequestedToStop() && mqExternalSub.isOpen()) {
						mainLoop();
						failCount = 0;
					}
				} catch (MqException e) {
					failCount += 2;
					if (failCount > 120) {
						failCount = 120;
					}
					logDebug(FNC_NAME, "MqException caught: " + e.getMessage());
					logError(FNC_NAME, "Connection to MQ lost/disconnected");
				} finally {
					mqExternalSub.close();
				}
				//
				if (failCount > 0) {
					sleepLongAndProsper(failCount);
				}
			}
		} catch (MqException e) {  // from mqInternalPub.connectToMq()
			logError(FNC_NAME, "MqException caught: " + e.getMessage());
		} catch (InterruptedException e) {
			logError(FNC_NAME, "InterruptedException");
			Thread.currentThread().interrupt();  // restore flag
		} catch (Exception e) {
			logError(FNC_NAME, "Exception caught: " + e.getMessage());
		} finally {
			mqInternalPub.close();
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private void mainLoop() throws InterruptedException, MqException {
		if (mqExternalSub == null) {
			throw new IllegalStateException("mqExternalSub is null");
		}
		Optional<MqPacketAv> optPacket = mqExternalSub.receiveMessageAv(cachePayloadData);
		if (optPacket.isEmpty()) {
			return;
		}
		MqPacketAv packet = optPacket.get();
		//
		boolean haveChanges = false;
		if (cacheCodecSettings.codec == null || cacheCodecSettings.codec.ordinal() != packet.codec().ordinal()) {
			// the codec should never actually change during a session - but we need to read it once
			cacheCodecSettings.codec = packet.codec();
			if (! packet.codec().isVideo()) {
				// samplerate and channel count should never actually change during a session - but we need to read them once
				cacheCodecSettings.audioSamplerate = packet.mdAudioSamplerate();
				cacheCodecSettings.audioChannels = packet.mdAudioChannelCount();
			}
			haveChanges = true;
		}
		if (packet.codec().isVideo() &&
				(cacheCodecSettings.videoFps == null || cacheCodecSettings.videoFps != packet.mdVideoFps())) {
			// the framerate can change during a session
			cacheCodecSettings.videoFps = packet.mdVideoFps();
			haveChanges = true;
		}
		if (haveChanges) {
			codecSettingsChangedFromMqInterface.onCodecSettingsChangedFromMq(idEsSource, cacheCodecSettings);
		}
		//
		mqInternalPub.sendMessageAv(packet);
	}

	private void sleepLongAndProsper(int secs) {
		try {
			int ms = secs * 1000;
			while (ms > 0 && ! hasBeenRequestedToStop()) {
				//noinspection BusyWait
				Thread.sleep(100L);
				ms -= 100;
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();  // restore flag
		}
	}

}
