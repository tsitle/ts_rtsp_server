package org.tsitle.rtsp_server.threads.inp_to_internal_mq;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.tsitle.lib_mq.client.types.MqElementaryStreamSourceSettings;
import org.tsitle.lib_mq.client.MqExternalSub;
import org.tsitle.lib_mq.common.MqInternalPub;
import org.tsitle.lib_mq.common.mqdata.MqCodecSettings;
import org.tsitle.lib_mq.common.mqdata.MqPacketAv;
import org.tsitle.lib_mq.common.mqdata.MqPacketCodec;
import org.tsitle.lib_mq.exceptions.MqException;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.*;
import org.tsitle.lib_xrtxp.avdata.exceptions.AvInvalidCodecDataException;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.subinfo.H264PpsContext;
import org.tsitle.lib_xrtxp.avdata.codec_v_h26x.subinfo.H264SpsContext;
import org.tsitle.lib_xrtxp.common.buffers.BufferExt;
import org.tsitle.lib_xrtxp.common.buffers.BufferView;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.rtsp.ids.RtspProtoIdEsSource;
import org.tsitle.rtsp_server.availstreams.AsCodecSettingsChangedFromMqInterface;
import org.tsitle.rtsp_server.threads.CancelToken;
import org.tsitle.rtsp_server.threads.RunnableBase;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class ThreadInpMqE2I extends RunnableBase {

	private static class MetadataVars {
		boolean haveAllMetadataPackets = false;
		int metadataPktCount = 0;
		final @NonNull BufferExt metadataPktH26xSps = new BufferExt();
		final @NonNull BufferExt metadataPktH26xPps = new BufferExt();
		final @NonNull BufferExt metadataPktH265Vps = new BufferExt();
		@Nullable Map<@NonNull Integer, @NonNull H264SpsContext> mapH264SpsContext = null;
		@Nullable Map<@NonNull Integer, @NonNull H264PpsContext> mapH264PpsContext = null;
		@Nullable VideoH264Parser codecParserH264 = null;
		@Nullable VideoH265Parser codecParserH265 = null;
		@NonNull String metadataHex = "";

		void reset(@NonNull MqPacketCodec mqCodec) {
			switch (mqCodec) {
				case H264, H265 -> haveAllMetadataPackets = false;
				default -> haveAllMetadataPackets = true;
			}

			metadataPktCount = 0;

			metadataPktH26xSps.clear();
			metadataPktH26xPps.clear();
			metadataPktH265Vps.clear();

			mapH264SpsContext = null;
			mapH264PpsContext = null;
			codecParserH264 = null;

			codecParserH265 = null;

			metadataHex = "";
		}
	}

	private final @NonNull AsCodecSettingsChangedFromMqInterface codecSettingsChangedInterface;
	private final @NonNull RtspProtoIdEsSource idEsSource = RtspProtoIdEsSource.ofEmpty();
	private final @NonNull MqElementaryStreamSourceSettings mqSettings;
	private final @NonNull String mqSslCertPath;

	private final @NonNull String threadName;

	private @Nullable MqExternalSub mqExternalSub = null;
	private @Nullable MqInternalPub mqInternalPub = null;

	private final @NonNull BufferExt cachePayloadData = new BufferExt();
	private final @NonNull MqCodecSettings cacheCodecSettings = new MqCodecSettings();

	private final @NonNull MetadataVars metadataVars = new MetadataVars();

	private final @NonNull CancelToken localCancelToken = new CancelToken();

	/**
	 * Constructor.
	 * @param logMsgInterface Functional interface for logging messages
	 * @param cancelToken Cancel token
	 * @param codecSettingsChangedInterface 'Codec settings changed' interface
	 * @param idEsSource Elementary-Stream Source identifier
	 * @param mqSettings Message Queue settings
	 * @param mqSslCertPath Path to the SSL certificate file (can be empty)
	 */
	public ThreadInpMqE2I(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull CancelToken cancelToken,
				@NonNull AsCodecSettingsChangedFromMqInterface codecSettingsChangedInterface,
				@NonNull RtspProtoIdEsSource idEsSource,
				@NonNull MqElementaryStreamSourceSettings mqSettings,
				@NonNull String mqSslCertPath
			) {
		super(logMsgInterface, cancelToken);

		//
		this.codecSettingsChangedInterface = codecSettingsChangedInterface;
		this.idEsSource.copyFrom(idEsSource);
		//
		this.mqSettings = mqSettings.clone();
		this.mqSslCertPath = mqSslCertPath;

		//
		this.threadName = String.format("MQE2I#es%s#%s:%s:%s",
				idEsSource.getIdStr().orElse("-unset-"), mqSettings.getHostname(),
				mqSettings.getRscGroup(), mqSettings.getRscChannel());
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	@Override
	public void run() {
		final String FNC_NAME = getClass().getSimpleName() + ".run()";

		Thread.currentThread().setName(threadName);

		//
		mqInternalPub = new MqInternalPub(logMsgInterface, idEsSource);

		//
		isRunning.set(true);
		logDebug(FNC_NAME, "Thread started");

		//
		try {
			mqInternalPub.connectToMq();

			int failCount = 0;
			while (! (hasBeenRequestedToStop() || localCancelToken.cancelled)) {
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
					while (! (hasBeenRequestedToStop() || localCancelToken.cancelled) && mqExternalSub.isOpen()) {
						mainLoop();
						failCount = 0;
					}
				} catch (MqException e) {
					failCount += 2;
					if (failCount > 120) {
						failCount = 120;
					}
					logDebug(FNC_NAME, "MqException caught: " + e.getMessage());
					logWarn(FNC_NAME, "Connection to MQ lost/disconnected");
				} finally {
					mqExternalSub.close();
				}
				//
				if (failCount > 0) {
					sleepLongAndProsper(localCancelToken, failCount);
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
			mqInternalPub = null;
			//
			isRunning.set(false);
			logDebug(FNC_NAME, "Thread ended");
		}
	}

	public void stopThread() {
		localCancelToken.cancelled = true;
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
		checkForCodecChanges(packet);
		if (! metadataVars.haveAllMetadataPackets && ++metadataVars.metadataPktCount <= 100) {
			switch (packet.codec()) {
				case H264 -> checkForCodecMetadata_h264(packet);
				case H265 -> checkForCodecMetadata_h265(packet);
			}
			if (metadataVars.haveAllMetadataPackets) {
				codecSettingsChangedInterface.onCodecMetadataFromMq(idEsSource, metadataVars.metadataHex);
			}
		}

		//
		if (mqInternalPub == null) {
			throw new IllegalStateException("mqInternalPub is null");
		}
		mqInternalPub.sendMessageAv(packet);
	}

	private void checkForCodecChanges(@NonNull MqPacketAv packet) {
		final String FNC_NAME = getClass().getSimpleName() + ".checkForCodecChanges()";

		boolean haveChanges = false;
		if (cacheCodecSettings.codec == null || cacheCodecSettings.codec.ordinal() != packet.codec().ordinal()) {
			// the codec should never actually change during a session - but we need to read it once
			cacheCodecSettings.codec = packet.codec();
			if (packet.codec().isAudio()) {
				/*
				 * The Samplerate and Channel Count should never actually change during a session,
				 * but we need to read them at least once
				 */
				cacheCodecSettings.audioSamplerate = packet.mdAudioSamplerate();
				cacheCodecSettings.audioChannels = packet.mdAudioChannelCount();
			}
			//
			haveChanges = true;
		}
		if (packet.codec().isVideo() &&
				(cacheCodecSettings.videoFps == null || cacheCodecSettings.videoFps != packet.mdVideoFps())) {
			// the Framerate can change during a session
			cacheCodecSettings.videoFps = packet.mdVideoFps();
			haveChanges = true;
		} else if (packet.codec().isAudio() &&
				(cacheCodecSettings.audioSamplesPerFrame == null ||
						cacheCodecSettings.audioSamplesPerFrame != packet.mdAudioSamplesPerFrame())) {
			cacheCodecSettings.audioSamplesPerFrame = packet.mdAudioSamplesPerFrame();
			haveChanges = true;
		}
		if (haveChanges) {
			metadataVars.reset(cacheCodecSettings.codec);
			//
			logDebug(FNC_NAME, "have new MQ codec settings: " + cacheCodecSettings);
			codecSettingsChangedInterface.onCodecSettingsChangedFromMq(idEsSource, cacheCodecSettings);
		}
	}

	private void checkForCodecMetadata_h264(@NonNull MqPacketAv packet) {
		final String FNC_NAME = getClass().getSimpleName() + ".checkForCodecMetadata_h264()";

		if (metadataVars.mapH264SpsContext == null) {
			metadataVars.mapH264SpsContext = new HashMap<>();
		}
		if (metadataVars.mapH264PpsContext == null) {
			metadataVars.mapH264PpsContext = new HashMap<>();
		}
		if (metadataVars.codecParserH264 == null) {
			metadataVars.codecParserH264 = new VideoH264Parser(metadataVars.mapH264SpsContext, metadataVars.mapH264PpsContext);
		}

		BufferView payloadBv = new BufferView(packet.payloadDataPtr());
		try {
			while (true) {
				int tmpMagicBytesLen = MagicBytesH26xHelper.findH26xMagicBytesLength(payloadBv);
				int tmpNextOffs = MagicBytesH26xHelper.findH26xNextNalUnit(payloadBv);
				BufferView tmpNuBv = new BufferView(packet.payloadDataPtr());
				tmpNuBv.setOffset(payloadBv.getOffset());
				tmpNuBv.setLength(tmpNextOffs > 0 ? tmpNextOffs : payloadBv.getLength());

				VideoH264Info codecInfo = metadataVars.codecParserH264.parseH264Data(
						0L,
						tmpMagicBytesLen,
						tmpNuBv,
						null
					);

				if (codecInfo.nalUnitTypeEn == VideoH264Info.NalUnitType.NVCL_SPS) {
					tmpNuBv.copyViewIntoBe(metadataVars.metadataPktH26xSps);
				} else if (codecInfo.nalUnitTypeEn == VideoH264Info.NalUnitType.NVCL_PPS) {
					tmpNuBv.copyViewIntoBe(metadataVars.metadataPktH26xPps);
				}

				if (tmpNextOffs < 1) {
					break;
				}
				payloadBv.increaseOffset(tmpNextOffs);
				payloadBv.increaseLength(-1 * tmpNextOffs);
			}

			metadataVars.haveAllMetadataPackets = (! (metadataVars.metadataPktH26xSps.isEmpty() ||
					metadataVars.metadataPktH26xPps.isEmpty()));
			if (metadataVars.haveAllMetadataPackets) {
				logDebug(FNC_NAME, "have all metadata packets");
				metadataVars.metadataHex = metadataVars.metadataPktH26xSps.toHexString() +
						metadataVars.metadataPktH26xPps.toHexString();
			}
		} catch (AvInvalidCodecDataException e) {
			logWarn(FNC_NAME, "AvInvalidCodecDataException caught: " + e.getMessage());
		}
	}

	private void checkForCodecMetadata_h265(@NonNull MqPacketAv packet) {
		final String FNC_NAME = getClass().getSimpleName() + ".checkForCodecMetadata_h265()";

		if (metadataVars.codecParserH265 == null) {
			metadataVars.codecParserH265 = new VideoH265Parser();
		}

		BufferView payloadBv = new BufferView(packet.payloadDataPtr());
		try {
			while (true) {
				int tmpMagicBytesLen = MagicBytesH26xHelper.findH26xMagicBytesLength(payloadBv);
				int tmpNextOffs = MagicBytesH26xHelper.findH26xNextNalUnit(payloadBv);
				BufferView tmpNuBv = new BufferView(packet.payloadDataPtr());
				tmpNuBv.setOffset(payloadBv.getOffset());
				tmpNuBv.setLength(tmpNextOffs > 0 ? tmpNextOffs : payloadBv.getLength());

				VideoH265Info codecInfo = metadataVars.codecParserH265.parseH265Data(
						0L,
						tmpMagicBytesLen,
						tmpNuBv
					);

				if (codecInfo.nalUnitTypeEn == VideoH265Info.NalUnitType.NVCL_SPS) {
					tmpNuBv.copyViewIntoBe(metadataVars.metadataPktH26xSps);
				} else if (codecInfo.nalUnitTypeEn == VideoH265Info.NalUnitType.NVCL_PPS) {
					tmpNuBv.copyViewIntoBe(metadataVars.metadataPktH26xPps);
				} else if (codecInfo.nalUnitTypeEn == VideoH265Info.NalUnitType.NVCL_VPS) {
					tmpNuBv.copyViewIntoBe(metadataVars.metadataPktH265Vps);
				}

				if (tmpNextOffs < 1) {
					break;
				}
				payloadBv.increaseOffset(tmpNextOffs);
				payloadBv.increaseLength(-1 * tmpNextOffs);
			}

			metadataVars.haveAllMetadataPackets = (! (metadataVars.metadataPktH26xSps.isEmpty() ||
					metadataVars.metadataPktH26xPps.isEmpty() ||
					metadataVars.metadataPktH265Vps.isEmpty()));
			if (metadataVars.haveAllMetadataPackets) {
				logDebug(FNC_NAME, "have all metadata packets");
				metadataVars.metadataHex = metadataVars.metadataPktH26xSps.toHexString() +
						metadataVars.metadataPktH26xPps.toHexString() +
						metadataVars.metadataPktH265Vps.toHexString();
			}
		} catch (AvInvalidCodecDataException e) {
			logWarn(FNC_NAME, "AvInvalidCodecDataException caught: " + e.getMessage());
		}
	}

}
