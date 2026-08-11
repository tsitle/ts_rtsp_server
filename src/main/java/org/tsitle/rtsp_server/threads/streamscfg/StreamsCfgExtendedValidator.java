package org.tsitle.rtsp_server.threads.streamscfg;

import org.jspecify.annotations.NonNull;
import org.tsitle.lib_xrtxp.common.logmsgs.LogMsgInterface;
import org.tsitle.lib_xrtxp.common.logmsgs.RtxpLogLevel;
import org.tsitle.lib_xrtxp.rtsp.misctypes.RtspProtoEsSourceType;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamInputEsMq;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamInputEsRawFile;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamsSs;
import org.tsitle.rtsp_server.config.RtspSrvConfigStreamsStream;

import java.util.Map;

final class StreamsCfgExtendedValidator {

	private final @NonNull LogMsgInterface logMsgInterface;
	private final @NonNull String caller;
	private final @NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsSs> allSubStreams;
	private final @NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsStream> allStreams;

	private StreamsCfgExtendedValidator(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull String caller,
				@NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsSs> allSubStreams,
				@NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsStream> allStreams
			) {
		this.logMsgInterface = logMsgInterface;
		this.caller = caller;
		this.allSubStreams = allSubStreams;
		this.allStreams = allStreams;
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	static boolean doExtendedValidation(
				@NonNull LogMsgInterface logMsgInterface,
				@NonNull String caller,
				@NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsSs> allSubStreams,
				@NonNull Map<@NonNull String, @NonNull RtspSrvConfigStreamsStream> allStreams
			) {
		StreamsCfgExtendedValidator escv = new StreamsCfgExtendedValidator(logMsgInterface, caller, allSubStreams, allStreams);

		return escv.checkAllStreams();
	}

	// -----------------------------------------------------------------------------------------------------------------
	// -----------------------------------------------------------------------------------------------------------------

	private boolean checkAllStreams() {
		boolean resB = true;
		for (Map.Entry<String, RtspSrvConfigStreamsStream> streamEntry : allStreams.entrySet()) {
			if (! streamEntry.getValue().getEnabled()) {
				continue;
			}
			boolean tmpR = checkOneStreamEnabledAndSrcType(streamEntry.getKey(), streamEntry.getValue());
			if (tmpR) {
				tmpR = checkOneStreamSsType(streamEntry.getKey(), streamEntry.getValue());
			}
			resB &= tmpR;
		}
		return resB;
	}

	private boolean checkOneStreamEnabledAndSrcType(
				@NonNull String streamId,
				@NonNull RtspSrvConfigStreamsStream streamCfg
			) {
		boolean resB = true;
		RtspProtoEsSourceType tmpEsSourceType = null;

		for (String subStreamId : streamCfg.getSubStreamIds()) {
			if (! allSubStreams.containsKey(subStreamId)) {
				logWarn(caller,
						"Stream ID '" + streamId + "' uses non-existing Sub-Stream ID '" + subStreamId + "'");
				resB = false;
			} else if (! allSubStreams.get(subStreamId).getEnabled()) {
				logWarn(caller,
						"Stream ID '" + streamId + "' uses disabled Sub-Stream ID '" + subStreamId + "'");
				resB = false;
			} else if (tmpEsSourceType == null) {
				tmpEsSourceType = allSubStreams.get(subStreamId).getEsSourceType();
			} else if (tmpEsSourceType != allSubStreams.get(subStreamId).getEsSourceType()) {
				logWarn(caller,
						"Stream ID '" + streamId + "' uses Sub-Streams of different types");
				resB = false;
			}
		}
		if (! resB) {
			return false;
		}
		final int tmpSsCount = streamCfg.getSubStreamIds().size();
		if (tmpSsCount == 0 || tmpEsSourceType == null) {
			logWarn(caller,
					"Stream ID '" + streamId + "' has no Sub-Streams");
			resB = false;
		} else if (tmpSsCount > 1 &&
				tmpEsSourceType != RtspProtoEsSourceType.ST_ES_RAW_FILE && tmpEsSourceType != RtspProtoEsSourceType.ST_ES_MQ) {
			logWarn(caller,
					"Stream ID '" + streamId + "' can not use more than one Sub-Stream (has " + tmpSsCount + ")");
			resB = false;
		} else if (tmpSsCount > 2) {
			logWarn(caller,
					"Stream ID '" + streamId + "' can not use more than two Sub-Stream (has " + tmpSsCount + ")");
			resB = false;
		}
		return resB;
	}

	private boolean checkOneStreamSsType(
				@NonNull String streamId,
				@NonNull RtspSrvConfigStreamsStream streamCfg
			) {
		boolean resB = true;

		boolean haveEsRawFileAudio = false;
		boolean haveEsRawFileVideo = false;
		String mqCompare = null;
		for (String subStreamId : streamCfg.getSubStreamIds()) {
			if (! allSubStreams.containsKey(subStreamId)) {
				continue;
			}
			RtspSrvConfigStreamsSs tmpSs = allSubStreams.get(subStreamId);
			if (! tmpSs.getEnabled()) {
				continue;
			}
			if (tmpSs.getEsSourceType() == RtspProtoEsSourceType.ST_DEMUX_MS_FILE ||
					tmpSs.getEsSourceType() == RtspProtoEsSourceType.ST_DEMUX_MS_RTSP) {
				continue;
			}
			if (tmpSs.getSsSourceEsRawFile().isPresent()) {
				RtspSrvConfigStreamInputEsRawFile rfObj = tmpSs.getSsSourceEsRawFile().get();
				if (rfObj.getCodec().isEmpty()) {
					logWarn(caller, "Sub-Stream ID '" + subStreamId + "' has no valid codec");
					resB = false;
				} else {
					if (rfObj.getCodec().get().isVideo()) {
						if (haveEsRawFileVideo) {
							logWarn(caller, "Stream ID '" + streamId + "' uses two video Sub-Streams");
							resB = false;
						}
						haveEsRawFileVideo = true;
					} else if (rfObj.getCodec().get().isAudio()) {
						if (haveEsRawFileAudio) {
							logWarn(caller, "Stream ID '" + streamId + "' uses two audio Sub-Streams");
							resB = false;
						}
						haveEsRawFileAudio = true;
					}
				}
			} else if (tmpSs.getSsSourceEsMq().isPresent()) {
				RtspSrvConfigStreamInputEsMq mqObj = tmpSs.getSsSourceEsMq().get();
				String tmpMqComp = "h=" + mqObj.getHost() + "|p=" + mqObj.getPort() +
						"|g=" + mqObj.getRscGroup() + "|c=" + mqObj.getRscChannel();
				if (mqCompare == null) {
					mqCompare = tmpMqComp;
				} else if (mqCompare.equalsIgnoreCase(tmpMqComp)) {
					logWarn(caller,
							"Stream ID '" + streamId + "' uses the same MQ Sub-Stream twice (" + tmpMqComp + ")");
					resB = false;
				}
			}
		}
		return resB;
	}

	// -----------------------------------------------------------------------------------------------------------------

	private void logWarn(@NonNull String fncName, @NonNull String msg) {
		internalLog(RtxpLogLevel.WARN, fncName, msg);
	}

	@SuppressWarnings("SameParameterValue")
	private void internalLog(@NonNull RtxpLogLevel logLevel, @NonNull String fncName, @NonNull String msg) {
		logMsgInterface.addMsgForLogThread(logLevel, Thread.currentThread().getName(), fncName + ": " + msg);
	}

}
